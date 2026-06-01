package com.meshhood

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable store of mesh envelopes for State Sync catch-up.
 * Uses the same SharedPreferences bucket as [MeshService] state.
 */
class MeshMessageStore(private val prefs: SharedPreferences) {

    data class StoredMessage(
        val seq: Long,
        val id: String,
        val ts: Long,
        val envelope: String,
    )

    companion object {
        private const val KEY_SEQ = "msg_seq"
        private const val KEY_MESSAGES = "msg_store"
        const val MAX_STORED = 200
        const val DEFAULT_WINDOW = 50

        /** Message types replayed during catch-up (feed-relevant traffic). */
        val SYNC_TYPES = setOf(
            "broadcast",
            "dm",
            "groupmsg",
            "crew",
            "crewjoin",
            "agency",
        )
    }

    private val byId = LinkedHashMap<String, StoredMessage>()
    private var nextSeq: Long = 1

    init {
        load()
    }

    @Synchronized
    fun highestSeq(): Long {
        if (byId.isEmpty()) return 0L
        return byId.values.maxOf { it.seq }
    }

    /**
     * Persist an envelope if it is a sync-eligible type and id is new.
     * @return assigned sequence, or null if skipped / duplicate id.
     */
    @Synchronized
    fun append(envelopeJson: String): Long? {
        val obj = try {
            JSONObject(envelopeJson)
        } catch (_: Exception) {
            return null
        }
        val type = obj.optString("type", "")
        if (type !in SYNC_TYPES) return null
        val id = obj.optString("id", "")
        if (id.isEmpty() || byId.containsKey(id)) return null
        val ts = obj.optLong("ts", System.currentTimeMillis())
        val seq = nextSeq++
        byId[id] = StoredMessage(seq, id, ts, envelopeJson)
        trim()
        persist()
        return seq
    }

    /**
     * Envelopes with [ts] strictly after [watermarkTs] (cross-device safe),
     * oldest-first, capped by [windowSize].
     */
    @Synchronized
    fun envelopesAfter(watermarkTs: Long, windowSize: Int): List<String> {
        val limit = windowSize.coerceIn(1, MAX_STORED)
        return byId.values
            .filter { it.ts > watermarkTs }
            .sortedBy { it.ts }
            .take(limit)
            .map { it.envelope }
    }

    @Synchronized
    fun highestTimestamp(): Long =
        byId.values.maxOfOrNull { it.ts } ?: 0L

    @Synchronized
    fun containsId(id: String): Boolean = id.isNotEmpty() && byId.containsKey(id)

    private fun trim() {
        while (byId.size > MAX_STORED) {
            val oldest = byId.entries.minByOrNull { it.value.seq } ?: break
            byId.remove(oldest.key)
        }
    }

    private fun load() {
        nextSeq = prefs.getLong(KEY_SEQ, 1L).coerceAtLeast(1L)
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                val env = o.optString("env", "")
                if (id.isEmpty() || env.isEmpty()) continue
                val seq = o.optLong("seq", 0L)
                val ts = o.optLong("ts", 0L)
                byId[id] = StoredMessage(seq, id, ts, env)
                if (seq >= nextSeq) nextSeq = seq + 1
            }
        } catch (_: Exception) {
            byId.clear()
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (m in byId.values.sortedBy { it.seq }) {
            arr.put(
                JSONObject().apply {
                    put("seq", m.seq)
                    put("id", m.id)
                    put("ts", m.ts)
                    put("env", m.envelope)
                },
            )
        }
        prefs.edit()
            .putLong(KEY_SEQ, nextSeq)
            .putString(KEY_MESSAGES, arr.toString())
            .apply()
    }
}
