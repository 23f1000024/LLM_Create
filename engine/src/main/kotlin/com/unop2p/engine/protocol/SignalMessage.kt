package com.unop2p.engine.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Messages for the host's temporary signaling service. Their ONLY job is to let
 * peers discover each other and exchange WebRTC SDP/ICE so a direct connection
 * can be formed. Once WebRTC is up, no game or voice traffic uses these — the
 * signaling channel goes quiet.
 */
@Serializable
sealed interface SignalMessage

/** Optional STUN/TURN server config. Empty for LAN; a slot for future Internet play. */
@Serializable
data class IceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

@Serializable
data class PeerInfo(val playerId: String, val displayName: String, val isHost: Boolean)

// ---- Join handshake (HTTP POST /room/join, or first WS frame) ---------------

@Serializable
@SerialName("join")
data class JoinRequest(val roomCode: String, val displayName: String) : SignalMessage

@Serializable
@SerialName("join_ok")
data class JoinAccepted(
    val playerId: String,
    val token: String,
    val hostPlayerId: String,
    val iceServers: List<IceServer> = emptyList(),
) : SignalMessage

@Serializable
@SerialName("join_err")
data class JoinRejected(val reason: String) : SignalMessage

// ---- WebSocket session ------------------------------------------------------

/** Authenticates a WebSocket connection to the seat obtained during join. */
@Serializable
@SerialName("sig_hello")
data class SignalHello(val playerId: String, val token: String) : SignalMessage

/** Host tells a client the current set of peers it should connect to. */
@Serializable
@SerialName("peers")
data class PeerRoster(val peers: List<PeerInfo>) : SignalMessage

@Serializable
@SerialName("peer_joined")
data class PeerJoined(val peer: PeerInfo) : SignalMessage

@Serializable
@SerialName("peer_left")
data class PeerLeft(val playerId: String) : SignalMessage

// ---- WebRTC negotiation (relayed peer-to-peer through the host) --------------

@Serializable
@SerialName("offer")
data class Offer(val from: String, val to: String, val sdp: String) : SignalMessage

@Serializable
@SerialName("answer")
data class Answer(val from: String, val to: String, val sdp: String) : SignalMessage

@Serializable
@SerialName("candidate")
data class IceCandidateMsg(
    val from: String,
    val to: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
) : SignalMessage

@Serializable
@SerialName("bye")
data class Bye(val playerId: String) : SignalMessage

@Serializable
@SerialName("sig_error")
data class SignalError(val message: String) : SignalMessage
