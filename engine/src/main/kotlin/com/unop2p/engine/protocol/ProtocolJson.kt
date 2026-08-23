package com.unop2p.engine.protocol

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for both game and signaling protocols. JSON is used
 * for the first release because it is easy to debug on the wire; the format is
 * isolated here so it can be swapped later without touching call sites.
 */
object ProtocolJson {
    /** Hard cap on a single decoded message, mirrored by the transport layer. */
    const val MAX_MESSAGE_BYTES = 64 * 1024

    val json: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun encode(message: GameMessage): String = json.encodeToString(GameMessage.serializer(), message)

    /** Decodes a [GameMessage], enforcing the size cap. Throws on malformed input. */
    fun decode(text: String): GameMessage {
        require(text.length <= MAX_MESSAGE_BYTES) { "Message exceeds size limit" }
        return json.decodeFromString(GameMessage.serializer(), text)
    }

    fun encodeSignal(message: SignalMessage): String = json.encodeToString(SignalMessage.serializer(), message)

    fun decodeSignal(text: String): SignalMessage {
        require(text.length <= MAX_MESSAGE_BYTES) { "Message exceeds size limit" }
        return json.decodeFromString(SignalMessage.serializer(), text)
    }
}
