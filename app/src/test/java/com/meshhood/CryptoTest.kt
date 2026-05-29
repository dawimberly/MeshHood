package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CryptoTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val plain = """{"v":1,"type":"broadcast","text":"hello mesh"}"""
        val wire = Crypto.encrypt(plain)
        assertEquals(plain, Crypto.decrypt(wire))
    }

    @Test
    fun decrypt_rejectsTamperedCiphertext() {
        val wire = Crypto.encrypt("test").copyOf()
        wire[wire.size - 1] = (wire[wire.size - 1].toInt() xor 0xFF).toByte()
        assertNull(Crypto.decrypt(wire))
    }

    @Test
    fun perPairKey_isIndependent() {
        val keyA = ByteArray(32) { 1 }
        val keyB = ByteArray(32) { 2 }
        val msg = "private dm payload"
        val blobA = Crypto.encryptWithKey(keyA, msg)
        assertEquals(msg, Crypto.decryptWithKey(keyA, blobA))
        assertNull(Crypto.decryptWithKey(keyB, blobA))
    }

    @Test
    fun encrypt_producesUniqueNonces() {
        val a = Crypto.encrypt("same")
        val b = Crypto.encrypt("same")
        assertNotEquals(a.contentToString(), b.contentToString())
    }
}
