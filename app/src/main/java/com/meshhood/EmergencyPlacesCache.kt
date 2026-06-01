package com.meshhood

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Nearby emergency facilities via Google Places API (Nearby Search).
 *
 * Uses the same [BuildConfig.MAPS_API_KEY] as the Maps SDK. In Google Cloud Console enable:
 * **Places API** (legacy Nearby Search) on the project that owns the key.
 * Restrict the key to Android app com.meshhood + your debug/release SHA-1.
 */
object EmergencyPlacesCache {
    private const val PREFS = "emergency_places_cache"
    private const val TTL_MS = 24 * 60 * 60 * 1000L
    private const val RADIUS_M = 8_000
    private const val MAX_PER_TYPE = 8

    private val executor: Executor = Executors.newSingleThreadExecutor()

    enum class Category(val label: String, val placeType: String?, val keyword: String? = null) {
        HOSPITAL("Hospital", "hospital"),
        PHARMACY("Pharmacy", "pharmacy"),
        FIRE_STATION("Fire / EMS", "fire_station"),
        SHELTER("Shelter", null, "emergency shelter"),
    }

    data class Facility(
        val id: String,
        val name: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val category: Category,
    )

    sealed class Result {
        data class Success(val facilities: List<Facility>) : Result()
        data class Cached(val facilities: List<Facility>) : Result()
        data class ApiKeyMissing(val message: String) : Result()
        data class PlacesDisabled(val message: String) : Result()
        data class Error(val message: String) : Result()
    }

    fun fetchNearby(
        context: Context,
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
        onResult: (Result) -> Unit,
    ) {
        val apiKey = BuildConfig.MAPS_API_KEY.trim()
        if (apiKey.isBlank()) {
            onResult(Result.ApiKeyMissing(context.getString(R.string.map_api_key_missing)))
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cacheKey = cacheKeyFor(lat, lon)
        if (!forceRefresh) {
            readCache(prefs, cacheKey)?.let { cached ->
                executor.execute { onResult(Result.Cached(cached)) }
                return
            }
        }

        executor.execute {
            try {
                val merged = linkedMapOf<String, Facility>()
                for (category in Category.entries) {
                    val batch = queryNearby(apiKey, lat, lon, category)
                    for (facility in batch) {
                        merged.putIfAbsent(facility.id, facility)
                    }
                }
                val facilities = merged.values.toList()
                if (facilities.isNotEmpty()) {
                    writeCache(prefs, cacheKey, lat, lon, facilities)
                    onResult(Result.Success(facilities))
                    return@execute
                }
                onResult(Result.Error(context.getString(R.string.map_emergency_places_empty)))
            } catch (e: PlacesApiDisabledException) {
                onResult(Result.PlacesDisabled(context.getString(R.string.map_places_api_disabled)))
            } catch (e: Exception) {
                onResult(Result.Error(e.message ?: context.getString(R.string.map_emergency_places_error)))
            }
        }
    }

    private fun cacheKeyFor(lat: Double, lon: Double): String {
        val roundedLat = (lat * 100.0).roundToInt() / 100.0
        val roundedLon = (lon * 100.0).roundToInt() / 100.0
        return "${roundedLat}_${roundedLon}"
    }

    private fun readCache(prefs: android.content.SharedPreferences, cacheKey: String): List<Facility>? {
        val savedAt = prefs.getLong("$cacheKey.saved_at", 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > TTL_MS) return null
        val raw = prefs.getString("$cacheKey.json", null) ?: return null
        return decodeFacilities(raw)
    }

    private fun writeCache(
        prefs: android.content.SharedPreferences,
        cacheKey: String,
        lat: Double,
        lon: Double,
        facilities: List<Facility>,
    ) {
        prefs.edit()
            .putLong("$cacheKey.saved_at", System.currentTimeMillis())
            .putString("$cacheKey.json", encodeFacilities(facilities))
            .putString("$cacheKey.center", "$lat,$lon")
            .apply()
    }

    private fun encodeFacilities(facilities: List<Facility>): String {
        val arr = JSONArray()
        for (f in facilities) {
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("address", f.address)
                    .put("lat", f.lat)
                    .put("lon", f.lon)
                    .put("category", f.category.name),
            )
        }
        return arr.toString()
    }

    private fun decodeFacilities(raw: String): List<Facility> {
        val arr = JSONArray(raw)
        val out = ArrayList<Facility>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val category = runCatching {
                Category.valueOf(obj.getString("category"))
            }.getOrDefault(Category.HOSPITAL)
            out.add(
                Facility(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    address = obj.optString("address", ""),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    category = category,
                ),
            )
        }
        return out
    }

    private fun queryNearby(
        apiKey: String,
        lat: Double,
        lon: Double,
        category: Category,
    ): List<Facility> {
        val params = buildString {
            append("location=").append(lat).append(',').append(lon)
            append("&radius=").append(RADIUS_M)
            category.placeType?.let { append("&type=").append(it) }
            category.keyword?.let {
                append("&keyword=").append(URLEncoder.encode(it, Charsets.UTF_8.name()))
            }
            append("&key=").append(apiKey)
        }
        val url = URL("https://maps.googleapis.com/maps/api/place/nearbysearch/json?$params")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (body.isBlank()) return emptyList()
            val json = JSONObject(body)
            when (json.optString("status")) {
                "OK", "ZERO_RESULTS" -> { /* continue */ }
                "REQUEST_DENIED" -> {
                    val msg = json.optString("error_message", "")
                    if (msg.contains("Places", ignoreCase = true) ||
                        msg.contains("not enabled", ignoreCase = true)
                    ) {
                        throw PlacesApiDisabledException(msg)
                    }
                    throw IllegalStateException(msg.ifBlank { "REQUEST_DENIED" })
                }
                else -> {
                    val msg = json.optString("error_message", json.optString("status"))
                    throw IllegalStateException(msg)
                }
            }
            val results = json.optJSONArray("results") ?: return emptyList()
            val out = ArrayList<Facility>(results.length().coerceAtMost(MAX_PER_TYPE))
            for (i in 0 until minOf(results.length(), MAX_PER_TYPE)) {
                val place = results.getJSONObject(i)
                val geometry = place.optJSONObject("geometry")?.optJSONObject("location") ?: continue
                val placeLat = geometry.optDouble("lat")
                val placeLon = geometry.optDouble("lng")
                if (!MapsHelper.hasUsableCoords(placeLat, placeLon)) continue
                out.add(
                    Facility(
                        id = place.optString("place_id", "${category.name}_$i"),
                        name = place.optString("name", category.label),
                        address = place.optString("vicinity", ""),
                        lat = placeLat,
                        lon = placeLon,
                        category = category,
                    ),
                )
            }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private class PlacesApiDisabledException(message: String) : Exception(message)
}
