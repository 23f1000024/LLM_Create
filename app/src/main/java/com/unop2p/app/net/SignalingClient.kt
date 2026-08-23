package com.unop2p.app.net

import com.unop2p.app.common.AppLog
import com.unop2p.engine.protocol.JoinAccepted
import com.unop2p.engine.protocol.JoinRejected
import com.unop2p.engine.protocol.JoinRequest
import com.unop2p.engine.protocol.ProtocolJson
import com.unop2p.engine.protocol.SignalHello
import com.unop2p.engine.protocol.SignalMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The joining side of signaling. Performs the HTTP join handshake, then opens a
 * WebSocket to relay WebRTC SDP/ICE with peers via the host. Carries no game or
 * voice traffic.
 */
class SignalingClient(private val scope: CoroutineScope) {

    private val http = HttpClient(CIO) { install(WebSockets) }
    private var wsSession: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null
    private var pumpJob: Job? = null

    /** POST /room/join. Returns the accepted seat or throws with the rejection reason. */
    suspend fun join(baseUrl: String, roomCode: String, displayName: String): JoinAccepted {
        val resp = http.post("$baseUrl/room/join") {
            setBody(ProtocolJson.encodeSignal(JoinRequest(roomCode, displayName)))
        }
        val body = resp.bodyAsText()
        return when (val msg = ProtocolJson.decodeSignal(body)) {
            is JoinAccepted -> msg
            is JoinRejected -> throw JoinException(msg.reason)
            else -> throw JoinException("Unexpected join response")
        }
    }

    /**
     * Opens the signaling WebSocket, authenticates with [playerId]/[token], and
     * dispatches inbound [SignalMessage]s to [onMessage].
     */
    suspend fun connect(
        wsUrl: String,
        playerId: String,
        token: String,
        onMessage: (SignalMessage) -> Unit,
        onClosed: () -> Unit,
    ) {
        val session = http.webSocketSession(urlString = "$wsUrl/signal")
        wsSession = session
        session.send(Frame.Text(ProtocolJson.encodeSignal(SignalHello(playerId, token))))
        AppLog.i(AppLog.Area.SIGNAL, "Signaling WS connected as $playerId")
        pumpJob = scope.launch {
            try {
                for (frame in session.incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    if (text.length > ProtocolJson.MAX_MESSAGE_BYTES) continue
                    val msg = runCatching { ProtocolJson.decodeSignal(text) }.getOrNull() ?: continue
                    onMessage(msg)
                }
            } finally {
                onClosed()
            }
        }
    }

    /** Sends a signaling message (offer/answer/candidate) to the host for relay. */
    fun send(message: SignalMessage) {
        val session = wsSession ?: return
        scope.launch { session.send(Frame.Text(ProtocolJson.encodeSignal(message))) }
    }

    suspend fun close() {
        pumpJob?.cancel()
        runCatching { wsSession?.close() }
        wsSession = null
        http.close()
    }
}

class JoinException(message: String) : Exception(message)
