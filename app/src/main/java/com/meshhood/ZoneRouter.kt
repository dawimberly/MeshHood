package com.meshhood

import org.json.JSONObject

/**
 * Zone-aware relay and feed visibility.
 *
 * Normal broadcasts stay inside the sender's geographic branch (same ZIP / state / nation tree).
 * [RouteClass.EMERGENCY_NATIONAL] bypasses the gate and floods like legacy `everyone`.
 */
object ZoneRouter {

    enum class RouteClass(val wire: String) {
        LOCAL("local"),
        EMERGENCY_NATIONAL("emergency-national"),
        AGENCY_OFFICIAL("agency-official"),
        ;

        companion object {
            fun fromWire(raw: String): RouteClass? =
                entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) }
        }
    }

    fun attachRouting(
        obj: JSONObject,
        routeClass: RouteClass,
        channel: String,
        origin: MeshZone,
        geo: GeoLocator.Snapshot? = null,
    ) {
        obj.put("routeClass", routeClass.wire)
        obj.put("origin", origin.toJson())
        MessageChannel.attach(obj, channel, geo)
    }

    fun routeClassFrom(obj: JSONObject): RouteClass {
        val explicit = obj.optString("routeClass", "").trim()
        RouteClass.fromWire(explicit)?.let { return it }
        // Legacy envelopes without routeClass: everyone = unrestricted relay.
        if (MessageChannel.fromEnvelope(obj) == MessageChannel.EVERYONE) {
            return RouteClass.EMERGENCY_NATIONAL
        }
        return RouteClass.LOCAL
    }

    fun originFrom(obj: JSONObject): MeshZone {
        val embedded = MeshZone.fromJson(obj.optJSONObject("origin"))
        if (embedded.hasAny()) return embedded
        return originFromChannel(MessageChannel.fromEnvelope(obj), MessageChannel.geoFromEnvelope(obj))
    }

    /** Infer a minimal origin tree when older senders only stamped channel + geo. */
    fun originFromChannel(channel: String, geo: GeoLocator.Snapshot?): MeshZone {
        if (!MeshZone.isZoneScope(channel)) return MeshZone()
        val level = MeshZone.levelFromScope(channel) ?: return MeshZone()
        val value = MeshZone.valueFromScope(channel)
        return when (level) {
            ZoneLevel.NATION -> MeshZone(nation = value)
            ZoneLevel.NATIONAL_REGION -> MeshZone(nationalRegion = value)
            ZoneLevel.STATE -> MeshZone(state = value)
            ZoneLevel.REGION -> MeshZone(region = value)
            ZoneLevel.POSTAL -> MeshZone(postal = geo?.postal?.takeIf { it.isNotBlank() } ?: value)
            ZoneLevel.LOCAL -> MeshZone(local = value, postal = geo?.postal.orEmpty())
        }
    }

    /** Whether this node should rebroadcast an envelope one more hop. */
    fun shouldRelay(obj: JSONObject, myZone: MeshZone): Boolean {
        val type = obj.optString("type", "broadcast")
        if (type !in ZONE_GATED_TYPES) return true
        if (routeClassFrom(obj) in NATIONWIDE_CLASSES) return true
        val channel = MessageChannel.fromEnvelope(obj)
        val origin = originFrom(obj)
        return shouldPropagateLocal(origin, channel, myZone)
    }

    /** Whether a stored message belongs in the viewer's current area feed. */
    fun visibleInView(
        entryScope: String,
        viewScope: String,
        home: MeshZone,
        emergency: Boolean,
        routeClass: RouteClass = RouteClass.LOCAL,
    ): Boolean {
        if (emergency || routeClass in NATIONWIDE_CLASSES) return true
        if (isDmOrGroup(viewScope)) return entryScope == viewScope
        if (viewScope == MessageChannel.EVERYONE) {
            return entryScope == MessageChannel.EVERYONE
        }
        if (!MeshZone.isZoneScope(viewScope)) return entryScope == viewScope
        if (entryScope == MessageChannel.EVERYONE) return false
        if (!MeshZone.isZoneScope(entryScope)) return false
        if (!shouldPropagateLocal(home, entryScope, home)) return false
        return scopeVisibleAtView(entryScope, viewScope, home)
    }

    private fun scopeVisibleAtView(entryScope: String, viewScope: String, home: MeshZone): Boolean {
        if (entryScope == viewScope) return true
        if (!MeshZone.isZoneScope(entryScope) || !MeshZone.isZoneScope(viewScope)) return false
        // State-wide post visible in ZIP view (broader → narrower).
        if (MeshZone.isBroaderThan(entryScope, viewScope)) return true
        // ZIP post visible in state view (narrower → broader) if same branch.
        if (MeshZone.isBroaderThan(viewScope, entryScope)) {
            return shouldPropagateLocal(home, entryScope, home)
        }
        return false
    }

    /**
     * Same-branch check with lateral containment at the message's channel level.
     * Allows upward propagation (ZIP → state viewers) but blocks sibling ZIPs/regions.
     */
    fun shouldPropagateLocal(origin: MeshZone, channel: String, myZone: MeshZone): Boolean {
        if (channel == MessageChannel.EVERYONE) return false
        if (!MeshZone.isZoneScope(channel)) return false
        if (!inSameBranch(origin, myZone)) return false

        val msgLevel = MeshZone.levelFromScope(channel) ?: return false
        val msgVal = MeshZone.valueFromScope(channel)
        if (msgVal.isBlank()) return false

        val myFinest = myZone.finestLevel() ?: return inSameBranch(origin, myZone)

        if (myFinest.ordinal <= msgLevel.ordinal) {
            val mineAtLevel = myZone.value(msgLevel).trim()
            return mineAtLevel.isBlank() || mineAtLevel.equals(msgVal, ignoreCase = true)
        }

        return myZone.value(msgLevel).trim().equals(msgVal, ignoreCase = true)
    }

    private fun inSameBranch(a: MeshZone, b: MeshZone): Boolean {
        for (level in ZoneLevel.entries) {
            val av = a.value(level).trim()
            val bv = b.value(level).trim()
            if (av.isEmpty() || bv.isEmpty()) continue
            if (!av.equals(bv, ignoreCase = true)) return false
        }
        return true
    }

    private fun isDmOrGroup(scope: String): Boolean =
        scope.startsWith("dm:") || scope.startsWith("group:")

    private val ZONE_GATED_TYPES = setOf("broadcast", "crew", "crewjoin")

    private val NATIONWIDE_CLASSES = setOf(
        RouteClass.EMERGENCY_NATIONAL,
        RouteClass.AGENCY_OFFICIAL,
    )
}
