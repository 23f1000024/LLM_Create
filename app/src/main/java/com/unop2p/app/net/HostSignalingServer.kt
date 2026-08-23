package com.unop2p.app.net

import com.unop2p.app.common.AppLog
import com.unop2p.engine.protocol.Answer
import com.unop2p.engine.protocol.Bye
import com.unop2p.engine.protocol.IceCandidateMsg
import com.unop2p.engine.protocol.IceServer
import com.unop2p.engine.protocol.JoinAccepted
import com.unop2p.engine.protocol.JoinRejected
import com.unop2p.engine.protocol.JoinRequest
import com.unop2p.engine.protocol.Offer
import com.unop2p.engine.protocol.PeerInfo
import com.unop2p.engine.protocol.PeerJoined
import com.unop2p.engine.protocol.PeerLeft
import com.unop2p.engine.protocol.PeerRoster
import com.unop2p.engine.protocol.ProtocolJson
import com.unop2p.engine.protocol.SignalHello
import com.unop2p.engine.protocol.SignalMessage
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of admitting (or rejecting) a join request. Decided by the host session,
 * not by the server — the server only carries the bytes.
 */
sealed interface AdmitDecision {
    data class Accepted(val playerId: String, val token: String) : AdmitDecision
    data class Rejected(val reason: String) : AdmitDecision
}

/** Everything the signaling server needs from the host game session. */
interface SignalingHost {
    val roomCode: String
    val hostPlayerId: String
    val hostDisplayName: String
    val maxPlayers: Int
    val iceServers: List<IceServer>
    fun admit(displayName: String): AdmitDecision
    fun validate(playerId: String, token: String): Boolean
    fun peerInfos(): List<PeerInfo>
    /** A signaling message addressed to the host's own (local) WebRTC peer. */
    fun onSignalToHost(message: SignalMessage)
    fun onSignalingConnected(playerId: String)
    fun onSignalingDisconnected(playerId: String)
}

/**
 * The host's temporary, embedded signaling service. Exposes only the endpoints
 * needed to join a room and exchange WebRTC SDP/ICE:
 *   GET  /room/info    — room metadata
 *   POST /room/join    — obtain a seat (playerId + token)
 *   WS   /signal       — authenticate, then relay offers/answers/candidates
 *
 * It contains NO game logic and moves NO game or voice traffic — those go
 * peer-to-peer over WebRTC once connected.
 */
class HostSignalingServer(private val host: SignalingHost) {

    private class Connection(val playerId: String, val outbox: Channel<SignalMessage>)

    private val connections = ConcurrentHashMap<String, Connection>()
    private var server: EmbeddedServer<*, *>? = null
    @Volatile var port: Int = 0; private set

    fun start(port: Int) {
        this.port = port
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                get("/room/info") {
                    val info = """
                        {"roomCode":"${host.roomCode}","hostName":"${escape(host.hostDisplayName)}",
                         "maxPlayers":${host.maxPlayers},"players":${connections.size + 1}}
                    """.trimIndent()
                    call.respondText(info, ContentType.Application.Json)
                }

                post("/room/join") {
                    val body = runCatching { call.receiveText() }.getOrNull()
                    val req = body?.let { runCatching { ProtocolJson.decodeSignal(it) }.getOrNull() } as? JoinRequest
                    if (req == null) {
                        call.respondText(
                            ProtocolJson.encodeSignal(JoinRejected("Malformed join request")),
                            ContentType.Application.Json, HttpStatusCode.BadRequest,
                        )
                        return@post
                    }
                    if (req.roomCode != host.roomCode) {
                        call.respondText(
                            ProtocolJson.encodeSignal(JoinRejected("Wrong room code")),
                            ContentType.Application.Json, HttpStatusCode.Forbidden,
                        )
                        return@post
                    }
                    when (val d = host.admit(req.displayName)) {
                        is AdmitDecision.Accepted -> {
                            AppLog.i(AppLog.Area.SIGNAL, "Admitted ${d.playerId} (${req.displayName})")
                            call.respondText(
                                ProtocolJson.encodeSignal(
                                    JoinAccepted(d.playerId, d.token, host.hostPlayerId, host.iceServers),
                                ),
                                ContentType.Application.Json,
                            )
                        }
                        is AdmitDecision.Rejected -> call.respondText(
                            ProtocolJson.encodeSignal(JoinRejected(d.reason)),
                            ContentType.Application.Json, HttpStatusCode.Forbidden,
                        )
                    }
                }

                webSocket("/signal") {
                    // First frame must authenticate the seat.
                    val first = (incoming.receive() as? Frame.Text)?.readText()
                    val hello = first?.let { runCatching { ProtocolJson.decodeSignal(it) }.getOrNull() } as? SignalHello
                    if (hello == null || !host.validate(hello.playerId, hello.token)) {
                        send(Frame.Text(ProtocolJson.encodeSignal(JoinRejected("Unauthorized"))))
                        close()
                        return@webSocket
                    }
                    val pid = hello.playerId
                    val conn = Connection(pid, Channel(Channel.BUFFERED))
                    connections[pid] = conn
                    host.onSignalingConnected(pid)
                    AppLog.i(AppLog.Area.SIGNAL, "WS connected: $pid")

                    // Tell the newcomer who to connect to, and tell existing peers about it.
                    send(Frame.Text(ProtocolJson.encodeSignal(PeerRoster(host.peerInfos()))))
                    broadcastExcept(pid, PeerJoined(host.peerInfos().first { it.playerId == pid }))

                    // Pump outbound messages from the channel to the socket.
                    val pump = launch {
                        for (msg in conn.outbox) {
                            if (!isActive) break
                            send(Frame.Text(ProtocolJson.encodeSignal(msg)))
                        }
                    }
                    try {
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            if (text.length > ProtocolJson.MAX_MESSAGE_BYTES) continue
                            val msg = runCatching { ProtocolJson.decodeSignal(text) }.getOrNull() ?: continue
                            handleFromPeer(pid, msg)
                        }
                    } finally {
                        pump.cancel()
                        conn.outbox.close()
                        connections.remove(pid)
                        host.onSignalingDisconnected(pid)
                        broadcastExcept(pid, PeerLeft(pid))
                        AppLog.i(AppLog.Area.SIGNAL, "WS disconnected: $pid")
                    }
                }
            }
        }.also { it.start(wait = false) }
        AppLog.i(AppLog.Area.SIGNAL, "Signaling server listening on :$port")
    }

    /** Validates the sender, then relays a peer's SDP/ICE toward its target. */
    private fun handleFromPeer(fromPlayerId: String, msg: SignalMessage) {
        val target = when (msg) {
            is Offer -> if (msg.from == fromPlayerId) msg.to else null
            is Answer -> if (msg.from == fromPlayerId) msg.to else null
            is IceCandidateMsg -> if (msg.from == fromPlayerId) msg.to else null
            is Bye -> { route(msg); return }
            else -> null
        } ?: return // ignore spoofed 'from' or non-relayable types
        routeTo(target, msg)
    }

    /** Sends a message toward its addressed peer (the host injects messages here too). */
    fun route(message: SignalMessage) {
        val to = when (message) {
            is Offer -> message.to
            is Answer -> message.to
            is IceCandidateMsg -> message.to
            is PeerLeft -> null
            else -> null
        }
        if (to != null) routeTo(to, message)
    }

    private fun routeTo(target: String, message: SignalMessage) {
        if (target == host.hostPlayerId) {
            host.onSignalToHost(message)
        } else {
            connections[target]?.outbox?.trySend(message)
        }
    }

    fun sendToPeer(playerId: String, message: SignalMessage) = routeTo(playerId, message)

    private fun broadcastExcept(exceptPlayerId: String, message: SignalMessage) {
        connections.values.filter { it.playerId != exceptPlayerId }
            .forEach { it.outbox.trySend(message) }
    }

    fun stop() {
        connections.values.forEach { it.outbox.close() }
        connections.clear()
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
        AppLog.i(AppLog.Area.SIGNAL, "Signaling server stopped")
    }

    private fun escape(s: String): String = s.replace("\"", "").replace("\n", " ")
}
