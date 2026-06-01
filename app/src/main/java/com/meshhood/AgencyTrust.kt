package com.meshhood

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Pinned agency Ed25519 keys — verify-only in the consumer app.
 * Unsigned or bad signatures are dropped before display and relay.
 */
object AgencyTrust {

    private const val TAG = "AgencyTrust"
    private const val ASSET = "agency_trust.json"

    data class Agency(
        val id: String,
        val label: String,
        val pubkey: String,
        val revoked: Boolean = false,
    )

    private var byId: Map<String, Agency> = emptyMap()

    fun load(context: Context) {
        byId = try {
            context.assets.open(ASSET).bufferedReader().use(::parseRoot)
        } catch (t: Throwable) {
            Log.w(TAG, "no agency trust bundle", t)
            emptyMap()
        }
    }

    fun isLoaded(): Boolean = byId.isNotEmpty()

    internal fun setAgenciesForTest(map: Map<String, Agency>) {
        byId = map
    }

    fun agency(id: String): Agency? = byId[id]?.takeUnless { it.revoked }

    /** Canonical signed bytes — keep stable for gateway tooling. */
    fun payload(agencyId: String, ts: Long, text: String): String =
        "agency|$agencyId|$ts|$text"

    /** Returns the trusted agency on success, null to drop the envelope. */
    fun verify(obj: JSONObject): Agency? {
        if (obj.optString("type", "") != "agency") return null
        return verifyMessage(
            agencyId = obj.optString("agencyId", "").trim(),
            sig = obj.optString("agencySig", "").trim(),
            text = obj.optString("text", "").trim(),
            ts = obj.optLong("ts", 0L),
        )
    }

    fun verifyMessage(agencyId: String, sig: String, text: String, ts: Long): Agency? {
        if (agencyId.isEmpty() || sig.isEmpty() || text.isEmpty() || ts <= 0L) return null
        val record = agency(agencyId) ?: return null
        val ok = SignKeys.verify(payload(agencyId, ts, text), sig, record.pubkey)
        return if (ok) record else null
    }

    private fun parseRoot(reader: BufferedReader): Map<String, Agency> {
        val root = JSONObject(reader.readText())
        val arr = root.optJSONArray("agencies") ?: JSONArray()
        val out = LinkedHashMap<String, Agency>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val label = o.optString("label", id).trim()
            val pubkey = o.optString("pubkey", "").trim()
            if (id.isEmpty() || pubkey.isEmpty()) continue
            out[id] = Agency(id, label, pubkey, o.optBoolean("revoked", false))
        }
        return out
    }
}
