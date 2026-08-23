package com.unop2p.app.net

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/** Small adapter so SDP create/set can be used with lambdas. */
open class SimpleSdpObserver(
    private val onSuccess: (SessionDescription?) -> Unit = {},
    private val onFailure: (String) -> Unit = {},
) : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) = onSuccess(desc)
    override fun onSetSuccess() = onSuccess(null)
    override fun onCreateFailure(error: String?) = onFailure(error ?: "createFailure")
    override fun onSetFailure(error: String?) = onFailure(error ?: "setFailure")
}
