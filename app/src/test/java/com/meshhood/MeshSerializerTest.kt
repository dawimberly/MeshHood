package com.meshhood

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MeshSerializerTest {

    @Test
    fun jsonRoundTrip_isIdentity() {
        val json = """{"v":1,"type":"broadcast","id":"abc123","ttl":6,"ts":1730000000000,"from":"Alice","text":"hello"}"""
        val decoded = MeshSerializer.decode(MeshSerializer.encodeJson(json))
        assertEquals(json, decoded)
    }

    @Test
    fun protoRoundTrip_preservesFields() {
        val json = """
            {"v":1,"type":"syncreq","id":"a1b2","ttl":1,"ts":1730000000000,
             "deviceId":"dev1","lastSeenSeq":100,"windowSize":50,"wireFmt":"pb"}
        """.trimIndent().replace("\n", "").replace(" ", "")
        val wire = MeshSerializer.encodeProto(json)
        assertTrue(MeshSerializer.isProtobuf(wire))
        val decoded = MeshSerializer.decode(wire)
        val obj = JSONObject(decoded)
        assertEquals("syncreq", obj.getString("type"))
        assertEquals("dev1", obj.getString("deviceId"))
        assertEquals(100L, obj.getLong("lastSeenSeq"))
        assertEquals("pb", obj.getString("wireFmt"))
    }

    @Test
    fun decode_autoDetectsJson() {
        val json = """{"v":1,"type":"dm","id":"x","ttl":3,"from":"A","to":"B","text":"hi"}"""
        val decoded = MeshSerializer.decode(json.toByteArray(Charsets.UTF_8))
        assertEquals(json, decoded)
        assertFalse(MeshSerializer.isProtobuf(json.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun nestedJsonFields_surviveProtoRoundTrip() {
        val json = """
            {"v":1,"type":"broadcast","id":"n1","ttl":6,"ts":1,
             "channel":"zone:postal:90210",
             "origin":{"postal":"90210"},
             "geo":{"lat":34.1,"lon":-118.2,"postal":"90210","ts":1}}
        """.trimIndent().replace("\n", "").replace(" ", "")
        val decoded = MeshSerializer.decode(MeshSerializer.encodeProto(json))
        val obj = JSONObject(decoded)
        assertEquals("90210", obj.getJSONObject("origin").getString("postal"))
        assertEquals(34.1, obj.getJSONObject("geo").getDouble("lat"), 0.001)
    }

    @Test
    fun signalsProtoCapability_readsWireFmt() {
        assertTrue(MeshSerializer.signalsProtoCapability("""{"wireFmt":"pb","type":"syncreq"}"""))
        assertFalse(MeshSerializer.signalsProtoCapability("""{"type":"broadcast"}"""))
    }

    @Test
    fun encryptDecrypt_protoWire() {
        val json = """{"v":1,"type":"broadcast","id":"p1","ttl":6,"text":"proto path"}"""
        val wire = MeshSerializer.encodeProto(json)
        val encrypted = Crypto.encryptBytes(wire)
        val decrypted = Crypto.decryptBytes(encrypted)
        assertTrue(decrypted != null)
        assertEquals(json, MeshSerializer.decode(decrypted!!))
    }
}
