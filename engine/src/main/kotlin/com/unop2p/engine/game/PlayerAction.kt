package com.unop2p.engine.game

import kotlinx.serialization.Serializable

/**
 * Engine-level actions a player can request. These are validated by the host
 * before being applied. The wire protocol (protocol package) maps to these.
 */
@Serializable
sealed interface PlayerAction {
    val playerId: String

    /** Play a card from hand. [chosenColor] is required for wild cards. */
    @Serializable
    data class PlayCard(
        override val playerId: String,
        val cardUid: String,
        val chosenColor: CardColor? = null,
        /** Player declares UNO simultaneously with the play that brings them to one card. */
        val declareUno: Boolean = false,
    ) : PlayerAction

    /** Draw a card (or, when a draw penalty is pending, draw the pending amount). */
    @Serializable
    data class DrawCard(override val playerId: String) : PlayerAction

    /** Choose the active color after playing a wild (phase AWAITING_COLOR). */
    @Serializable
    data class ChooseColor(override val playerId: String, val color: CardColor) : PlayerAction

    /** Declare UNO for one's own hand (valid when holding one or two cards). */
    @Serializable
    data class CallUno(override val playerId: String) : PlayerAction

    /** Catch another player who reached one card without declaring UNO. */
    @Serializable
    data class ChallengeUno(override val playerId: String, val targetPlayerId: String) : PlayerAction

    /** Challenge the most recent Wild Draw Four as an illegal bluff. */
    @Serializable
    data class ChallengeDrawFour(override val playerId: String) : PlayerAction
}
