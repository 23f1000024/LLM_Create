package com.unop2p.app.net

import com.unop2p.app.common.AppLog
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.ProtocolJson
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Observable per-peer connection state for the debug screen. */
data class PeerLinkState(
    val playerId: String,
    val ice: PeerConnection.IceConnectionState = PeerConnection.IceConnectionState.NEW,
    val dataOpen: Boolean = false,
    val hasRemoteAudio: Boolean = false,
)

/**
 * A single WebRTC connection to one remote player, carrying the game DataChannel
 * and (optionally) a voice audio track. All game and voice traffic for this peer
 * flows here — never through the signaling server.
 *
 * Glare avoidance: the peer whose playerId sorts lower is the "caller" and
 * creates the offer and the DataChannel; the other side is the "callee".
 */
class PeerLink(
    private val factory: PeerConnectionFactory,
    private val myPlayerId: String,
    val remotePlayerId: String,
    private val iceServers: List<PeerConnection.IceServer>,
    private val localAudioTrack: org.webrtc.AudioTrack?,
    private val onLocalDescription: (SessionDescription) -> Unit,
    private val onLocalIceCandidate: (IceCandidate) -> Unit,
    private val onGameMessage: (from: String, GameMessage) -> Unit,
    private val onState: (PeerLinkState) -> Unit,
) {
    val isCaller: Boolean = myPlayerId < remotePlayerId

    private var state = PeerLinkState(remotePlayerId)
    private var dataChannel: DataChannel? = null
    private val pc: PeerConnection

    init {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Trickle ICE; on LAN, host/local candidates suffice with no STUN/TURN.
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        pc = factory.createPeerConnection(rtcConfig, Observer())
            ?: error("Failed to create PeerConnection")

        // Voice: attach the shared local mic track to every peer (mesh).
        localAudioTrack?.let { pc.addTrack(it, listOf("audio")) }

        if (isCaller) {
            val init = DataChannel.Init().apply { ordered = true }
            attachDataChannel(pc.createDataChannel("game", init))
        }
    }

    /** Kicks off negotiation if this side is the caller. */
    fun startIfCaller() {
        if (isCaller) createOffer()
    }

    private fun createOffer() {
        pc.createOffer(
            SimpleSdpObserver(onSuccess = { sdp -> sdp?.let { setLocalAndSignal(it) } }),
            audioOnlyConstraints(),
        )
    }

    fun onRemoteOffer(sdp: String) {
        pc.setRemoteDescription(
            SimpleSdpObserver(onSuccess = {
                pc.createAnswer(
                    SimpleSdpObserver(onSuccess = { answer -> answer?.let { setLocalAndSignal(it) } }),
                    audioOnlyConstraints(),
                )
            }),
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    fun onRemoteAnswer(sdp: String) {
        pc.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    fun onRemoteIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    private fun setLocalAndSignal(sdp: SessionDescription) {
        pc.setLocalDescription(
            SimpleSdpObserver(onSuccess = { onLocalDescription(sdp) }),
            sdp,
        )
    }

    /** Sends a game message on the DataChannel. Returns false if not open. */
    fun send(message: GameMessage): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val bytes = ProtocolJson.encode(message).toByteArray(StandardCharsets.UTF_8)
        return dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    fun close() {
        runCatching { dataChannel?.close() }
        runCatching { pc.close() }
        runCatching { pc.dispose() }
    }

    private fun attachDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                val open = dc.state() == DataChannel.State.OPEN
                updateState(state.copy(dataOpen = open))
                AppLog.i(AppLog.Area.DATA, "DataChannel $remotePlayerId -> ${dc.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                val msg = runCatching { ProtocolJson.decode(text) }.getOrNull() ?: return
                onGameMessage(remotePlayerId, msg)
            }
        })
    }

    private fun updateState(newState: PeerLinkState) {
        state = newState
        onState(state)
    }

    private fun audioOnlyConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    private inner class Observer : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) = onLocalIceCandidate(candidate)
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            updateState(state.copy(ice = newState))
            AppLog.i(AppLog.Area.WEBRTC, "ICE $remotePlayerId -> $newState")
        }
        override fun onDataChannel(dc: DataChannel) = attachDataChannel(dc)
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
            val track = receiver?.track()
            if (track?.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                updateState(state.copy(hasRemoteAudio = true))
                AppLog.i(AppLog.Area.VOICE, "Remote audio from $remotePlayerId")
            }
        }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: org.webrtc.MediaStream?) {}
        override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
        override fun onRenegotiationNeeded() {}
    }
}
