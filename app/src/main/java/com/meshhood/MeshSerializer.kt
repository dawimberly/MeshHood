package com.meshhood

import com.meshhood.proto.MessageEnvelope
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * JSON ↔ protobuf wire encoding for mesh envelopes.
 *
 * Protobuf frames are prefixed with [MAGIC] so receivers can auto-detect format
 * after decryption. JSON frames are raw UTF-8 (legacy + debug inject).
 */
object MeshSerializer {
    /** "MH" + wire version 1 */
    val MAGIC = byteArrayOf(0x4D, 0x48, 0x01)

    private val BASE_KEYS = setOf("v", "type", "id", "ttl", "ts")

    /** Encode envelope JSON as protobuf bytes (includes [MAGIC]). */
    fun encodeProto(envelopeJson: String): ByteArray {
        val msg = jsonToProto(envelopeJson)
        return MAGIC + msg.toByteArray()
    }

    /** Encode as UTF-8 JSON bytes (no magic). */
    fun encodeJson(envelopeJson: String): ByteArray =
        envelopeJson.toByteArray(StandardCharsets.UTF_8)

    /**
     * Decode wire plaintext (post-decryption) to canonical JSON envelope string.
     * Accepts protobuf ([MAGIC]) or UTF-8 JSON.
     */
    fun decode(wire: ByteArray): String {
        if (isProtobuf(wire)) {
            val msg = MessageEnvelope.parseFrom(wire.copyOfRange(MAGIC.size, wire.size))
            return protoToJson(msg)
        }
        return wire.toString(StandardCharsets.UTF_8)
    }

    fun isProtobuf(wire: ByteArray): Boolean =
        wire.size >= MAGIC.size && wire.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    /** True when decoded JSON includes wireFmt hint from a protobuf-capable peer. */
    fun signalsProtoCapability(envelopeJson: String): Boolean {
        return try {
            val obj = JSONObject(envelopeJson)
            obj.optString("wireFmt", "") == "pb"
        } catch (_: Exception) {
            false
        }
    }

    private fun jsonToProto(json: String): MessageEnvelope {
        val obj = JSONObject(json)
        val builder = MessageEnvelope.newBuilder()
            .setV(obj.optInt("v", 1))
            .setType(obj.optString("type", ""))
            .setId(obj.optString("id", ""))
            .setTtl(obj.optInt("ttl", 0))
            .setTs(obj.optLong("ts", 0L))
        for (key in obj.keys()) {
            if (key in BASE_KEYS) continue
            builder.putExtra(key, jsonValueToString(obj.get(key)))
        }
        return builder.build()
    }

    private fun protoToJson(msg: MessageEnvelope): String {
        val obj = JSONObject()
        if (msg.v != 0) obj.put("v", msg.v)
        if (msg.type.isNotEmpty()) obj.put("type", msg.type)
        if (msg.id.isNotEmpty()) obj.put("id", msg.id)
        if (msg.ttl != 0) obj.put("ttl", msg.ttl)
        if (msg.ts != 0L) obj.put("ts", msg.ts)
        for ((key, value) in msg.extraMap) {
            obj.put(key, stringToJsonValue(value))
        }
        return obj.toString()
    }

    private fun jsonValueToString(value: Any): String = when (value) {
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    private fun stringToJsonValue(raw: String): Any {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return try {
                JSONObject(trimmed)
            } catch (_: Exception) {
                raw
            }
        }
        if (trimmed.startsWith("[")) {
            return try {
                JSONArray(trimmed)
            } catch (_: Exception) {
                raw
            }
        }
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        if (trimmed == "true" || trimmed == "false") return trimmed.toBoolean()
        return raw
    }
}
