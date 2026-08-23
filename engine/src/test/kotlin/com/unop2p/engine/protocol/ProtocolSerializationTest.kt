package com.unop2p.engine.protocol

import com.unop2p.engine.game.CardColor
import com.unop2p.engine.game.GameEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ProtocolSerializationTest {

    @Test
    fun `game messages round-trip through json with a type tag`() {
        val messages = listOf(
            Hello("p2", "tok"),
            PlayCardRequest("r1", "c3_RED_7", CardColor.BLUE, declareUno = true),
            DrawCardRequest("r2"),
            ChooseColorRequest("r3", CardColor.GREEN),
            CallUnoRequest("r4"),
            ChallengeUnoRequest("r5", "p3"),
            ChallengeDrawFourRequest("r6"),
            RequestState("r7", 41),
            ActionResult("r1", accepted = false, error = "Not your turn"),
            Welcome("p2", "Sam"),
        )
        for (m in messages) {
            val text = ProtocolJson.encode(m)
            assertTrue(text.contains("\"type\""), "missing discriminator in $text")
            assertEquals(m, ProtocolJson.decode(text))
        }
    }

    @Test
    fun `a full redacted state update round-trips`() {
        val engine = GameEngine(Random(3))
        val started = engine.startGame("g", listOf("a" to "A", "b" to "B", "c" to "C"))
        val update = StateUpdate(started.state.redactedFor("a"))
        val text = ProtocolJson.encode(update)
        val back = ProtocolJson.decode(text) as StateUpdate
        assertEquals(update.state.stateVersion, back.state.stateVersion)
        assertEquals(update.state.players.size, back.state.players.size)
        // 'a' hand present, others null — preserved across serialization.
        assertEquals(7, back.state.players.first { it.playerId == "a" }.hand!!.size)
        assertEquals(null, back.state.players.first { it.playerId == "b" }.hand)
    }

    @Test
    fun `signal messages round-trip`() {
        val messages = listOf(
            JoinRequest("7K4P", "Alex"),
            JoinAccepted("p2", "tok", "host", listOf(IceServer(listOf("stun:example:3478")))),
            JoinRejected("Room full"),
            SignalHello("p2", "tok"),
            PeerRoster(listOf(PeerInfo("host", "Host", true), PeerInfo("p2", "Sam", false))),
            Offer("p2", "p3", "v=0..."),
            Answer("p3", "p2", "v=0..."),
            IceCandidateMsg("p2", "p3", "0", 0, "candidate:..."),
            Bye("p2"),
        )
        for (m in messages) {
            assertEquals(m, ProtocolJson.decodeSignal(ProtocolJson.encodeSignal(m)))
        }
    }

    @Test
    fun `malformed json is rejected`() {
        assertThrows(Exception::class.java) { ProtocolJson.decode("{ not valid json ") }
        assertThrows(Exception::class.java) { ProtocolJson.decode("""{"type":"nope"}""") }
    }

    @Test
    fun `oversized messages are rejected`() {
        val huge = "x".repeat(ProtocolJson.MAX_MESSAGE_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) { ProtocolJson.decode(huge) }
    }
}
