package com.meshhood

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneRouterTest {

    private val nm87110 = MeshZone(nation = "US", state = "NM", postal = "87110")
    private val nm90210 = MeshZone(nation = "US", state = "NM", postal = "90210")
    private val txZone = MeshZone(nation = "US", state = "TX", postal = "73301")
    private val nmState = MeshZone(nation = "US", state = "NM")

    @Test
    fun samePostalRelays() {
        val channel = "zone:postal:87110"
        assertTrue(ZoneRouter.shouldPropagateLocal(nm87110, channel, nm87110))
    }

    @Test
    fun siblingPostalBlocked() {
        val channel = "zone:postal:87110"
        assertFalse(ZoneRouter.shouldPropagateLocal(nm87110, channel, nm90210))
    }

    @Test
    fun otherStateBlocked() {
        val channel = "zone:postal:87110"
        assertFalse(ZoneRouter.shouldPropagateLocal(nm87110, channel, txZone))
    }

    @Test
    fun broaderStateRelaysChildPostal() {
        val channel = "zone:postal:87110"
        assertTrue(ZoneRouter.shouldPropagateLocal(nm87110, channel, nmState))
    }
}
