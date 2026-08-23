package com.unop2p.engine.game

import kotlin.random.Random

/**
 * Standard 108-card UNO deck construction and shuffling.
 *
 * Composition (108 cards total):
 *  - Per color (RED/YELLOW/GREEN/BLUE): one 0, and two each of 1..9  → 19 * 4 = 76
 *  - Per color: two Skip, two Reverse, two Draw Two                  →  6 * 4 = 24
 *  - Four Wild, four Wild Draw Four                                  →         8
 */
object Deck {

    val COLORS: List<CardColor> = listOf(CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE)

    const val FULL_DECK_SIZE = 108

    /** Builds a fresh, ordered (unshuffled) 108-card deck with unique uids. */
    fun standardDeck(): List<Card> {
        val cards = ArrayList<Card>(FULL_DECK_SIZE)
        var seq = 0
        fun uid(prefix: String): String = "c${seq++}_$prefix"

        for (color in COLORS) {
            // one 0
            cards += Card(uid("${color.name}_0"), color, CardKind.NUMBER, 0)
            // two of each 1..9
            for (n in 1..9) {
                repeat(2) { cards += Card(uid("${color.name}_$n"), color, CardKind.NUMBER, n) }
            }
            // two each of Skip, Reverse, Draw Two
            repeat(2) { cards += Card(uid("${color.name}_SKIP"), color, CardKind.SKIP) }
            repeat(2) { cards += Card(uid("${color.name}_REVERSE"), color, CardKind.REVERSE) }
            repeat(2) { cards += Card(uid("${color.name}_DRAW_TWO"), color, CardKind.DRAW_TWO) }
        }
        // four Wild + four Wild Draw Four
        repeat(4) { cards += Card(uid("WILD"), CardColor.WILD, CardKind.WILD) }
        repeat(4) { cards += Card(uid("WD4"), CardColor.WILD, CardKind.WILD_DRAW_FOUR) }

        check(cards.size == FULL_DECK_SIZE) { "Deck size ${cards.size} != $FULL_DECK_SIZE" }
        return cards
    }

    /** Returns a new shuffled copy of [cards] using [random]. */
    fun shuffled(cards: List<Card>, random: Random): List<Card> = cards.shuffled(random)

    /** Convenience: a freshly shuffled standard deck. */
    fun shuffledStandardDeck(random: Random): List<Card> = shuffled(standardDeck(), random)
}
