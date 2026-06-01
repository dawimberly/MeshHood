package com.meshhood

import org.json.JSONObject

/**
 * Comms-layer addressing for mesh messages.
 *
 * [channel] tags the intended geographic audience. [origin] + [routeClass] on the envelope
 * drive zone-aware relay (see [ZoneRouter]). Optional [geo] is a sender snapshot only.
 */
object MessageChannel {
    const val EVERYONE = "everyone"

    /** Primary channel id on the envelope (same shape as feed scope ids). */
    fun fromEnvelope(obj: JSONObject): String {
        val channel = obj.optString("channel", "").trim()
        if (channel.isNotEmpty()) return channel
        return obj.optString("zoneScope", EVERYONE)
    }

    fun attach(obj: JSONObject, channel: String, geo: GeoLocator.Snapshot? = null) {
        obj.put("channel", channel)
        // Legacy readers (Python tools, older builds).
        if (channel != EVERYONE) obj.put("zoneScope", channel)
        attachGeo(obj, geo)
    }

    fun attachGeo(obj: JSONObject, geo: GeoLocator.Snapshot?) {
        if (geo == null || !geo.hasCoords()) return
        obj.put(
            "geo",
            JSONObject().apply {
                put("lat", geo.lat)
                put("lon", geo.lon)
                if (geo.postal.isNotBlank()) put("postal", geo.postal)
                put("ts", geo.updatedAt)
            },
        )
    }

    fun geoFromEnvelope(obj: JSONObject): GeoLocator.Snapshot? {
        val g = obj.optJSONObject("geo") ?: return null
        val lat = g.optDouble("lat", 0.0)
        val lon = g.optDouble("lon", 0.0)
        if (kotlin.math.abs(lat) <= 0.001 && kotlin.math.abs(lon) <= 0.001) return null
        return GeoLocator.Snapshot(
            lat = lat,
            lon = lon,
            postal = g.optString("postal", "").trim(),
            updatedAt = g.optLong("ts", 0L),
        )
    }

    /** Infer a postal channel when only a geo snapshot was attached. */
    fun channelFromGeo(obj: JSONObject): String {
        val explicit = fromEnvelope(obj)
        if (explicit != EVERYONE) return explicit
        val postal = geoFromEnvelope(obj)?.postal ?: return EVERYONE
        return "${MeshZone.SCOPE_PREFIX}${ZoneLevel.POSTAL.key}:$postal"
    }
}
