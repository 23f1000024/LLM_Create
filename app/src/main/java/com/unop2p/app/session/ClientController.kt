package com.unop2p.app.session

import android.content.Context
import com.unop2p.app.common.AppLog
import com.unop2p.app.net.PeerLinkState
import com.unop2p.app.net.SignalingClient
import com.unop2p.app.net.WebRtcManager
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.PublicGameState
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.LobbyUpdate
import com.unop2p.engine.protocol.Offer
import com.unop2p.engine.protocol.Answer
import com.unop2p.engine.protocol.IceCandidateMsg
import com.unop2p.engine.protocol.PeerJoined
import com.unop2p.engine.protocol.PeerLeft
import com.unop2p.engine.protocol.PeerRoster
import com.unop2p.engine.protocol.ActionResult
import com.unop2p.engine.session.ClientDataChannel
import com.unop2p.engine.session.ClientSession
import com.unop2p.engine.session.ClientSessionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus { IDLE, JOINING, NEGOTIATING, CONNECTED, FAILED, ENDED }

/**
 * Client-side orchestration: joins via signaling, forms the WebRTC mesh, and
 * runs a [ClientSession] that talks only to the host over the data channel.
 */
class ClientController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val displayName: String,
) {
    private val signaling = SignalingClient(scope)
    private var webrtc: WebRtcManager? = null
    private var session: ClientSession? = null

    private var myPlayerId: String = ""
    private var hostPlayerId: String = ""
    private var helloSent = false

    private val _status = MutableStateFlow(ConnectionStatus.IDLE)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _gameState = MutableStateFlow<PublicGameState?>(null)
    val gameState: StateFlow<PublicGameState?> = _gameState.asStateFlow()

    private val _lobby = MutableStateFlow<LobbyUpdate?>(null)
    val lobby: StateFlow<LobbyUpdate?> = _lobby.asStateFlow()

    private val _peerStates = MutableStateFlow<Map<String, PeerLinkState>>(emptyMap())
    val peerStates: StateFlow<Map<String, PeerLinkState>> = _peerStates.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val localPlayerId: String get() = myPlayerId

    private val listener = object : ClientSessionListener {
        override fun onState(state: PublicGameState) { _gameState.value = state }
        override fun onLobby(update: LobbyUpdate) { _lobby.value = update }
        override fun onActionResult(result: ActionResult) {
            if (!result.accepted) _error.value = result.error
        }
        override fun onWelcome(playerId: String, displayName: String) {
            _status.value = ConnectionStatus.CONNECTED
        }
        override fun onSessionEnded(reason: String) {
            _status.value = ConnectionStatus.ENDED
            _error.value = reason
        }
        override fun onError(message: String) { _error.value = message }
    }

    /** Joins the room at [host]:[port] and begins WebRTC negotiation. */
    suspend fun connect(host: String, port: Int, roomCode: String) {
        _status.value = ConnectionStatus.JOINING
        val baseUrl = "http://$host:$port"
        val wsUrl = "ws://$host:$port"
        val accepted = signaling.join(baseUrl, roomCode, displayName)
        myPlayerId = accepted.playerId
        hostPlayerId = accepted.hostPlayerId

        webrtc = WebRtcManager(
            context = context,
            myPlayerId = myPlayerId,
            iceServers = accepted.iceServers,
            signalSender = { msg -> signaling.send(msg) },
            onGameMessage = { from, msg -> if (from == hostPlayerId) session?.onMessage(msg) },
            onPeerState = { st -> onPeerState(st) },
        )
        session = ClientSession(
            myPlayerId = myPlayerId,
            token = accepted.token,
            channel = object : ClientDataChannel {
                override fun sendToHost(message: GameMessage) { webrtc?.sendTo(hostPlayerId, message) }
            },
            listener = listener,
        )
        _status.value = ConnectionStatus.NEGOTIATING

        signaling.connect(
            wsUrl = wsUrl,
            playerId = myPlayerId,
            token = accepted.token,
            onMessage = { msg -> handleSignal(msg) },
            onClosed = { if (_status.value != ConnectionStatus.ENDED) _status.value = ConnectionStatus.FAILED },
        )
        AppLog.i(AppLog.Area.ROOM, "Joined room $roomCode as $myPlayerId")
    }

    private fun handleSignal(message: com.unop2p.engine.protocol.SignalMessage) {
        when (message) {
            is PeerRoster -> message.peers.filter { it.playerId != myPlayerId }
                .forEach { webrtc?.connectPeer(it.playerId) }
            is PeerJoined -> if (message.peer.playerId != myPlayerId) webrtc?.connectPeer(message.peer.playerId)
            is PeerLeft -> webrtc?.removePeer(message.playerId)
            is Offer, is Answer, is IceCandidateMsg -> webrtc?.onSignal(message)
            else -> {}
        }
    }

    private fun onPeerState(st: PeerLinkState) {
        _peerStates.value = _peerStates.value + (st.playerId to st)
        // Once the data channel to the host is open, claim our seat with Hello.
        if (st.playerId == hostPlayerId && st.dataOpen && !helloSent) {
            helloSent = true
            session?.connect()
        }
    }

    // ---- Player actions ------------------------------------------------------

    fun play(cardUid: String, chosenColor: CardColor? = null, declareUno: Boolean = false) =
        session?.playCard(cardUid, chosenColor, declareUno)
    fun draw() = session?.drawCard()
    fun chooseColor(color: CardColor) = session?.chooseColor(color)
    fun callUno() = session?.callUno()
    fun challengeUno(target: String) = session?.challengeUno(target)
    fun challengeDrawFour() = session?.challengeDrawFour()
    fun setReady(ready: Boolean) = session?.setReady(ready)
    fun requestState() = session?.requestState()

    fun setMicEnabled(enabled: Boolean) = webrtc?.setMicEnabled(enabled)

    suspend fun disconnect() {
        signaling.close()
        webrtc?.close()
        webrtc = null
        _status.value = ConnectionStatus.ENDED
        AppLog.i(AppLog.Area.ROOM, "Left room")
    }

    fun clearError() { _error.value = null }
}
