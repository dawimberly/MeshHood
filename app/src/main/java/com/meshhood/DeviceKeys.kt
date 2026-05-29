package com.meshhood

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Per-device identity keys for TRULY private direct messages.
 *
 * Each device generates its own X25519 key pair on startup. Devices exchange
 * their *public* keys over the mesh (a "key" handshake message). For a direct
 * message between A and B, we compute a shared secret using Elliptic-Curve
 * Diffie-Hellman (ECDH):
 *
 *     shared = ECDH(myPrivate, peerPublic)   // both sides compute the same value
 *     dmKey  = SHA-256(shared)               // 256-bit AES key, unique to A<->B
 *
 * Only A and B can derive dmKey. Other neighbors — even those who know the
 * shared "neighborhood" transport key — cannot read the DM body.
 *
 * Public keys travel as raw 32-byte values (base64). We convert to/from Java's
 * X.509 SubjectPublicKeyInfo using the fixed 12-byte X25519 DER prefix, which
 * keeps us byte-compatible with Python's `cryptography` library.
 */
object DeviceKeys {

    // Fixed DER prefix for an X25519 SubjectPublicKeyInfo (RFC 8410).
    // Followed by the 32-byte raw public key => 44 bytes total.
    private val X25519_SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
        0x6e, 0x03, 0x21, 0x00
    )

    /** True only on API 33+, where the platform exposes the "XDH" algorithm. */
    val isSupported: Boolean by lazy {
        try {
            KeyPairGenerator.getInstance("XDH")
            true
        } catch (t: Throwable) {
            android.util.Log.e("DeviceKeys", "XDH not supported", t)
            false
        }
    }

    private val keyPair: KeyPair? by lazy {
        if (!isSupported) return@lazy null
        try {
            // Android's Conscrypt XDH generator is X25519-only and rejects an
            // explicit NamedParameterSpec, so we generate without initialize().
            KeyPairGenerator.getInstance("XDH").generateKeyPair()
        } catch (t: Throwable) {
            android.util.Log.e("DeviceKeys", "keyPair generation failed", t)
            null
        }
    }

    /** Our raw 32-byte public key, base64-encoded for sending over the mesh. */
    val myPublicKeyB64: String? by lazy {
        val pub = keyPair?.public ?: return@lazy null
        val encoded = pub.encoded // X.509 SPKI, last 32 bytes are the raw key
        val raw = encoded.copyOfRange(encoded.size - 32, encoded.size)
        Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    // Cache derived AES keys per peer public-key string so we don't redo ECDH.
    private val derivedCache = HashMap<String, ByteArray>()

    /**
     * Derive the per-pair AES key shared with the owner of [peerPublicKeyB64].
     * Returns null if X25519 isn't available or the peer key is invalid.
     */
    fun sharedKeyWith(peerPublicKeyB64: String): ByteArray? {
        derivedCache[peerPublicKeyB64]?.let { return it }
        val priv = keyPair?.private ?: return null
        return try {
            val raw = Base64.decode(peerPublicKeyB64, Base64.NO_WRAP)
            if (raw.size != 32) return null
            val spki = X25519_SPKI_PREFIX + raw
            val peerPub = KeyFactory.getInstance("XDH")
                .generatePublic(X509EncodedKeySpec(spki))
            val ka = KeyAgreement.getInstance("XDH")
            ka.init(priv)
            ka.doPhase(peerPub, true)
            val shared = ka.generateSecret()
            val aesKey = MessageDigest.getInstance("SHA-256").digest(shared)
            derivedCache[peerPublicKeyB64] = aesKey
            aesKey
        } catch (_: Throwable) {
            null
        }
    }
}
