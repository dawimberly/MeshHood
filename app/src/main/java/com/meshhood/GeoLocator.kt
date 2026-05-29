package com.meshhood

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import java.util.Locale

/**
 * Rolling location → postal code. Requires location permission (already needed for BLE).
 * Does not persist postal; [MeshService] merges snapshots into [ZoneContext].
 */
class GeoLocator(private val context: Context) {

    data class Snapshot(
        val lat: Double,
        val lon: Double,
        val postal: String,
        val updatedAt: Long,
    )

    private var last: Snapshot? = null

    fun current(): Snapshot? = last

    fun currentPostal(): String = last?.postal?.trim().orEmpty()

    /** Refresh from device location; returns latest snapshot (may reuse prior postal). */
    @SuppressLint("MissingPermission")
    fun refresh(): Snapshot? {
        val coords = readCoords() ?: return last
        val postal = reverseGeocode(coords.first, coords.second) ?: last?.postal.orEmpty()
        last = Snapshot(coords.first, coords.second, postal, System.currentTimeMillis())
        return last
    }

    @SuppressLint("MissingPermission")
    private fun readCoords(): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                val loc = lm.getLastKnownLocation(p) ?: continue
                return Pair(loc.latitude, loc.longitude)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale.US)
            val places = geocoder.getFromLocation(lat, lon, 1)
            places?.firstOrNull()?.postalCode?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
