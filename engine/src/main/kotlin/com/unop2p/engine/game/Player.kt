package com.unop2p.engine.game

import kotlinx.serialization.Serializable

/** Where a player currently sits in the connection lifecycle (host's view). */
@Serializable
enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

/**
 * Authoritative player record held by the host. [hand] is the full private hand
 * and must never be broadcast to other clients (see GameState.redactedFor).
 */
@Serializable
data class Player(
    val playerId: String,
    val displayName: String,
    val hand: List<Card> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val isReady: Boolean = false,
    /** True once the player has legitimately declared "UNO" for their current hand. */
    val hasDeclaredUno: Boolean = false,
    /**
     * True when the player reached one card without declaring UNO and can still
     * be "caught" by another player until they act again.
     */
    val unoVulnerable: Boolean = false,
) {
    val handCount: Int get() = hand.size
    fun hasCard(uid: String): Boolean = hand.any { it.uid == uid }
    fun card(uid: String): Card? = hand.firstOrNull { it.uid == uid }
}
