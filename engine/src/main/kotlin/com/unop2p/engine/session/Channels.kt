package com.unop2p.engine.session

import com.unop2p.engine.protocol.GameMessage

/**
 * The host's outbound view of its WebRTC data channels, one per connected
 * remote player. The concrete implementation lives in the Android app and wraps
 * real WebRTC DataChannels; tests supply an in-memory fake. Keeping this an
 * interface is what lets the entire host-authoritative session be unit-tested
 * without WebRTC.
 */
interface HostDataChannel {
    /** Send to one connected player. No-op if that player has no open channel. */
    fun send(playerId: String, message: GameMessage)

    /** Send to every connected remote player. */
    fun broadcast(message: GameMessage)

    /** Send to every connected remote player except [exceptPlayerId]. */
    fun broadcastExcept(exceptPlayerId: String, message: GameMessage)
}

/** The client's single data channel to the host. */
interface ClientDataChannel {
    fun sendToHost(message: GameMessage)
}
