package com.unop2p.app.common

import android.util.Log

/**
 * Structured, filterable logging with a fixed set of subsystem tags so logcat
 * can be filtered per area (e.g. `adb logcat -s UNO`). Never log microphone
 * data or any player's private hand.
 */
object AppLog {
    private const val TAG = "UNO"

    enum class Area(val label: String) {
        ROOM("ROOM"), SIGNAL("SIGNAL"), WEBRTC("WEBRTC"),
        DATA("DATA"), VOICE("VOICE"), GAME("GAME"), UI("UI");
    }

    private val ring = ArrayDeque<String>()
    private const val RING_MAX = 500

    /** In-memory recent log lines for the on-device debug screen. */
    fun recent(): List<String> = synchronized(ring) { ring.toList() }

    fun i(area: Area, message: String) = log(Log.INFO, area, message)
    fun w(area: Area, message: String) = log(Log.WARN, area, message)
    fun e(area: Area, message: String, t: Throwable? = null) {
        log(Log.ERROR, area, message)
        if (t != null) Log.e(TAG, "[${area.label}] ${t.message}", t)
    }

    private fun log(level: Int, area: Area, message: String) {
        val line = "[${area.label}] $message"
        Log.println(level, TAG, line)
        synchronized(ring) {
            ring.addLast(line)
            while (ring.size > RING_MAX) ring.removeFirst()
        }
    }
}
