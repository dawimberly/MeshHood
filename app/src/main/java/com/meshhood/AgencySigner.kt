package com.meshhood

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

/** Signs official agency mesh envelopes. Private key comes from gateway build config only. */
object AgencySigner {

    fun signAlert(agencyId: String, text: String, privateKeyB64: String): JSONObject? {
        val body = text.trim()
        if (agencyId.isEmpty() || body.isEmpty() || privateKeyB64.isBlank()) return null
        val ts = System.currentTimeMillis()
        val payload = AgencyTrust.payload(agencyId, ts, body)
        val sig = sign(payload, privateKeyB64) ?: return null
        return JSONObject().apply {
            put("v", 1)
            put("type", "agency")
            put("id", newId())
            put("ttl", 10)
            put("ts", ts)
            put("agencyId", agencyId)
            put("agencySig", sig)
            put("text", body)
            put("routeClass", "agency-official")
            put("channel", "everyone")
        }
    }


    /** Exposed for JVM unit tests (android org.json.JSONObject is stubbed on unitTest). */
    internal fun signAgencyMessage(agencyId: String, text: String, privateKeyB64: String, ts: Long): String? {
        val body = text.trim()
        if (agencyId.isEmpty() || body.isEmpty() || privateKeyB64.isBlank()) return null
        return sign(AgencyTrust.payload(agencyId, ts, body), privateKeyB64)
    }

    private fun sign(message: String, privateKeyB64: String): String? {
        return try {
            val raw = Base64.getDecoder().decode(privateKeyB64.trim())
            if (raw.size != 32) return null
            val priv = Ed25519PrivateKeyParameters(raw, 0)
            val bytes = message.toByteArray(Charsets.UTF_8)
            val signer = Ed25519Signer()
            signer.init(true, priv)
            signer.update(bytes, 0, bytes.size)
            Base64.getEncoder().encodeToString(signer.generateSignature())
        } catch (_: Throwable) {
            null
        }
    }

    private fun newId(): String {
        val buf = ByteArray(8)
        SecureRandom().nextBytes(buf)
        return buf.joinToString("") { "%02x".format(it) }
    }
}
