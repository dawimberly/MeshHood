package com.meshhood

/**
 * Joins **saved anchor** (profile: nation, state, …) with **live GPS postal**
 * for feed display, sorting, and outbound channel tagging.
 *
 * Anchor = slow, user-declared. Live postal = rolling, permission-linked.
 * Comms envelopes carry a [MessageChannel] id; they do not read GPS directly.
 */
object ZoneContext {
    /** Anchor + rolling postal merged for UI and proximity rank. */
    fun effective(anchor: MeshZone, livePostal: String?): MeshZone {
        val postal = livePostal?.trim()?.takeIf { it.isNotEmpty() } ?: anchor.postal.trim()
        return anchor.copy(postal = postal)
    }

    /** Finest channel to stamp on a public broadcast at send time. */
    fun broadcastChannel(anchor: MeshZone, livePostal: String?): String {
        val zone = effective(anchor, livePostal)
        return zone.finestLevel()?.let { zone.scopeId(it) } ?: MeshService.SCOPE_EVERYONE
    }
}
