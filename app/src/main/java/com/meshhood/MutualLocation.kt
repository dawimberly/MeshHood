package com.meshhood

import android.content.SharedPreferences

/**
 * Pairwise mutual location consent stored on-device.
 *
 * Flow: [locoffer] → peer accepts with [locaccept] → both in [mutual]; either sends [lochide] or
 * [locreject] / local revoke clears the pair. Receivers ignore [locshare] unless mutual.
 */
object MutualLocation {

    private const val KEY_MUTUAL = "mutual_loc_peers"
    private const val KEY_PENDING_IN = "mutual_loc_pending_in"
    private const val KEY_PENDING_OUT = "mutual_loc_pending_out"

    fun locOfferPayload(from: String, to: String, ts: Long): String = "locoffer|$from|$to|$ts"

    fun locAcceptPayload(from: String, to: String, ts: Long): String = "locaccept|$from|$to|$ts"

    fun locRejectPayload(from: String, to: String, ts: Long): String = "locreject|$from|$to|$ts"

    data class Snapshot(
        val mutual: Set<String> = emptySet(),
        val pendingIn: Set<String> = emptySet(),
        val pendingOut: Set<String> = emptySet(),
    ) {
        fun isMutual(peer: String): Boolean = peer in mutual

        fun hasIncomingOffer(peer: String): Boolean = peer in pendingIn

        fun hasOutgoingOffer(peer: String): Boolean = peer in pendingOut
    }

    fun load(prefs: SharedPreferences): Snapshot = Snapshot(
        mutual = prefs.getStringSet(KEY_MUTUAL, null)?.toSet() ?: emptySet(),
        pendingIn = prefs.getStringSet(KEY_PENDING_IN, null)?.toSet() ?: emptySet(),
        pendingOut = prefs.getStringSet(KEY_PENDING_OUT, null)?.toSet() ?: emptySet(),
    )

    fun save(prefs: SharedPreferences, snap: Snapshot) {
        prefs.edit()
            .putStringSet(KEY_MUTUAL, snap.mutual.ifEmpty { null })
            .putStringSet(KEY_PENDING_IN, snap.pendingIn.ifEmpty { null })
            .putStringSet(KEY_PENDING_OUT, snap.pendingOut.ifEmpty { null })
            .apply()
    }

    fun recordOutgoingOffer(snap: Snapshot, peer: String): Snapshot =
        snap.copy(pendingOut = snap.pendingOut + peer)

    fun recordIncomingOffer(snap: Snapshot, peer: String): Snapshot =
        snap.copy(pendingIn = snap.pendingIn + peer)

    /** Both sides call when accept is sent or received. */
    fun establishMutual(snap: Snapshot, peer: String): Snapshot = snap.copy(
        mutual = snap.mutual + peer,
        pendingIn = snap.pendingIn - peer,
        pendingOut = snap.pendingOut - peer,
    )

    fun removePeer(snap: Snapshot, peer: String): Snapshot = snap.copy(
        mutual = snap.mutual - peer,
        pendingIn = snap.pendingIn - peer,
        pendingOut = snap.pendingOut - peer,
    )

    fun clearAllPending(snap: Snapshot): Snapshot =
        snap.copy(pendingIn = emptySet(), pendingOut = emptySet())
}
