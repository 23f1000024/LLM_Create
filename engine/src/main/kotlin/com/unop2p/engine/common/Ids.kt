package com.unop2p.engine.common

import kotlin.random.Random

/**
 * Small, dependency-free ID and code helpers shared across the engine and the
 * networking layer. Kept in the engine module so both host and client agree on
 * the exact format of player IDs, request IDs and room codes.
 */
object Ids {
    private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1
    private const val HEX = "0123456789abcdef"

    /** Human-friendly room code, e.g. "7K4P". Avoids visually ambiguous chars. */
    fun roomCode(length: Int = 4, random: Random = Random.Default): String =
        buildString { repeat(length) { append(ROOM_CODE_ALPHABET[random.nextInt(ROOM_CODE_ALPHABET.length)]) } }

    /** Opaque request ID used to correlate client actions with host results. */
    fun requestId(random: Random = Random.Default): String = randomHex(10, random)

    /** Opaque player ID assigned by the host on join. */
    fun playerId(random: Random = Random.Default): String = "p_" + randomHex(12, random)

    /** Bearer token issued to a player so it can reconnect to the same seat. */
    fun sessionToken(random: Random = Random.Default): String = randomHex(24, random)

    private fun randomHex(len: Int, random: Random): String =
        buildString { repeat(len) { append(HEX[random.nextInt(HEX.length)]) } }
}
