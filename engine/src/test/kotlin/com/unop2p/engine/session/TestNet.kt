package com.unop2p.engine.session

import com.unop2p.engine.game.GameEvent
import com.unop2p.engine.game.PublicGameState
import com.unop2p.engine.protocol.ActionResult
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.LobbyUpdate
import com.unop2p.engine.protocol.ProtocolJson

/**
 * In-memory transport that routes messages between a HostSession and several
 * ClientSessions, encoding/decoding through the real JSON codec so serialization
 * bugs surface in tests. This is the same seam the WebRTC data channel fills in
 * the Android app.
 */
class TestNet {
    lateinit var host: HostSession
    val clients = LinkedHashMap<String, ClientSession>()
    val listeners = LinkedHashMap<String, RecordingListener>()

    val hostChannel = object : HostDataChannel {
        override fun send(playerId: String, message: GameMessage) {
            val decoded = ProtocolJson.decode(ProtocolJson.encode(message))
            clients[playerId]?.onMessage(decoded)
        }
        override fun broadcast(message: GameMessage) {
            clients.keys.toList().forEach { send(it, message) }
        }
        override fun broadcastExcept(exceptPlayerId: String, message: GameMessage) {
            clients.keys.toList().filter { it != exceptPlayerId }.forEach { send(it, message) }
        }
    }

    fun clientChannel(playerId: String) = object : ClientDataChannel {
        override fun sendToHost(message: GameMessage) {
            val decoded = ProtocolJson.decode(ProtocolJson.encode(message))
            host.onClientMessage(playerId, decoded)
        }
    }

    fun addClient(playerId: String, token: String): RecordingListener {
        val listener = RecordingListener(playerId)
        val session = ClientSession(playerId, token, clientChannel(playerId), listener)
        clients[playerId] = session
        listeners[playerId] = listener
        return listener
    }
}

class RecordingListener(val playerId: String) : ClientSessionListener {
    var latestState: PublicGameState? = null
    var lastLobby: LobbyUpdate? = null
    val events = mutableListOf<GameEvent>()
    val actionResults = mutableListOf<ActionResult>()
    var welcomed = false
    var endedReason: String? = null
    var lastError: String? = null

    override fun onState(state: PublicGameState) { latestState = state }
    override fun onEvents(stateVersion: Int, events: List<GameEvent>) { this.events += events }
    override fun onLobby(update: LobbyUpdate) { lastLobby = update }
    override fun onActionResult(result: ActionResult) { actionResults += result }
    override fun onWelcome(playerId: String, displayName: String) { welcomed = true }
    override fun onSessionEnded(reason: String) { endedReason = reason }
    override fun onError(message: String) { lastError = message }
}
