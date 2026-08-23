package com.unop2p.app.session

import android.content.Context
import com.unop2p.app.common.AppLog
import com.unop2p.app.net.AddressUtils
import com.unop2p.app.net.AdmitDecision
import com.unop2p.app.net.HostSignalingServer
import com.unop2p.app.net.LocalAddress
import com.unop2p.app.net.PeerLinkState
import com.unop2p.app.net.SignalingHost
import com.unop2p.app.net.WebRtcManager
import com.unop2p.engine.common.Ids
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GameSettings
import com.unop2p.engine.game.PlayerAction
import com.unop2p.engine.game.PublicGameState
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.IceServer
import com.unop2p.engine.protocol.LobbyPlayer
import com.unop2p.engine.protocol.PeerInfo
import com.unop2p.engine.protocol.SignalMessage
import com.unop2p.engine.session.HostDataChannel
import com.unop2p.engine.session.HostSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Host-side orchestration: owns the embedded signaling server, the WebRTC mesh,
 * and the authoritative [HostSession]. The Android UI observes the exposed
 * StateFlows and calls the host action methods; all rules live in the engine.
 */
class HostController(
    private val context: Context,
    hostName: String,
    override val maxPlayers: Int,
    settings: GameSettings,
    override val iceServers: List<IceServer> = emptyList(),
    private val port: Int = DEFAULT_PORT,
) : SignalingHost {

    override val hostPlayerId: String = Ids.playerId()
    override val roomCode: String = Ids.roomCode()
    override val hostDisplayName: String = hostName.trim().take(24).ifEmpty { "Host" }

    private val webrtc: WebRtcManager
    private val session: HostSession
    private lateinit var server: HostSignalingServer

    private val _lobby = MutableStateFlow<List<LobbyPlayer>>(emptyList())
    val lobby: StateFlow<List<LobbyPlayer>> = _lobby.asStateFlow()

    private val _gameState = MutableStateFlow<PublicGameState?>(null)
    val gameState: StateFlow<PublicGameState?> = _gameState.asStateFlow()

    private val _peerStates = MutableStateFlow<Map<String, PeerLinkState>>(emptyMap())
    val peerStates: StateFlow<Map<String, PeerLinkState>> = _peerStates.asStateFlow()

    private val _micEnabled = MutableStateFlow(false)
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    init {
        val channel = object : HostDataChannel {
            override fun send(playerId: String, message: GameMessage) { webrtcRef().sendTo(playerId, message) }
            override fun broadcast(message: GameMessage) { webrtcRef().broadcast(message) }
            override fun broadcastExcept(exceptPlayerId: String, message: GameMessage) {
                webrtcRef().broadcastExcept(exceptPlayerId, message)
            }
        }
        session = HostSession(
            gameId = Ids.playerId(),
            roomCode = roomCode,
            hostPlayerId = hostPlayerId,
            hostDisplayName = hostDisplayName,
            settings = settings,
            channel = channel,
            random = Random.Default,
            onStateChanged = { s ->
                _lobby.value = s.lobbyPlayers()
                _gameState.value = s.hostView()
            },
        )
        webrtc = WebRtcManager(
            context = context,
            myPlayerId = hostPlayerId,
            iceServers = iceServers,
            signalSender = { msg -> server.route(msg) },
            onGameMessage = { from, msg -> session.onClientMessage(from, msg) },
            onPeerState = { st -> _peerStates.value = _peerStates.value + (st.playerId to st) },
        )
        _lobby.value = session.lobbyPlayers()
    }

    private fun webrtcRef() = webrtc

    fun start() {
        server = HostSignalingServer(this)
        server.start(port)
        AppLog.i(AppLog.Area.ROOM, "Created room $roomCode on port $port")
    }

    /** The address other phones should use to join, and its QR payload. */
    fun bestAddress(): LocalAddress? = AddressUtils.bestAddress()
    fun allAddresses(): List<LocalAddress> = AddressUtils.localAddresses()
    fun joinUri(address: LocalAddress): String =
        "uno://join?host=${address.ip}&port=$port&room=$roomCode"

    // ---- Lobby controls ------------------------------------------------------

    fun setLocked(locked: Boolean) = session.setLocked(locked)
    fun kick(playerId: String) { session.kick(playerId); webrtc.removePeer(playerId) }
    fun startGame(): Result<Unit> = session.startGame()

    // ---- Host player actions -------------------------------------------------

    fun play(cardUid: String, chosenColor: CardColor? = null, declareUno: Boolean = false) =
        session.applyHostAction(PlayerAction.PlayCard(hostPlayerId, cardUid, chosenColor, declareUno))
    fun draw() = session.applyHostAction(PlayerAction.DrawCard(hostPlayerId))
    fun chooseColor(color: CardColor) = session.applyHostAction(PlayerAction.ChooseColor(hostPlayerId, color))
    fun callUno() = session.applyHostAction(PlayerAction.CallUno(hostPlayerId))
    fun challengeUno(target: String) = session.applyHostAction(PlayerAction.ChallengeUno(hostPlayerId, target))
    fun challengeDrawFour() = session.applyHostAction(PlayerAction.ChallengeDrawFour(hostPlayerId))

    fun setMicEnabled(enabled: Boolean) { webrtc.setMicEnabled(enabled); _micEnabled.value = enabled }

    fun shutdown() {
        session.endSession("Host ended the game")
        if (::server.isInitialized) server.stop()
        webrtc.close()
        AppLog.i(AppLog.Area.ROOM, "Room $roomCode closed")
    }

    // ---- SignalingHost implementation ---------------------------------------

    override fun admit(displayName: String): AdmitDecision {
        if (!session.canAccept(maxPlayers)) return AdmitDecision.Rejected("Room is full or locked")
        val playerId = Ids.playerId()
        val token = Ids.sessionToken()
        session.addPlayer(playerId, displayName, token)
        return AdmitDecision.Accepted(playerId, token)
    }

    override fun validate(playerId: String, token: String): Boolean = session.validateToken(playerId, token)

    override fun peerInfos(): List<PeerInfo> =
        session.lobbyPlayers().map { PeerInfo(it.playerId, it.displayName, it.playerId == hostPlayerId) }

    override fun onSignalToHost(message: SignalMessage) = webrtc.onSignal(message)

    override fun onSignalingConnected(playerId: String) {
        webrtc.connectPeer(playerId)
        session.markConnected(playerId, true)
    }

    override fun onSignalingDisconnected(playerId: String) {
        webrtc.removePeer(playerId)
        session.markConnected(playerId, false)
    }

    companion object { const val DEFAULT_PORT = 8080 }
}
