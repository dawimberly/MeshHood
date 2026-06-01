package com.meshhood

import java.util.concurrent.ConcurrentHashMap

/** Ephemeral neighbor locations — not saved permanently; expires after [TTL_MS]. */
object PeerLocationStore {
    private const val TTL_MS = 45 * 60 * 1000L

    private data class Entry(
        val snapshot: GeoLocator.Snapshot,
        val receivedAt: Long,
    )

    private val peers = ConcurrentHashMap<String, Entry>()

    fun put(peer: String, snapshot: GeoLocator.Snapshot) {
        if (!snapshot.hasCoords()) return
        peers[peer] = Entry(snapshot, System.currentTimeMillis())
    }

    fun remove(peer: String) {
        peers.remove(peer)
    }

    fun get(peer: String): GeoLocator.Snapshot? {
        val entry = peers[peer] ?: return null
        if (System.currentTimeMillis() - entry.receivedAt > TTL_MS) {
            peers.remove(peer)
            return null
        }
        return entry.snapshot
    }

    fun allValid(): Map<String, GeoLocator.Snapshot> {
        purgeExpired()
        return peers.mapValues { it.value.snapshot }
    }

    fun purgeExpired() {
        val now = System.currentTimeMillis()
        peers.entries.removeIf { now - it.value.receivedAt > TTL_MS }
    }

    /** Drop cached pins for peers that fail [keep] (e.g. non-mutual after travel). */
    fun purgeExcept(keep: (String) -> Boolean) {
        peers.keys.filter { !keep(it) }.forEach { remove(it) }
    }
}

fun GeoLocator.Snapshot.hasCoords(): Boolean =
    kotlin.math.abs(lat) > 0.001 || kotlin.math.abs(lon) > 0.001
