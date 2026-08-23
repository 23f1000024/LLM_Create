package com.unop2p.engine.protocol

import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GameEvent
import com.unop2p.engine.game.GameSettings
import com.unop2p.engine.game.PublicGameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Messages exchanged over the WebRTC DataChannel *after* peers are connected.
 * This is the game protocol — it never travels through the signaling server.
 *
 * A single sealed hierarchy is used so kotlinx.serialization tags each message
 * with a "type" discriminator (see [ProtocolJson]). Client actions carry a
 * [requestId] so the host's [ActionResult] can be correlated and duplicates
 * detected.
 */
@Serializable
sealed interface GameMessage

// ---- Client -> Host ---------------------------------------------------------

/** First message a client sends on its data channel to identify its seat. */
@Serializable
@SerialName("hello")
data class Hello(val playerId: String, val token: String) : GameMessage

@Serializable
@SerialName("set_ready")
data class SetReady(val requestId: String, val ready: Boolean) : GameMessage

@Serializable
@SerialName("play_card")
data class PlayCardRequest(
    val requestId: String,
    val cardUid: String,
    val chosenColor: CardColor? = null,
    val declareUno: Boolean = false,
) : GameMessage

@Serializable
@SerialName("draw_card")
data class DrawCardRequest(val requestId: String) : GameMessage

@Serializable
@SerialName("choose_color")
data class ChooseColorRequest(val requestId: String, val color: CardColor) : GameMessage

@Serializable
@SerialName("call_uno")
data class CallUnoRequest(val requestId: String) : GameMessage

@Serializable
@SerialName("challenge_uno")
data class ChallengeUnoRequest(val requestId: String, val targetPlayerId: String) : GameMessage

@Serializable
@SerialName("challenge_draw_four")
data class ChallengeDrawFourRequest(val requestId: String) : GameMessage

/** Ask the host for a fresh full snapshot (e.g. after detecting a version gap). */
@Serializable
@SerialName("request_state")
data class RequestState(val requestId: String, val knownVersion: Int? = null) : GameMessage

@Serializable
@SerialName("ping")
data class Ping(val requestId: String, val timestamp: Long) : GameMessage

// ---- Host -> Client ---------------------------------------------------------

/** Host acknowledges a client's data channel and confirms its seat. */
@Serializable
@SerialName("welcome")
data class Welcome(val playerId: String, val displayName: String) : GameMessage

/** Result of a client action, correlated by [requestId]. */
@Serializable
@SerialName("action_result")
data class ActionResult(
    val requestId: String,
    val accepted: Boolean,
    val error: String? = null,
) : GameMessage

/**
 * Authoritative, per-recipient redacted snapshot. Each client receives a state
 * containing only its own hand plus opponents' card counts.
 */
@Serializable
@SerialName("state_update")
data class StateUpdate(val state: PublicGameState) : GameMessage

/** Descriptive events for animations/sound; state remains the source of truth. */
@Serializable
@SerialName("events")
data class EventsMessage(val stateVersion: Int, val events: List<GameEvent>) : GameMessage

/** Lobby roster + settings, sent before the game starts. */
@Serializable
@SerialName("lobby_update")
data class LobbyUpdate(val players: List<LobbyPlayer>, val settings: GameSettings, val locked: Boolean) : GameMessage

@Serializable
data class LobbyPlayer(val playerId: String, val displayName: String, val ready: Boolean, val connected: Boolean)

@Serializable
@SerialName("pong")
data class Pong(val requestId: String, val timestamp: Long) : GameMessage

/** Terminal message: the host is ending the session (e.g. host left). */
@Serializable
@SerialName("session_ended")
data class SessionEnded(val reason: String) : GameMessage

@Serializable
@SerialName("error")
data class ErrorMessage(val requestId: String? = null, val message: String) : GameMessage
