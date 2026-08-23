package com.unop2p.engine.session

import com.unop2p.engine.common.Ids
import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GameEvent
import com.unop2p.engine.game.PublicGameState
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
import com.unop2p.engine.protocol.LobbyUpdate
import com.unop2p.engine.protocol.PlayCardRequest
import com.unop2p.engine.protocol.Ping
import com.unop2p.engine.protocol.Pong
import com.unop2p.engine.protocol.RequestState
import com.unop2p.engine.protocol.SessionEnded
import com.unop2p.engine.protocol.SetReady
import com.unop2p.engine.protocol.StateUpdate
import com.unop2p.engine.protocol.Welcome
import kotlin.random.Random

/** Callbacks a client UI registers to observe session updates. */
interface ClientSessionListener {
    fun onState(state: PublicGameState) {}
    fun onEvents(stateVersion: Int, events: List<GameEvent>) {}
    fun onLobby(update: LobbyUpdate) {}
    fun onActionResult(result: ActionResult) {}
    fun onWelcome(playerId: String, displayName: String) {}
    fun onSessionEnded(reason: String) {}
    fun onError(message: String) {}
}

/**
 * Client-side session. Sends the local player's actions to the host (each with a
 * request id) and tracks the latest authoritative redacted snapshot. It performs
 * no rules logic itself — the host is the sole authority.
 */
class ClientSession(
    val myPlayerId: String,
    private val token: String,
    private val channel: ClientDataChannel,
    private val listener: ClientSessionListener,
    private val random: Random = Random.Default,
) {
    var latestState: PublicGameState? = null
        private set

    private var lastVersion = 0
    private val pending = HashSet<String>()

    /** Sends the initial Hello to claim the seat on this data channel. */
    fun connect() = channel.sendToHost(Hello(myPlayerId, token))

    // ---- Outbound actions ----------------------------------------------------

    fun setReady(ready: Boolean): String = send { SetReady(it, ready) }
    fun playCard(cardUid: String, chosenColor: CardColor? = null, declareUno: Boolean = false): String =
        send { PlayCardRequest(it, cardUid, chosenColor, declareUno) }
    fun drawCard(): String = send { DrawCardRequest(it) }
    fun chooseColor(color: CardColor): String = send { ChooseColorRequest(it, color) }
    fun callUno(): String = send { CallUnoRequest(it) }
    fun challengeUno(targetPlayerId: String): String = send { ChallengeUnoRequest(it, targetPlayerId) }
    fun challengeDrawFour(): String = send { ChallengeDrawFourRequest(it) }
    fun requestState(): String = send { RequestState(it, lastVersion) }
    fun ping(): String = send { Ping(it, System.currentTimeMillis()) }

    private inline fun send(build: (requestId: String) -> GameMessage): String {
        val id = Ids.requestId(random)
        pending += id
        channel.sendToHost(build(id))
        return id
    }

    // ---- Inbound handling ----------------------------------------------------

    fun onMessage(message: GameMessage) {
        when (message) {
            is Welcome -> listener.onWelcome(message.playerId, message.displayName)
            is StateUpdate -> {
                val incoming = message.state
                // Ignore stale snapshots; accept any newer authoritative state.
                if (incoming.stateVersion >= lastVersion) {
                    lastVersion = incoming.stateVersion
                    latestState = incoming
                    listener.onState(incoming)
                }
            }
            is EventsMessage -> {
                listener.onEvents(message.stateVersion, message.events)
                // If events reference a version ahead of our state, we missed a
                // snapshot — ask the host for a fresh one.
                if (message.stateVersion > lastVersion) requestState()
            }
            is LobbyUpdate -> listener.onLobby(message)
            is ActionResult -> {
                pending.remove(message.requestId)
                listener.onActionResult(message)
            }
            is Pong -> { /* latency sample; ignored for now */ }
            is SessionEnded -> listener.onSessionEnded(message.reason)
            is ErrorMessage -> listener.onError(message.message)
            else -> { /* client never receives client->host request types */ }
        }
    }
}
