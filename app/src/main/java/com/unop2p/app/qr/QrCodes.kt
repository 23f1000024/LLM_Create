package com.unop2p.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Validated join target parsed from a QR payload. Contains only plain data. */
data class JoinTarget(val host: String, val port: Int, val room: String)

/**
 * QR generation and *strict* parsing for the `uno://join?...` join link. Parsing
 * never executes anything from the code — it only extracts and validates three
 * plain fields, rejecting anything malformed.
 */
object QrCodes {

    private val ROOM_REGEX = Regex("^[A-Za-z0-9]{3,8}$")
    private val HOST_REGEX = Regex("^[A-Za-z0-9.:\\[\\]_-]{1,64}$") // ipv4/ipv6/hostname chars only

    fun generate(text: String, sizePx: Int = 640): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    /** Parses and validates a scanned payload; returns null if it is not a valid join link. */
    fun parseJoin(payload: String?): JoinTarget? {
        if (payload == null) return null
        val uri = runCatching { Uri.parse(payload.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("uno", ignoreCase = true)) return null
        if (!uri.host.equals("join", ignoreCase = true)) return null

        val host = uri.getQueryParameter("host")?.takeIf { HOST_REGEX.matches(it) } ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val room = uri.getQueryParameter("room")?.takeIf { ROOM_REGEX.matches(it) } ?: return null
        return JoinTarget(host, port, room)
    }

    /** Parses a manually-typed "host:port" (with optional room) into a target. */
    fun parseManual(input: String, room: String): JoinTarget? {
        val trimmed = input.trim()
        if (!ROOM_REGEX.matches(room)) return null
        // IPv6 in brackets: [::1]:8080
        val (host, portStr) = if (trimmed.startsWith("[")) {
            val close = trimmed.indexOf(']')
            if (close < 0) return null
            trimmed.substring(1, close) to trimmed.substringAfter("]:", "")
        } else {
            val idx = trimmed.lastIndexOf(':')
            if (idx < 0) return null
            trimmed.substring(0, idx) to trimmed.substring(idx + 1)
        }
        val port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (!HOST_REGEX.matches(host)) return null
        return JoinTarget(host, port, room)
    }
}
