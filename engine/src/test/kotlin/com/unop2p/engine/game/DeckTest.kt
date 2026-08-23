package com.unop2p.engine.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DeckTest {

    @Test
    fun `standard deck has 108 cards`() {
        assertEquals(108, Deck.standardDeck().size)
    }

    @Test
    fun `deck composition matches official UNO`() {
        val deck = Deck.standardDeck()
        // 76 number cards: per color one 0 and two each 1..9
        assertEquals(76, deck.count { it.kind == CardKind.NUMBER })
        for (color in Deck.COLORS) {
            assertEquals(1, deck.count { it.color == color && it.kind == CardKind.NUMBER && it.number == 0 })
            for (n in 1..9) {
                assertEquals(2, deck.count { it.color == color && it.kind == CardKind.NUMBER && it.number == n })
            }
            assertEquals(2, deck.count { it.color == color && it.kind == CardKind.SKIP })
            assertEquals(2, deck.count { it.color == color && it.kind == CardKind.REVERSE })
            assertEquals(2, deck.count { it.color == color && it.kind == CardKind.DRAW_TWO })
        }
        assertEquals(4, deck.count { it.kind == CardKind.WILD })
        assertEquals(4, deck.count { it.kind == CardKind.WILD_DRAW_FOUR })
    }

    @Test
    fun `every card has a unique uid`() {
        val deck = Deck.standardDeck()
        assertEquals(deck.size, deck.map { it.uid }.toSet().size)
    }

    @Test
    fun `shuffle preserves the multiset of cards`() {
        val deck = Deck.standardDeck()
        val shuffled = Deck.shuffled(deck, Random(42))
        assertEquals(deck.size, shuffled.size)
        assertEquals(deck.toSet(), shuffled.toSet())
        // With a fixed seed the order should actually change (not a no-op shuffle).
        assertTrue(deck != shuffled)
    }

    @Test
    fun `shuffle is deterministic for a fixed seed`() {
        val a = Deck.shuffled(Deck.standardDeck(), Random(7))
        val b = Deck.shuffled(Deck.standardDeck(), Random(7))
        assertEquals(a.map { it.uid }, b.map { it.uid })
    }
}
