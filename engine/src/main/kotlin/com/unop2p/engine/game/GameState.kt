package com.unop2p.engine.game

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase { LOBBY, PLAYING, AWAITING_COLOR, FINISHED }

@Serializable
enum class Direction {
    CLOCKWISE, COUNTER_CLOCKWISE;

    val step: Int get() = if (this == CLOCKWISE) 1 else -1
    fun reversed(): Direction = if (this == CLOCKWISE) COUNTER_CLOCKWISE else CLOCKWISE
}

/** Rule variations chosen by the host at room creation. */
@Serializable
data class GameSettings(
    val startingHandSize: Int = 7,
    /** Allow stacking Draw Two / Wild Draw Four onto a pending draw. Off = official rules. */
    val allowStacking: Boolean = false,
    /** Enable the Wild Draw Four bluff challenge. */
    val enableDrawFourChallenge: Boolean = true,
    /** Penalty drawn when caught not declaring UNO. */
    val unoPenaltyCards: Int = 2,
)

/**
 * The complete authoritative game state. Held only by the host. Clients receive
 * a [PublicGameState] (via [redactedFor]) that hides other players' hands.
 *
 * [stateVersion] increments on every applied action so clients can detect when
 * they are behind and request a full snapshot.
 */
@Serializable
data class GameState(
    val gameId: String,
    val phase: GamePhase,
    val players: List<Player>,
    val currentPlayerIndex: Int,
    val direction: Direction,
    val drawPile: List<Card>,
    val discardPile: List<Card>,
    val chosenColor: CardColor?,
    val pendingDraw: Int,
    val pendingDrawKind: CardKind?,
    val turnNumber: Int,
    val stateVersion: Int,
    val winnerId: String?,
    val settings: GameSettings,
    // Bookkeeping for the Wild Draw Four challenge:
    val lastWildDrawFourPlayerId: String? = null,
    val lastWildDrawFourWasBluff: Boolean = false,
    /** When phase == AWAITING_COLOR, the id of the player who must choose a color. */
    val awaitingColorPlayerId: String? = null,
) {
    val topCard: Card? get() = discardPile.lastOrNull()

    /** The color currently in play: the chosen color if the top is a wild, else the top card's color. */
    val activeColor: CardColor? get() {
        val top = topCard ?: return null
        return if (top.kind.isWild) chosenColor else top.color
    }

    val currentPlayer: Player? get() = players.getOrNull(currentPlayerIndex)

    fun playerById(id: String): Player? = players.firstOrNull { it.playerId == id }
    fun indexOf(id: String): Int = players.indexOfFirst { it.playerId == id }

    fun isFinished(): Boolean = phase == GamePhase.FINISHED

    /**
     * Produces the view a specific client is allowed to see: its own full hand,
     * plus only the *count* of every other player's hand. Never leak private cards.
     */
    fun redactedFor(viewerId: String): PublicGameState = PublicGameState(
        gameId = gameId,
        phase = phase,
        players = players.map { p ->
            PublicPlayer(
                playerId = p.playerId,
                displayName = p.displayName,
                handCount = p.handCount,
                connectionState = p.connectionState,
                isReady = p.isReady,
                hasDeclaredUno = p.hasDeclaredUno,
                unoVulnerable = p.unoVulnerable,
                hand = if (p.playerId == viewerId) p.hand else null,
            )
        },
        currentPlayerId = currentPlayer?.playerId,
        direction = direction,
        drawPileCount = drawPile.size,
        topCard = topCard,
        activeColor = activeColor,
        pendingDraw = pendingDraw,
        pendingDrawKind = pendingDrawKind,
        turnNumber = turnNumber,
        stateVersion = stateVersion,
        winnerId = winnerId,
        settings = settings,
    )
}

/** A player as seen by clients: hand is present only for the viewer, else null. */
@Serializable
data class PublicPlayer(
    val playerId: String,
    val displayName: String,
    val handCount: Int,
    val connectionState: ConnectionState,
    val isReady: Boolean,
    val hasDeclaredUno: Boolean,
    val unoVulnerable: Boolean,
    val hand: List<Card>?,
)

/** The redacted snapshot broadcast to clients. Contains no other player's cards. */
@Serializable
data class PublicGameState(
    val gameId: String,
    val phase: GamePhase,
    val players: List<PublicPlayer>,
    val currentPlayerId: String?,
    val direction: Direction,
    val drawPileCount: Int,
    val topCard: Card?,
    val activeColor: CardColor?,
    val pendingDraw: Int,
    val pendingDrawKind: CardKind?,
    val turnNumber: Int,
    val stateVersion: Int,
    val winnerId: String?,
    val settings: GameSettings,
)
