package com.unop2p.app.net

import android.content.Context
import com.unop2p.app.common.AppLog
import com.unop2p.engine.protocol.Answer
import com.unop2p.engine.protocol.GameMessage
import com.unop2p.engine.protocol.IceCandidateMsg
import com.unop2p.engine.protocol.IceServer
import com.unop2p.engine.protocol.Offer
import com.unop2p.engine.protocol.SignalMessage
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the WebRTC [PeerConnectionFactory], the single shared microphone track,
 * and one [PeerLink] per remote player. It is the seam between the app's
 * signaling and the transport-agnostic session layer: it turns DataChannel
 * traffic into [GameMessage]s and exposes send/broadcast helpers.
 *
 * The same manager is used by both host and client — only the wiring differs.
 */
class WebRtcManager(
    context: Context,
    private val myPlayerId: String,
    iceServers: List<IceServer>,
    /** Sends a signaling message out (host: via server relay; client: via WS). */
    private val signalSender: (SignalMessage) -> Unit,
    private val onGameMessage: (from: String, GameMessage) -> Unit,
    private val onPeerState: (PeerLinkState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .createAudioDeviceModule()

    private val rtcIceServers: List<PeerConnection.IceServer> = iceServers.map { s ->
        PeerConnection.IceServer.builder(s.urls)
            .setUsername(s.username ?: "")
            .setPassword(s.credential ?: "")
            .createIceServer()
    }

    private val audioSource: AudioSource
    private val localAudioTrack: AudioTrack
    private val peers = ConcurrentHashMap<String, PeerLink>()

    init {
        ensureInitialized(appContext)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("mic", audioSource).apply {
            setEnabled(false) // start muted; the game un-mutes on user request
        }
        AppLog.i(AppLog.Area.WEBRTC, "WebRTC factory ready for $myPlayerId")
    }

    // ---- Peer lifecycle ------------------------------------------------------

    /** Creates a link to [remotePlayerId] and begins negotiation if we are the caller. */
    fun connectPeer(remotePlayerId: String) {
        if (remotePlayerId == myPlayerId || peers.containsKey(remotePlayerId)) return
        val link = createLink(remotePlayerId)
        link.startIfCaller()
    }

    fun removePeer(remotePlayerId: String) {
        peers.remove(remotePlayerId)?.close()
    }

    private fun createLink(remotePlayerId: String): PeerLink {
        val link = PeerLink(
            factory = factory,
            myPlayerId = myPlayerId,
            remotePlayerId = remotePlayerId,
            iceServers = rtcIceServers,
            localAudioTrack = localAudioTrack,
            onLocalDescription = { sdp ->
                val msg = when (sdp.type) {
                    SessionDescription.Type.OFFER -> Offer(myPlayerId, remotePlayerId, sdp.description)
                    SessionDescription.Type.ANSWER -> Answer(myPlayerId, remotePlayerId, sdp.description)
                    else -> return@PeerLink
                }
                signalSender(msg)
            },
            onLocalIceCandidate = { c ->
                signalSender(IceCandidateMsg(myPlayerId, remotePlayerId, c.sdpMid, c.sdpMLineIndex, c.sdp))
            },
            onGameMessage = onGameMessage,
            onState = onPeerState,
        )
        peers[remotePlayerId] = link
        return link
    }

    // ---- Inbound signaling ---------------------------------------------------

    fun onSignal(message: SignalMessage) {
        when (message) {
            is Offer -> {
                if (message.to != myPlayerId) return
                val link = peers.getOrPut(message.from) { createLink(message.from) }
                link.onRemoteOffer(message.sdp)
            }
            is Answer -> if (message.to == myPlayerId) peers[message.from]?.onRemoteAnswer(message.sdp)
            is IceCandidateMsg -> if (message.to == myPlayerId) {
                peers[message.from]?.onRemoteIceCandidate(message.sdpMid, message.sdpMLineIndex, message.candidate)
            }
            else -> {}
        }
    }

    // ---- Data helpers --------------------------------------------------------

    fun sendTo(remotePlayerId: String, message: GameMessage): Boolean =
        peers[remotePlayerId]?.send(message) ?: false

    fun broadcast(message: GameMessage) = peers.values.forEach { it.send(message) }

    fun broadcastExcept(exceptPlayerId: String, message: GameMessage) =
        peers.values.filter { it.remotePlayerId != exceptPlayerId }.forEach { it.send(message) }

    val connectedPeerIds: Set<String> get() = peers.keys.toSet()

    // ---- Voice ---------------------------------------------------------------

    /** Un-mutes/mutes the local microphone (transmission only; see close() for release). */
    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack.setEnabled(enabled)
        AppLog.i(AppLog.Area.VOICE, "Mic ${if (enabled) "unmuted" else "muted"}")
    }

    val isMicEnabled: Boolean get() = localAudioTrack.enabled()

    // ---- Teardown ------------------------------------------------------------

    /** Closes all peers and fully releases the microphone and WebRTC resources. */
    fun close() {
        peers.values.forEach { it.close() }
        peers.clear()
        runCatching { localAudioTrack.setEnabled(false) }
        runCatching { localAudioTrack.dispose() }
        runCatching { audioSource.dispose() }
        runCatching { factory.dispose() }
        runCatching { audioDeviceModule.release() }
        AppLog.i(AppLog.Area.WEBRTC, "WebRTC closed; microphone released")
    }

    companion object {
        @Volatile private var initialized = false
        private fun ensureInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions(),
                )
                initialized = true
            }
        }
    }
}
