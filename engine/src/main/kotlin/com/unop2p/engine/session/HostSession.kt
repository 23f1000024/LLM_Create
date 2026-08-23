package com.unop2p.engine.session

import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.ConnectionState
import com.unop2p.engine.game.GameEngine
import com.unop2p.engine.game.GameEvent
import com.unop2p.engine.game.GamePhase
import com.unop2p.engine.game.GameSettings
import com.unop2p.engine.game.GameState
import com.unop2p.engine.game.PlayerAction
import com.unop2p.engine.protocol.ActionResult
import com.unop2p.engine.protocol.CallUnoRequest
import com.unop2p.engine.protocol.ChallengeDrawFourRequest
import com.unop2p.engine.protocol.ChallengeUnoRequest
import com.unop2p.engine.protocol.ChooseColorRequest
import com.unop2p.engine.protocol.DrawCardRequest
import com.unop2p.engine.protocol.ErrorMessage
import com.unop2p.engine.protocol.EventsMessage
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.Hello
import com.unop2p.engine.protocol.LobbyPlayer
import com.unop2p.engine.protocol.LobbyUpdate
import com.unop2p.engine.protocol.PlayCardRequest
import com.unop2p.engine.protocol.Ping
import com.unop2p.engine.protocol.Pong
import com.unop2p.engine.protocol.RequestState
import com.unop2p.engine.protocol.SetReady
import com.unop2p.engine.protocol.SessionEnded
import com.unop2p.engine.protocol.StateUpdate
import com.unop2p.engine.protocol.Welcome
import kotlin.random.Random

/**
 * The authoritative game session, running only on the host phone. It owns the
 * single true [GameState], validates every client action through [GameEngine],
 * and pushes each client a *redacted* snapshot (own hand + opponents' counts).
 *
 * This class is deliberately free of any Android, WebRTC or Ktor dependency: it
 * talks to peers only through [HostDataChannel] and is driven by
 * [onClientMessage]. That makes the whole host authority unit-testable.
 *
 * Threading: all methods must be invoked on a single dispatcher/thread. The
 * Android layer confines calls to one coroutine context.
 */
class HostSession(
    val gameId: String,
    val roomCode: String,
    val hostPlayerId: String,
    hostDisplayName: String,
    private var settings: GameSettings,
    private val channel: HostDataChannel,
    private val random: Random = Random.Default,
    /** Notified whenever the authoritative state changes, for the host's own UI. */
    private val onStateChanged: (HostSession) -> Unit = {},
) {
    private data class Seat(
        val playerId: String,
        var displayName: String,
        val token: String,
        var ready: Boolean = false,
        var connected: Boolean = false,
        val isHost: Boolean = false,
    )

    private val engine = GameEngine(random)
    private val seats = LinkedHashMap<String, Seat>()

    var locked: Boolean = false
        private set

    /** Null while in the lobby; set once the game starts. */
    var state: GameState? = null
        private set

    init {
        // The host always occupies seat 0 and is considered connected & ready.
        seats[hostPlayerId] = Seat(
            playerId = hostPlayerId,
            displayName = hostDisplayName,
            token = "",
            ready = true,
            connected = true,
            isHost = true,
        )
    }

    // ---- Lobby management (driven by the signaling layer) --------------------

    /** Whether another player can still be admitted. */
    fun canAccept(maxPlayers: Int): Boolean = !locked && state == null && seats.size < maxPlayers

    /** Registers a newly-joined player (called by the signaling server on accept). */
    fun addPlayer(playerId: String, displayName: String, token: String) {
        seats[playerId] = Seat(playerId, sanitizeName(displayName), token)
        broadcastLobby()
        onStateChanged(this)
    }

    fun validateToken(playerId: String, token: String): Boolean =
        seats[playerId]?.token?.let { it.isNotEmpty() && it == token } ?: false

    fun knownPlayer(playerId: String): Boolean = seats.containsKey(playerId)

    fun markConnected(playerId: String, connected: Boolean) {
        val seat = seats[playerId] ?: return
        seat.connected = connected
        state = state?.let { s ->
            val idx = s.indexOf(playerId)
            if (idx < 0) s else {
                val players = s.players.toMutableList()
                players[idx] = players[idx].copy(
                    connectionState = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED,
                )
                s.copy(players = players)
            }
        }
        if (state == null) broadcastLobby() else broadcastState(emptyList())
        onStateChanged(this)
    }

    fun kick(playerId: String) {
        if (playerId == hostPlayerId) return
        seats.remove(playerId)
        channel.send(playerId, SessionEnded("Removed by host"))
        if (state == null) broadcastLobby() else broadcastState(emptyList())
        onStateChanged(this)
    }

    fun setLocked(value: Boolean) {
        locked = value
        broadcastLobby()
    }

    fun updateSettings(newSettings: GameSettings) {
        if (state != null) return // settings are frozen once the game starts
        settings = newSettings
        broadcastLobby()
    }

    fun lobbyPlayers(): List<LobbyPlayer> =
        seats.values.map { LobbyPlayer(it.playerId, it.displayName, it.ready, it.connected) }

    // ---- Starting the game ---------------------------------------------------

    /** Starts the game with all currently-seated players (host first). */
    fun startGame(): Result<Unit> {
        if (state != null) return Result.failure(IllegalStateException("Game already started"))
        val seated = seats.values.toList()
        if (seated.size < 2) return Result.failure(IllegalStateException("Need at least 2 players"))
        val seeds = seated.map { it.playerId to it.displayName }
        val result = engine.startGame(gameId, seeds, settings)
        state = result.state
        // Reflect real connection state onto the freshly dealt players.
        state = state!!.copy(
            players = state!!.players.map { p ->
                p.copy(connectionState = if (seats[p.playerId]?.connected == true) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
            },
        )
        broadcastState(result.events)
        onStateChanged(this)
        return Result.success(Unit)
    }

    /** Ends the session for everyone (e.g. the host is leaving). */
    fun endSession(reason: String) {
        channel.broadcast(SessionEnded(reason))
    }

    // ---- Inbound message handling -------------------------------------------

    /**
     * Handles a message that arrived on [playerId]'s data channel. [playerId] is
     * authenticated by the transport (it is the seat whose token was validated
     * during signaling), so messages cannot be spoofed for another seat here.
     */
    fun onClientMessage(playerId: String, message: GameMessage) {
        if (!seats.containsKey(playerId)) return // unknown seat: ignore
        when (message) {
            is Hello -> {
                markConnected(playerId, true)
                val seat = seats[playerId]!!
                channel.send(playerId, Welcome(playerId, seat.displayName))
                if (state == null) sendLobbyTo(playerId) else sendStateTo(playerId)
            }
            is SetReady -> {
                seats[playerId]?.ready = message.ready
                channel.send(playerId, ActionResult(message.requestId, accepted = true))
                broadcastLobby()
                onStateChanged(this)
            }
            is RequestState -> {
                if (state != null) sendStateTo(playerId) else sendLobbyTo(playerId)
            }
            is Ping -> channel.send(playerId, Pong(message.requestId, message.timestamp))
            is PlayCardRequest -> applyAction(
                playerId, message.requestId,
                PlayerAction.PlayCard(playerId, message.cardUid, message.chosenColor, message.declareUno),
            )
            is DrawCardRequest -> applyAction(playerId, message.requestId, PlayerAction.DrawCard(playerId))
            is ChooseColorRequest -> applyAction(
                playerId, message.requestId, PlayerAction.ChooseColor(playerId, message.color),
            )
            is CallUnoRequest -> applyAction(playerId, message.requestId, PlayerAction.CallUno(playerId))
            is ChallengeUnoRequest -> applyAction(
                playerId, message.requestId, PlayerAction.ChallengeUno(playerId, message.targetPlayerId),
            )
            is ChallengeDrawFourRequest ->
                applyAction(playerId, message.requestId, PlayerAction.ChallengeDrawFour(playerId))
            // Host->Client message types are ignored if received from a client.
            else -> channel.send(playerId, ErrorMessage(message = "Unexpected message"))
        }
    }

    /** Lets the host player (seat 0, local) take an action without a data channel. */
    fun applyHostAction(action: PlayerAction): EngineActionOutcome =
        applyAction(hostPlayerId, requestId = null, action)

    private fun applyAction(playerId: String, requestId: String?, action: PlayerAction): EngineActionOutcome {
        val current = state
        if (current == null) {
            requestId?.let { channel.send(playerId, ActionResult(it, accepted = false, error = "Game not started")) }
            return EngineActionOutcome(false, "Game not started")
        }
        val result = engine.apply(current, action)
        if (!result.accepted) {
            requestId?.let { channel.send(playerId, ActionResult(it, accepted = false, error = result.error)) }
            return EngineActionOutcome(false, result.error)
        }
        state = result.state
        requestId?.let { channel.send(playerId, ActionResult(it, accepted = true)) }
        broadcastState(result.events)
        onStateChanged(this)
        return EngineActionOutcome(true, null)
    }

    // ---- Outbound helpers ----------------------------------------------------

    /** The host's own redacted view (its hand + opponents' counts). */
    fun hostView() = state?.redactedFor(hostPlayerId)

    fun publicStateFor(playerId: String) = state?.redactedFor(playerId)

    private fun broadcastState(events: List<GameEvent>) {
        val s = state ?: return
        // Every *remote* connected player gets their own redacted snapshot.
        seats.values.filter { !it.isHost && it.connected }.forEach { seat ->
            channel.send(seat.playerId, StateUpdate(s.redactedFor(seat.playerId)))
        }
        if (events.isNotEmpty()) channel.broadcast(EventsMessage(s.stateVersion, events))
    }

    private fun sendStateTo(playerId: String) {
        val s = state ?: return
        channel.send(playerId, StateUpdate(s.redactedFor(playerId)))
    }

    private fun broadcastLobby() {
        channel.broadcast(LobbyUpdate(lobbyPlayers(), settings, locked))
    }

    private fun sendLobbyTo(playerId: String) {
        channel.send(playerId, LobbyUpdate(lobbyPlayers(), settings, locked))
    }

    private fun sanitizeName(name: String): String =
        name.trim().take(24).ifEmpty { "Player" }

    val phase: GamePhase get() = state?.phase ?: GamePhase.LOBBY
}

/** Result of an action applied on the host. */
data class EngineActionOutcome(val accepted: Boolean, val error: String?)

/** Convenience for host UI wild plays. */
fun HostSession.hostPlayWild(cardUid: String, color: CardColor, declareUno: Boolean = false) =
    applyHostAction(PlayerAction.PlayCard(hostPlayerId, cardUid, color, declareUno))
