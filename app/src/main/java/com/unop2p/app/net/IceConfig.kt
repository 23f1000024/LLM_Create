package com.unop2p.app.net

import com.unop2p.engine.protocol.IceServer

/**
 * ICE server configuration for WebRTC.
 *
 * LAN play needs NO ICE servers (host/local candidates connect directly). For
 * Internet play, peers need at least STUN (to discover their public
 * "server-reflexive" address) and, behind hostile/symmetric NAT, a TURN relay.
 *
 * STUN is enabled by default because it is harmless on a LAN (local candidates
 * are still tried first) and is the minimum needed to have any chance of a
 * direct Internet connection. TURN is optional and user-supplied — the app
 * ships no TURN server of its own (that would be dedicated infrastructure).
 * Bring your own TURN in Settings for guaranteed Internet connectivity.
 */
object IceConfig {

    /** Public STUN utilities (no accounts, no relaying of media — address discovery only). */
    val DEFAULT_STUN = IceServer(
        urls = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
        ),
    )

    /**
     * Builds the ICE server list from user preferences.
     * @param stunEnabled include the default STUN servers
     * @param turnUrl e.g. "turn:turn.example.com:3478" (or "turns:" for TLS); blank = none
     */
    fun build(
        stunEnabled: Boolean,
        turnUrl: String? = null,
        turnUsername: String? = null,
        turnCredential: String? = null,
    ): List<IceServer> {
        val servers = mutableListOf<IceServer>()
        if (stunEnabled) servers += DEFAULT_STUN
        val url = turnUrl?.trim().orEmpty()
        if (url.startsWith("turn:") || url.startsWith("turns:")) {
            servers += IceServer(
                urls = listOf(url),
                username = turnUsername?.takeIf { it.isNotBlank() },
                credential = turnCredential?.takeIf { it.isNotBlank() },
            )
        }
        return servers
    }
}
