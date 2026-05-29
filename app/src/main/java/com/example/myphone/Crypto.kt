package com.example.myphone

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for MeshHood messages.
 *
 * Uses AES-256-GCM with a 256-bit key derived from a shared "neighborhood
 * passphrase" (SHA-256 of the passphrase). Every message on the wire is:
 *
 *     [ 12-byte random nonce ][ ciphertext + 16-byte auth tag ]
 *
 * Only devices that know the neighborhood key can decrypt. This matches the
 * Python tools, so PC and phone can talk securely over Bluetooth.
 *
 * NOTE (roadmap): a production version should replace the shared passphrase
 * with per-device X25519 key exchange (Noise Protocol). This is a solid v1.
 */
object Crypto {

    // The shared neighborhood key. In a real deployment this would be set per
    // neighborhood (like a Wi-Fi password for your block), not hardcoded.
    private const val NEIGHBORHOOD_PASSPHRASE = "meshhood-demo-key"

    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    private val key: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(NEIGHBORHOOD_PASSPHRASE.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    private val random = SecureRandom()

    // Neighborhood-key encryption (transport layer, all messages).
    fun encrypt(plaintext: String): ByteArray = encryptWithKey(key.encoded, plaintext)

    fun decrypt(data: ByteArray): String? = decryptWithKey(key.encoded, data)

    // Per-pair-key encryption (used for private direct messages with X25519).
    fun encryptWithKey(keyBytes: ByteArray, plaintext: String): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return nonce + ciphertext
    }

    fun decryptWithKey(keyBytes: ByteArray, data: ByteArray): String? {
        return try {
            if (data.size <= NONCE_LENGTH) return null
            val nonce = data.copyOfRange(0, NONCE_LENGTH)
            val ciphertext = data.copyOfRange(NONCE_LENGTH, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
