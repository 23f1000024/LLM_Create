package com.unop2p.engine.game

import com.unop2p.engine.game.TestCards.drawTwo
import com.unop2p.engine.game.TestCards.num
import com.unop2p.engine.game.TestCards.reverse
import com.unop2p.engine.game.TestCards.skip
import com.unop2p.engine.game.TestCards.wild
import com.unop2p.engine.game.TestCards.wildDrawFour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GameEngineTest {

    private val engine = GameEngine(Random(1234))

    // ---- Setup / dealing -----------------------------------------------------

    @Test
    fun `startGame deals correct hands and a numeric starter`() {
        val res = engine.startGame("g", listOf("a" to "A", "b" to "B", "c" to "C", "d" to "D"))
        val s = res.state
        assertEquals(GamePhase.PLAYING, s.phase)
        assertEquals(4, s.players.size)
        s.players.forEach { assertEquals(7, it.handCount) }
        assertEquals(CardKind.NUMBER, s.topCard!!.kind)
        // 108 - (4*7) - 1 starter = 79
        assertEquals(79, s.drawPile.size)
        assertEquals("a", s.currentPlayer!!.playerId)
    }

    @Test
    fun `startGame rejects fewer than two players`() {
        try {
            engine.startGame("g", listOf("a" to "A"))
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    // ---- Legality ------------------------------------------------------------

    @Test
    fun `color match number match and symbol match are legal`() {
        val s = TestCards.playing(listOf("a" to emptyList()), top = num(CardColor.RED, 5))
        assertTrue(GameRules.isPlayable(num(CardColor.RED, 9), s))   // color
        assertTrue(GameRules.isPlayable(num(CardColor.BLUE, 5), s))  // number
        assertTrue(GameRules.isPlayable(skip(CardColor.RED), s))     // color
        assertTrue(GameRules.isPlayable(wild(), s))                  // wild always
        assertTrue(GameRules.isPlayable(wildDrawFour(), s))
        assertFalse(GameRules.isPlayable(num(CardColor.BLUE, 9), s)) // neither color nor number
    }

    @Test
    fun `symbol match works across colors`() {
        val s = TestCards.playing(listOf("a" to emptyList()), top = skip(CardColor.RED))
        assertTrue(GameRules.isPlayable(skip(CardColor.BLUE), s))
        assertFalse(GameRules.isPlayable(reverse(CardColor.BLUE), s))
    }

    @Test
    fun `wild active color is respected`() {
        val s = TestCards.playing(
            listOf("a" to emptyList()), top = wild(), chosenColor = CardColor.GREEN,
        )
        assertEquals(CardColor.GREEN, s.activeColor)
        assertTrue(GameRules.isPlayable(num(CardColor.GREEN, 3), s))
        assertFalse(GameRules.isPlayable(num(CardColor.RED, 3), s))
    }

    // ---- Turn order / action cards ------------------------------------------

    @Test
    fun `number card advances one seat`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3, num(CardColor.BLUE, 7)), "b" to listOf(num(CardColor.BLUE, 1)), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", red3.uid))
        assertTrue(res.accepted)
        assertEquals("b", res.state.currentPlayer!!.playerId)
    }

    @Test
    fun `skip jumps the next player`() {
        val redSkip = skip(CardColor.RED)
        val s = TestCards.playing(
            listOf("a" to listOf(redSkip, num(CardColor.BLUE, 7)), "b" to emptyList(), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", redSkip.uid))
        assertEquals("c", res.state.currentPlayer!!.playerId)
        assertTrue(res.events.any { it is GameEvent.PlayerSkipped && it.playerId == "b" })
    }

    @Test
    fun `reverse flips direction with three players`() {
        val redRev = reverse(CardColor.RED)
        val s = TestCards.playing(
            listOf("a" to listOf(redRev, num(CardColor.BLUE, 7)), "b" to emptyList(), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", redRev.uid))
        assertEquals(Direction.COUNTER_CLOCKWISE, res.state.direction)
        assertEquals("c", res.state.currentPlayer!!.playerId) // previous seat in new direction
    }

    @Test
    fun `reverse acts as skip with two players`() {
        val redRev = reverse(CardColor.RED)
        val s = TestCards.playing(
            listOf("a" to listOf(redRev, num(CardColor.BLUE, 7)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", redRev.uid))
        assertEquals("a", res.state.currentPlayer!!.playerId) // returns to the same player
    }

    @Test
    fun `draw two forces the next player to draw two and be skipped`() {
        val redD2 = drawTwo(CardColor.RED)
        val s = TestCards.playing(
            listOf("a" to listOf(redD2, num(CardColor.BLUE, 7)), "b" to emptyList(), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val afterPlay = engine.apply(s, PlayerAction.PlayCard("a", redD2.uid))
        assertEquals(2, afterPlay.state.pendingDraw)
        assertEquals("b", afterPlay.state.currentPlayer!!.playerId)

        val afterDraw = engine.apply(afterPlay.state, PlayerAction.DrawCard("b"))
        assertEquals(2, afterDraw.state.playerById("b")!!.handCount)
        assertEquals(0, afterDraw.state.pendingDraw)
        assertEquals("c", afterDraw.state.currentPlayer!!.playerId) // b was skipped
    }

    // ---- Wilds ---------------------------------------------------------------

    @Test
    fun `wild with supplied color sets active color and advances`() {
        val w = wild()
        val s = TestCards.playing(
            listOf("a" to listOf(w, num(CardColor.BLUE, 7)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", w.uid, chosenColor = CardColor.BLUE))
        assertTrue(res.accepted)
        assertEquals(CardColor.BLUE, res.state.activeColor)
        assertEquals("b", res.state.currentPlayer!!.playerId)
    }

    @Test
    fun `wild without color enters AWAITING_COLOR then resolves on ChooseColor`() {
        val w = wild()
        val s = TestCards.playing(
            listOf("a" to listOf(w, num(CardColor.RED, 1)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val played = engine.apply(s, PlayerAction.PlayCard("a", w.uid))
        assertEquals(GamePhase.AWAITING_COLOR, played.state.phase)
        assertEquals("a", played.state.awaitingColorPlayerId)
        // b cannot act while color is pending
        assertFalse(engine.apply(played.state, PlayerAction.DrawCard("b")).accepted)

        val chosen = engine.apply(played.state, PlayerAction.ChooseColor("a", CardColor.GREEN))
        assertTrue(chosen.accepted)
        assertEquals(GamePhase.PLAYING, chosen.state.phase)
        assertEquals(CardColor.GREEN, chosen.state.activeColor)
        assertEquals("b", chosen.state.currentPlayer!!.playerId)
    }

    @Test
    fun `wild draw four makes next player draw four`() {
        val wd4 = wildDrawFour()
        // 'a' holds only the WD4 plus a blue card (no red) -> not a bluff vs red top.
        val s = TestCards.playing(
            listOf("a" to listOf(wd4, num(CardColor.BLUE, 2)), "b" to emptyList(), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val played = engine.apply(s, PlayerAction.PlayCard("a", wd4.uid, chosenColor = CardColor.BLUE))
        assertEquals(4, played.state.pendingDraw)
        assertEquals(CardKind.WILD_DRAW_FOUR, played.state.pendingDrawKind)
        assertEquals("b", played.state.currentPlayer!!.playerId)

        val drew = engine.apply(played.state, PlayerAction.DrawCard("b"))
        assertEquals(4, drew.state.playerById("b")!!.handCount)
        assertEquals("c", drew.state.currentPlayer!!.playerId)
    }

    @Test
    fun `successful draw four challenge penalizes the bluffer`() {
        val wd4 = wildDrawFour()
        // 'a' holds a RED card while top is RED -> playing WD4 is a bluff.
        val s = TestCards.playing(
            listOf("a" to listOf(wd4, num(CardColor.RED, 9)), "b" to listOf(num(CardColor.BLUE, 1)), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val played = engine.apply(s, PlayerAction.PlayCard("a", wd4.uid, chosenColor = CardColor.BLUE))
        assertTrue(played.state.lastWildDrawFourWasBluff)

        val challenge = engine.apply(played.state, PlayerAction.ChallengeDrawFour("b"))
        assertTrue(challenge.accepted)
        // Bluffer 'a' draws the 4; challenger 'b' keeps their turn and draws nothing.
        assertEquals(1 + 4, challenge.state.playerById("a")!!.handCount)
        assertEquals(1, challenge.state.playerById("b")!!.handCount)
        assertEquals(0, challenge.state.pendingDraw)
        assertEquals("b", challenge.state.currentPlayer!!.playerId)
        assertTrue(challenge.events.any { it is GameEvent.DrawFourChallenge && it.successful })
    }

    @Test
    fun `failed draw four challenge penalizes the challenger by six`() {
        val wd4 = wildDrawFour()
        // 'a' has no RED -> WD4 is legal, challenge should fail.
        val s = TestCards.playing(
            listOf("a" to listOf(wd4, num(CardColor.BLUE, 2)), "b" to listOf(num(CardColor.GREEN, 1)), "c" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val played = engine.apply(s, PlayerAction.PlayCard("a", wd4.uid, chosenColor = CardColor.BLUE))
        assertFalse(played.state.lastWildDrawFourWasBluff)

        val challenge = engine.apply(played.state, PlayerAction.ChallengeDrawFour("b"))
        assertTrue(challenge.accepted)
        assertEquals(1 + 6, challenge.state.playerById("b")!!.handCount) // 4 + 2 penalty
        assertEquals("c", challenge.state.currentPlayer!!.playerId)       // b forfeits turn
        assertTrue(challenge.events.any { it is GameEvent.DrawFourChallenge && !it.successful })
    }

    // ---- Reshuffle -----------------------------------------------------------

    @Test
    fun `empty draw pile reshuffles the discard pile`() {
        val red3 = num(CardColor.RED, 3)
        // Draw pile empty; discard has several cards under the top to reshuffle.
        val discardUnder = List(5) { num(CardColor.GREEN, it) }
        val s = TestCards.playing(
            listOf("a" to listOf(red3, num(CardColor.BLUE, 7)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
            drawPile = emptyList(),
        ).let { it.copy(discardPile = discardUnder + it.discardPile) }

        // 'b' draws normally; the engine must reshuffle to supply a card.
        val playARed = engine.apply(s, PlayerAction.PlayCard("a", red3.uid))
        val res = engine.apply(playARed.state, PlayerAction.DrawCard("b"))
        assertTrue(res.accepted)
        assertEquals(1, res.state.playerById("b")!!.handCount)
        assertTrue(res.events.any { it is GameEvent.DeckReshuffled })
    }

    // ---- Win detection -------------------------------------------------------

    @Test
    fun `playing the last card wins the game`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3), "b" to listOf(num(CardColor.BLUE, 1))),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", red3.uid, declareUno = true))
        assertEquals(GamePhase.FINISHED, res.state.phase)
        assertEquals("a", res.state.winnerId)
        assertTrue(res.events.any { it is GameEvent.GameFinished && it.winnerId == "a" })
    }

    // ---- UNO -----------------------------------------------------------------

    @Test
    fun `declaring uno on the play protects the player`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3, num(CardColor.RED, 8)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", red3.uid, declareUno = true))
        val a = res.state.playerById("a")!!
        assertEquals(1, a.handCount)
        assertTrue(a.hasDeclaredUno)
        assertFalse(a.unoVulnerable)
    }

    @Test
    fun `not declaring uno leaves the player catchable`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3, num(CardColor.RED, 8)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", red3.uid, declareUno = false))
        assertTrue(res.state.playerById("a")!!.unoVulnerable)

        val caught = engine.apply(res.state, PlayerAction.ChallengeUno("b", "a"))
        assertTrue(caught.accepted)
        assertEquals(1 + 2, caught.state.playerById("a")!!.handCount) // drew penalty
        assertFalse(caught.state.playerById("a")!!.unoVulnerable)
        assertTrue(caught.events.any { it is GameEvent.UnoPenalty })
    }

    @Test
    fun `challenging a non-vulnerable player is rejected`() {
        val s = TestCards.playing(
            listOf("a" to listOf(num(CardColor.RED, 1), num(CardColor.RED, 2)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.ChallengeUno("b", "a"))
        assertFalse(res.accepted)
    }

    // ---- Invalid / duplicate actions ----------------------------------------

    @Test
    fun `playing out of turn is rejected and state is unchanged`() {
        val blue1 = num(CardColor.BLUE, 1)
        val s = TestCards.playing(
            listOf("a" to emptyList(), "b" to listOf(blue1)),
            top = num(CardColor.BLUE, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("b", blue1.uid))
        assertFalse(res.accepted)
        assertNotNull(res.error)
        assertEquals(s, res.state) // unchanged
    }

    @Test
    fun `playing a card you do not hold is rejected`() {
        val s = TestCards.playing(
            listOf("a" to listOf(num(CardColor.RED, 1)), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", "does_not_exist"))
        assertFalse(res.accepted)
        assertEquals(s, res.state)
    }

    @Test
    fun `playing an illegal card is rejected`() {
        val blue9 = num(CardColor.BLUE, 9)
        val s = TestCards.playing(
            listOf("a" to listOf(blue9), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val res = engine.apply(s, PlayerAction.PlayCard("a", blue9.uid))
        assertFalse(res.accepted)
        assertEquals(s, res.state)
    }

    @Test
    fun `duplicate play of the same card is rejected the second time`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3, num(CardColor.RED, 8)), "b" to listOf(num(CardColor.RED, 1))),
            top = num(CardColor.RED, 5),
        )
        val first = engine.apply(s, PlayerAction.PlayCard("a", red3.uid))
        assertTrue(first.accepted)
        // Replaying the same action against the new state: it's no longer a's turn
        // and the card is gone -> rejected, no double effect.
        val second = engine.apply(first.state, PlayerAction.PlayCard("a", red3.uid))
        assertFalse(second.accepted)
    }

    @Test
    fun `state version increments on accepted actions and holds on rejects`() {
        val red3 = num(CardColor.RED, 3)
        val s = TestCards.playing(
            listOf("a" to listOf(red3), "b" to emptyList()),
            top = num(CardColor.RED, 5),
        )
        val bad = engine.apply(s, PlayerAction.PlayCard("b", red3.uid))
        assertEquals(s.stateVersion, bad.state.stateVersion)

        val good = engine.apply(s, PlayerAction.PlayCard("a", red3.uid))
        assertEquals(s.stateVersion + 1, good.state.stateVersion)
    }

    // ---- Private information -------------------------------------------------

    @Test
    fun `redactedFor hides other players hands but keeps counts`() {
        val s = TestCards.playing(
            listOf(
                "a" to listOf(num(CardColor.RED, 1), num(CardColor.RED, 2)),
                "b" to listOf(num(CardColor.BLUE, 3), num(CardColor.BLUE, 4), num(CardColor.BLUE, 5)),
            ),
            top = num(CardColor.RED, 5),
        )
        val view = s.redactedFor("a")
        val self = view.players.first { it.playerId == "a" }
        val other = view.players.first { it.playerId == "b" }
        assertNotNull(self.hand)
        assertEquals(2, self.hand!!.size)
        assertNull(other.hand)          // never leaked
        assertEquals(3, other.handCount) // count still visible
    }
}
