package com.meshhood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutualLocationTest {

    @Test
    fun establishMutual_clearsPending() {
        var snap = MutualLocation.Snapshot(
            pendingIn = setOf("Alice"),
            pendingOut = setOf("Bob"),
        )
        snap = MutualLocation.establishMutual(snap, "Alice")
        assertTrue(snap.isMutual("Alice"))
        assertFalse(snap.hasIncomingOffer("Alice"))
        snap = MutualLocation.establishMutual(snap, "Bob")
        assertTrue(snap.isMutual("Bob"))
        assertFalse(snap.hasOutgoingOffer("Bob"))
        assertEquals(setOf("Alice", "Bob"), snap.mutual)
    }

    @Test
    fun removePeer_clearsAllStateForPeer() {
        var snap = MutualLocation.Snapshot(
            mutual = setOf("Pat"),
            pendingIn = setOf("Pat"),
            pendingOut = setOf("Pat"),
        )
        snap = MutualLocation.removePeer(snap, "Pat")
        assertFalse(snap.isMutual("Pat"))
        assertFalse(snap.hasIncomingOffer("Pat"))
        assertFalse(snap.hasOutgoingOffer("Pat"))
    }

    @Test
    fun offerPayloads_areCanonical() {
        assertEquals("locoffer|A|B|1", MutualLocation.locOfferPayload("A", "B", 1))
        assertEquals("locaccept|A|B|2", MutualLocation.locAcceptPayload("A", "B", 2))
        assertEquals("locreject|A|B|3", MutualLocation.locRejectPayload("A", "B", 3))
    }
}
