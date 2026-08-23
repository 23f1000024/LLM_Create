package com.unop2p.engine.session

import com.unop2p.engine.game.GameSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HostSessionTest {

    private fun newNet(): TestNet {
        val net = TestNet()
        net.host = HostSession(
            gameId = "g",
            roomCode = "7K4P",
            hostPlayerId = "host",
            hostDisplayName = "Host",
            settings = GameSettings(),
            channel = net.hostChannel,
            random = Random(99),
        )
        return net
    }

    @Test
    fun `players join, connect and appear in the lobby`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        val p3 = net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")

        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()

        assertTrue(p2.welcomed)
        assertTrue(p3.welcomed)
        val lobby = p3.lastLobby
        assertNotNull(lobby)
        assertEquals(3, lobby!!.players.size) // host + p2 + p3
        assertTrue(lobby.players.any { it.playerId == "host" && it.connected })
    }

    @Test
    fun `token validation rejects a wrong token`() {
        val net = newNet()
        net.host.addPlayer("p2", "Sam", "tok2")
        assertTrue(net.host.validateToken("p2", "tok2"))
        assertFalse(net.host.validateToken("p2", "wrong"))
        assertFalse(net.host.validateToken("ghost", "tok2"))
    }

    @Test
    fun `starting the game deals redacted snapshots to each client`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        val p3 = net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")
        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()

        net.host.startGame().getOrThrow()

        val p2State = p2.latestState
        assertNotNull(p2State)
        // p2 sees its own 7 cards…
        val p2Self = p2State!!.players.first { it.playerId == "p2" }
        assertEquals(7, p2Self.handCount)
        assertNotNull(p2Self.hand)
        assertEquals(7, p2Self.hand!!.size)
        // …but never another player's cards, only their counts.
        val p2ViewOfP3 = p2State.players.first { it.playerId == "p3" }
        assertNull(p2ViewOfP3.hand)
        assertEquals(7, p2ViewOfP3.handCount)
        val p2ViewOfHost = p2State.players.first { it.playerId == "host" }
        assertNull(p2ViewOfHost.hand)
    }

    @Test
    fun `an out-of-turn action is rejected via ActionResult`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        val p3 = net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")
        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()
        net.host.startGame().getOrThrow()

        // Host (seat 0) is the first player, so p2 acting now is out of turn.
        val currentId = net.host.state!!.currentPlayer!!.playerId
        val outOfTurn = if (currentId == "p2") "p3" else "p2"
        val listener = net.listeners[outOfTurn]!!
        net.clients[outOfTurn]!!.drawCard()

        val result = listener.actionResults.last()
        assertFalse(result.accepted)
        assertNotNull(result.error)
    }

    @Test
    fun `a legal action from the current player updates every client`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        val p3 = net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")
        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()
        net.host.startGame().getOrThrow()

        val versionBefore = net.host.state!!.stateVersion
        val currentId = net.host.state!!.currentPlayer!!.playerId

        // Drive one draw from whoever's turn it is (host acts locally; a remote
        // player acts through its client) — always a legal move.
        if (currentId == "host") {
            net.host.applyHostAction(com.unop2p.engine.game.PlayerAction.DrawCard("host"))
        } else {
            net.clients[currentId]!!.drawCard()
        }

        val versionAfter = net.host.state!!.stateVersion
        assertTrue(versionAfter > versionBefore)
        // Both remote clients observe the newer authoritative version.
        assertEquals(versionAfter, p2.latestState!!.stateVersion)
        assertEquals(versionAfter, p3.latestState!!.stateVersion)
    }

    @Test
    fun `a disconnect marks the player and is visible to other clients`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")
        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()
        net.host.startGame().getOrThrow()

        net.host.markConnected("p3", false)

        // p2's snapshot shows p3 as disconnected; the seat (and its cards) are kept.
        val p3AsSeenByP2 = p2.latestState!!.players.first { it.playerId == "p3" }
        assertEquals(com.unop2p.engine.game.ConnectionState.DISCONNECTED, p3AsSeenByP2.connectionState)
        assertEquals(7, p3AsSeenByP2.handCount)

        // Reconnect restores the seat as CONNECTED.
        net.host.markConnected("p3", true)
        val p3Back = p2.latestState!!.players.first { it.playerId == "p3" }
        assertEquals(com.unop2p.engine.game.ConnectionState.CONNECTED, p3Back.connectionState)
    }

    @Test
    fun `reconnect via RequestState resends the current snapshot`() {
        val net = newNet()
        val p2 = net.addClient("p2", "tok2")
        net.addClient("p3", "tok3")
        net.host.addPlayer("p2", "Sam", "tok2")
        net.host.addPlayer("p3", "Rahul", "tok3")
        net.clients["p2"]!!.connect()
        net.clients["p3"]!!.connect()
        net.host.startGame().getOrThrow()

        p2.latestState = null // simulate a client that lost its state
        net.clients["p2"]!!.requestState()
        assertNotNull(p2.latestState)
        assertEquals(net.host.state!!.stateVersion, p2.latestState!!.stateVersion)
    }
}
