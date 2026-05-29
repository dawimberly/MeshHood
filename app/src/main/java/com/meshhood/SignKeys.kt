package com.meshhood

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * Ed25519 signing identity — the trust anchor for the reputation system.
 *
 * X25519 (DeviceKeys) is for *encryption*; it can't prove who wrote something.
 * Reputation needs unforgeable authorship: when you thank a neighbor ("kudos"),
 * you SIGN it with your Ed25519 key. Anyone who has your public verify key can
 * confirm it really came from you — so nobody can fake endorsements to farm a
 * reputation. This same signing layer will later back profession-badge
 * endorsements ("3 neighbors vouch this person is a nurse").
 *
 * We use BouncyCastle's low-level Ed25519 primitives rather than JCA: Android's
 * Conscrypt exposes an Ed25519 Signature/KeyPairGenerator but no KeyFactory for
 * importing raw 32-byte peer keys. The low-level API sidesteps that entirely and
 * stays byte-compatible with Python's `cryptography` library (raw 32-byte keys,
 * raw 64-byte signatures).
 */
object SignKeys {

    private const val TAG = "SignKeys"

    val isSupported: Boolean = true

    private val priv: Ed25519PrivateKeyParameters by lazy {
        Ed25519PrivateKeyParameters(SecureRandom())
    }

    /** Our raw 32-byte public verify key, base64-encoded. */
    val myVerifyKeyB64: String? by lazy {
        try {
            Base64.encodeToString(priv.generatePublicKey().encoded, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "verify key export failed", t); null
        }
    }

    /** Sign a message, returning a base64 signature (or null if unavailable). */
    fun sign(message: String): String? {
        return try {
            val bytes = message.toByteArray(Charsets.UTF_8)
            val signer = Ed25519Signer()
            signer.init(true, priv)
            signer.update(bytes, 0, bytes.size)
            Base64.encodeToString(signer.generateSignature(), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "sign failed", t); null
        }
    }

    /** Verify a base64 signature over [message] using a peer's raw verify key. */
    fun verify(message: String, signatureB64: String, peerVerifyKeyB64: String): Boolean {
        return try {
            val raw = Base64.decode(peerVerifyKeyB64, Base64.NO_WRAP)
            if (raw.size != 32) return false
            val pub = Ed25519PublicKeyParameters(raw, 0)
            val bytes = message.toByteArray(Charsets.UTF_8)
            val signer = Ed25519Signer()
            signer.init(false, pub)
            signer.update(bytes, 0, bytes.size)
            signer.verifySignature(Base64.decode(signatureB64, Base64.NO_WRAP))
        } catch (t: Throwable) {
            Log.e(TAG, "verify failed", t); false
        }
    }

    /** Canonical string that gets signed for a kudos/endorsement. */
    fun kudosPayload(giver: String, helper: String, ts: Long): String = "kudos|$giver|$helper|$ts"
}
