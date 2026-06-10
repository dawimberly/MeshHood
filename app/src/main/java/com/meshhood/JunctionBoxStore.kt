package com.meshhood

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Bundled LoRa junction box / relay sites for Santa Barbara County deployment planning.
 */
object JunctionBoxStore {

    private const val TAG = "JunctionBoxStore"
    private const val ASSET = "junction_boxes.json"

    enum class Tier { URBAN, RIDGE, TRAILHEAD }

    enum class Status { PLANNED, ACTIVE }

    data class JunctionBox(
        val id: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val tier: Tier,
        val status: Status,
        val notes: String = "",
    )

    private var boxes: List<JunctionBox> = emptyList()

    fun load(context: Context) {
        boxes = try {
            context.assets.open(ASSET).bufferedReader().use(::parseRoot)
        } catch (t: Throwable) {
            Log.w(TAG, "no junction box bundle", t)
            emptyList()
        }
    }

    fun all(): List<JunctionBox> = boxes

    fun count(): Int = boxes.size

    fun isLoaded(): Boolean = boxes.isNotEmpty()

    internal fun setBoxesForTest(list: List<JunctionBox>) {
        boxes = list
    }

    internal fun parseRoot(reader: BufferedReader): List<JunctionBox> =
        parseRootText(reader.readText())

    internal fun parseRootText(text: String): List<JunctionBox> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("boxes") ?: JSONArray()
        val out = mutableListOf<JunctionBox>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            val name = o.optString("name", id).trim()
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            val tier = parseTier(o.optString("tier", ""))
            val status = parseStatus(o.optString("status", "planned"))
            val notes = o.optString("notes", "").trim()
            if (id.isEmpty() || !MapsHelper.hasUsableCoords(lat, lon) || tier == null) continue
            out.add(JunctionBox(id, name, lat, lon, tier, status, notes))
        }
        return out
    }

    fun findNearest(userLat: Double, userLon: Double): JunctionBox? {
        if (!MapsHelper.hasUsableCoords(userLat, userLon) || boxes.isEmpty()) return null
        return boxes.minByOrNull { box ->
            MapsHelper.distanceMeters(userLat, userLon, box.lat, box.lon)
        }
    }

    fun distanceMeters(from: JunctionBox, userLat: Double, userLon: Double): Double? {
        if (!MapsHelper.hasUsableCoords(userLat, userLon)) return null
        return MapsHelper.distanceMeters(userLat, userLon, from.lat, from.lon)
    }

    internal fun isInSantaBarbaraCounty(lat: Double, lon: Double): Boolean =
        lat in SB_LAT_MIN..SB_LAT_MAX && lon in SB_LON_MIN..SB_LON_MAX

    internal fun allInSantaBarbaraCounty(list: List<JunctionBox>): Boolean =
        list.isNotEmpty() && list.all { isInSantaBarbaraCounty(it.lat, it.lon) }

    private fun parseTier(raw: String): Tier? = when (raw.trim().lowercase()) {
        "urban" -> Tier.URBAN
        "ridge" -> Tier.RIDGE
        "trailhead" -> Tier.TRAILHEAD
        else -> null
    }

    private fun parseStatus(raw: String): Status = when (raw.trim().lowercase()) {
        "active" -> Status.ACTIVE
        else -> Status.PLANNED
    }

    private const val SB_LAT_MIN = 34.2
    private const val SB_LAT_MAX = 34.7
    private const val SB_LON_MIN = -120.2
    private const val SB_LON_MAX = -119.5
}
