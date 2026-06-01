package com.meshhood

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Rolling device location → postal, state, city. Requires location permission.
 * [MeshService] merges snapshots into [ZoneContext] for feed + mesh routing.
 */
class GeoLocator(private val context: Context) {

    data class Snapshot(
        val lat: Double,
        val lon: Double,
        val postal: String = "",
        val state: String = "",
        val locality: String = "",
        val updatedAt: Long = System.currentTimeMillis(),
    )

    private var last: Snapshot? = null

    fun current(): Snapshot? = last

    fun currentPostal(): String = last?.postal?.trim().orEmpty()

    /** Refresh from device location; returns latest snapshot (may reuse prior fields). */
    @SuppressLint("MissingPermission")
    fun refresh(): Snapshot? {
        val coords = readCoords() ?: return last
        val place = reverseGeocode(coords.first, coords.second)
        val prior = last
        last = Snapshot(
            lat = coords.first,
            lon = coords.second,
            postal = place?.postal?.ifBlank { prior?.postal.orEmpty() }.orEmpty(),
            state = place?.state?.ifBlank { prior?.state.orEmpty() }.orEmpty(),
            locality = place?.locality?.ifBlank { prior?.locality.orEmpty() }.orEmpty(),
            updatedAt = System.currentTimeMillis(),
        )
        return last
    }

    @SuppressLint("MissingPermission")
    private fun readCoords(): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var best: Location? = null
            for (p in providers) {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best!!.time) best = loc
            }
            val freshCutoff = System.currentTimeMillis() - 10 * 60_000
            if (best != null && best.time >= freshCutoff) {
                return Pair(best.latitude, best.longitude)
            }
            val handler = Handler(Looper.getMainLooper())
            for (p in providers) {
                if (!lm.isProviderEnabled(p)) continue
                val latch = CountDownLatch(1)
                var fresh: Location? = null
                val listener = LocationListener { loc ->
                    fresh = loc
                    latch.countDown()
                }
                try {
                    lm.requestSingleUpdate(p, listener, handler.looper)
                    latch.await(8, TimeUnit.SECONDS)
                } catch (_: Exception) {
                    // Fall back to last known below.
                } finally {
                    try {
                        lm.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }
                fresh?.let { return Pair(it.latitude, it.longitude) }
            }
            best?.let { return Pair(it.latitude, it.longitude) }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lon: Double): GeoPlace? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale.US)
            val places = geocoder.getFromLocation(lat, lon, 1)
            val addr = places?.firstOrNull() ?: return null
            val postal = addr.postalCode?.trim().orEmpty()
            val state = UsStates.abbrFromName(addr.adminArea?.trim().orEmpty())
            val locality = addr.locality?.trim()?.takeIf { it.isNotEmpty() }
                ?: addr.subLocality?.trim()?.takeIf { it.isNotEmpty() }
                ?: addr.subAdminArea?.trim().orEmpty()
            GeoPlace(postal, state, locality)
        } catch (_: Exception) {
            null
        }
    }

    private data class GeoPlace(
        val postal: String,
        val state: String,
        val locality: String,
    )
}
