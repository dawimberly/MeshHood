package com.meshhood

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** State Sync v1 — JSON handshake + sliding-window catch-up (Tier 2). */
object StateSync {
    const val TAG = "StateSync"
    const val TYPE_REQ = "syncreq"
    const val TYPE_RESP = "syncresp"
    const val TTL_SYNC = 1

    private val rng = SecureRandom()

    fun newId(): String {
        val b = ByteArray(8)
        rng.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /**
     * @param lastSeenSeq v1 wire field: millis watermark (max envelope ts the requester already has).
     */
    fun buildRequest(deviceId: String, lastSeenSeq: Long, windowSize: Int): String =
        base(TYPE_REQ).apply {
            put("deviceId", deviceId)
            put("lastSeenSeq", lastSeenSeq)
            put("windowSize", windowSize.coerceIn(1, MeshMessageStore.MAX_STORED))
        }.toString()

    fun buildResponse(deviceId: String, envelopes: List<String>): String =
        base(TYPE_RESP).apply {
            put("deviceId", deviceId)
            put("messages", JSONArray(envelopes))
        }.toString()

    private fun base(type: String): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("type", type)
            put("id", newId())
            put("ttl", TTL_SYNC)
            put("ts", System.currentTimeMillis())
            put("wireFmt", "pb")
        }

    fun parseRequest(obj: JSONObject): Triple<String, Long, Int>? {
        if (obj.optString("type") != TYPE_REQ) return null
        val deviceId = obj.optString("deviceId", "")
        if (deviceId.isEmpty()) return null
        val watermarkTs = obj.optLong("lastSeenSeq", 0L)
        val window = obj.optInt("windowSize", MeshMessageStore.DEFAULT_WINDOW)
        return Triple(deviceId, watermarkTs, window)
    }

    fun parseResponse(obj: JSONObject): Pair<String, List<String>>? {
        if (obj.optString("type") != TYPE_RESP) return null
        val deviceId = obj.optString("deviceId", "")
        val arr = obj.optJSONArray("messages") ?: return deviceId to emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return deviceId to out
    }

    fun shouldStoreType(type: String): Boolean = type in MeshMessageStore.SYNC_TYPES
}
