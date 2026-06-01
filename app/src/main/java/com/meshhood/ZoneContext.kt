package com.meshhood

/**
 * Joins **saved anchor** (profile extras) with **live GPS** for feed, sorting, and routing.
 *
 * When GPS is available, state / ZIP / city come from the device — manual region
 * labels from an old profile are ignored so they can't linger after you travel.
 */
object ZoneContext {

    /** Anchor + live GPS merged for UI and proximity rank. */
    fun effective(anchor: MeshZone, live: GeoLocator.Snapshot?): MeshZone {
        if (live == null) {
            return anchor.copy(postal = anchor.postal.trim())
        }
        val state = live.state.ifBlank { anchor.state.trim() }
        val postal = live.postal.ifBlank { anchor.postal.trim() }
        val sameState = state.isNotBlank() &&
            state.equals(anchor.state.trim(), ignoreCase = true)
        val local = when {
            live.locality.isNotBlank() -> live.locality.trim()
            sameState -> anchor.local.trim()
            else -> ""
        }
        return anchor.copy(
            nation = anchor.nation.ifBlank { "US" },
            nationalRegion = "",
            state = state,
            region = "",
            postal = postal,
            local = local,
        )
    }

    /** Finest channel to stamp on a public broadcast at send time. */
    fun broadcastChannel(anchor: MeshZone, live: GeoLocator.Snapshot?): String {
        val zone = effective(anchor, live)
        return zone.finestLevel()?.let { zone.scopeId(it) } ?: MeshService.SCOPE_EVERYONE
    }
}
