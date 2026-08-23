package com.unop2p.engine.game

import kotlinx.serialization.Serializable

/** The four play colors plus WILD for wild cards (which have no fixed color). */
@Serializable
enum class CardColor { RED, YELLOW, GREEN, BLUE, WILD }

/** Every distinct kind of card face. */
@Serializable
enum class CardKind {
    NUMBER,
    SKIP,
    REVERSE,
    DRAW_TWO,
    WILD,
    WILD_DRAW_FOUR,
    ;

    val isWild: Boolean get() = this == WILD || this == WILD_DRAW_FOUR
    val isDraw: Boolean get() = this == DRAW_TWO || this == WILD_DRAW_FOUR
    /** Number of cards this card forces the next player to draw (0 if none). */
    val drawAmount: Int get() = when (this) {
        DRAW_TWO -> 2
        WILD_DRAW_FOUR -> 4
        else -> 0
    }
}

/**
 * A single physical card. [uid] is unique per physical card in the deck (two
 * identical "red 7" cards get different uids) so a card can be referenced
 * unambiguously across the wire and matched against a player's hand.
 *
 * For number cards [number] is 0..9; for all other kinds it is null.
 */
@Serializable
data class Card(
    val uid: String,
    val color: CardColor,
    val kind: CardKind,
    val number: Int? = null,
) {
    init {
        if (kind == CardKind.NUMBER) {
            require(number in 0..9) { "Number card must have number 0..9, was $number" }
            require(color != CardColor.WILD) { "Number card cannot be WILD colored" }
        } else {
            require(number == null) { "Non-number card must not carry a number" }
        }
        if (kind.isWild) {
            require(color == CardColor.WILD) { "Wild card must have WILD color" }
        } else {
            require(color != CardColor.WILD) { "Non-wild card must have a concrete color" }
        }
    }

    /** Points value used for scoring (standard UNO scoring). */
    val points: Int get() = when (kind) {
        CardKind.NUMBER -> number ?: 0
        CardKind.SKIP, CardKind.REVERSE, CardKind.DRAW_TWO -> 20
        CardKind.WILD, CardKind.WILD_DRAW_FOUR -> 50
    }

    /** Short human/debug label, e.g. "RED 7", "BLUE SKIP", "WILD_DRAW_FOUR". */
    val label: String get() = when (kind) {
        CardKind.NUMBER -> "${color.name} $number"
        CardKind.WILD, CardKind.WILD_DRAW_FOUR -> kind.name
        else -> "${color.name} ${kind.name}"
    }
}
