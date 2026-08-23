package com.unop2p.engine.game

import kotlinx.serialization.Serializable

/**
 * Facts emitted by the engine when an action is applied. The host broadcasts
 * these to clients (for animations, sound, logging) alongside the authoritative
 * state snapshot. Events are descriptive, not authoritative — state is truth.
 */
@Serializable
sealed interface GameEvent {
    @Serializable
    data class GameStarted(val firstPlayerId: String, val topCard: Card) : GameEvent

    @Serializable
    data class CardPlayed(val playerId: String, val card: Card) : GameEvent

    @Serializable
    data class ColorChosen(val playerId: String, val color: CardColor) : GameEvent

    @Serializable
    data class CardsDrawn(val playerId: String, val count: Int) : GameEvent

    @Serializable
    data class TurnChanged(val playerId: String) : GameEvent

    @Serializable
    data class DirectionReversed(val direction: Direction) : GameEvent

    @Serializable
    data class PlayerSkipped(val playerId: String) : GameEvent

    @Serializable
    data class DrawPenaltyApplied(val playerId: String, val count: Int) : GameEvent

    @Serializable
    data class UnoDeclared(val playerId: String) : GameEvent

    @Serializable
    data class UnoPenalty(val playerId: String, val count: Int) : GameEvent

    @Serializable
    data class DrawFourChallenge(
        val challengerId: String,
        val targetId: String,
        val successful: Boolean,
        val penaltyCards: Int,
    ) : GameEvent

    @Serializable
    data class DeckReshuffled(val newDrawPileSize: Int) : GameEvent

    @Serializable
    data class GameFinished(val winnerId: String) : GameEvent
}
