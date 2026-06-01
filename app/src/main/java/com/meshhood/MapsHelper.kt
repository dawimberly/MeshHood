package com.meshhood

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object MapsHelper {
    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /** Opens Google Maps when installed; otherwise any maps app via geo: intent. */
    fun openInGoogleMaps(context: Context, lat: Double, lon: Double, label: String = "") {
        val q = if (label.isNotEmpty()) {
            "$lat,$lon(${Uri.encode(label)})"
        } else {
            "$lat,$lon"
        }
        launchMapsIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$q")))
    }

    /** Opens turn-by-turn navigation in Google Maps when available. */
    fun navigateTo(context: Context, lat: Double, lon: Double) {
        val googleIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
            return
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (webIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(webIntent)
        } else {
            openInGoogleMaps(context, lat, lon)
        }
    }

    /**
     * Search Google Maps near [lat],[lon]. Requires valid coords — never opens at (0,0) or a
     * default city. Returns false when location is missing (caller should show a toast).
     */
    fun searchNearby(
        context: Context,
        query: String,
        lat: Double?,
        lon: Double?,
        noLocationMessage: Int = R.string.map_turn_on_location,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        if (!hasUsableCoords(lat, lon)) {
            Toast.makeText(context, noLocationMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        val latVal = lat!!
        val lonVal = lon!!
        val anchoredQuery = Uri.encode("$trimmed near $latVal,$lonVal")
        val uri = Uri.parse(
            "https://www.google.com/maps/search/?api=1" +
                "&query=$anchoredQuery" +
                "&center=$latVal,$lonVal",
        )
        launchMapsIntent(context, Intent(Intent.ACTION_VIEW, uri))
        return true
    }

    /** Great-circle distance in meters between two WGS84 points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earthRadius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    /** Google Maps search URL — opens in the Maps app; works offline when tiles are downloaded. */
    fun locationSearchUrl(lat: Double, lon: Double): String =
        "https://www.google.com/maps/search/?api=1&query=$lat,$lon"

    /** Human-readable line appended to SOS / emergency broadcasts with coordinates. */
    fun emergencyLocationLine(lat: Double, lon: Double): String =
        "Open in Maps: ${locationSearchUrl(lat, lon)}"

    /** Best-effort lat/lon extraction from free text (e.g. agency alert bodies, maps URLs). */
    fun parseCoordsFromText(text: String): Pair<Double, Double>? {
        Regex("""[?&]query=(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""").find(text)?.let { match ->
            return coordsFromMatch(match.groupValues[1], match.groupValues[2])
        }
        val match = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""").find(text) ?: return null
        return coordsFromMatch(match.groupValues[1], match.groupValues[2])
    }

    /** Opens a maps link or free-text coords in Google Maps when available. */
    fun openLocationLink(context: Context, link: String) {
        parseCoordsFromText(link)?.let { (lat, lon) ->
            openInGoogleMaps(context, lat, lon)
            return
        }
        val trimmed = link.trim()
        if (trimmed.startsWith("http") || trimmed.startsWith("geo:")) {
            launchMapsIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
        }
    }

    private fun coordsFromMatch(latStr: String, lonStr: String): Pair<Double, Double>? {
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return null
        return lat to lon
    }

    /** City/neighborhood zoom when opening Google Maps for offline download. */
    private const val OFFLINE_MAPS_ZOOM = 14

    /**
     * Opens Google Maps centered on [lat],[lon] so the user can download offline tiles for their
     * area. Requires valid GPS — never opens a zoomed-out default view at (0,0) or without coords.
     * Returns false when location is missing (caller may rely on the toast).
     */
    fun openOfflineMapsGuide(
        context: Context,
        lat: Double?,
        lon: Double?,
        noLocationMessage: Int = R.string.map_turn_on_location,
    ): Boolean {
        if (!hasUsableCoords(lat, lon)) {
            Toast.makeText(context, noLocationMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        val latVal = lat!!
        val lonVal = lon!!
        val uri = Uri.parse(offlineMapsCenterUrl(latVal, lonVal))
        launchMapsIntent(context, Intent(Intent.ACTION_VIEW, uri))
        Toast.makeText(context, R.string.offline_maps_open_toast, Toast.LENGTH_LONG).show()
        return true
    }

    /** Maps URL that centers the viewport on a point at neighborhood/city zoom. */
    internal fun offlineMapsCenterUrl(lat: Double, lon: Double, zoom: Int = OFFLINE_MAPS_ZOOM): String =
        "https://www.google.com/maps/@?api=1&map_action=map" +
            "&center=$lat,$lon&zoom=$zoom"

    /** Opens the user's default maps app (Google Maps, Samsung Maps, etc.). */
    fun openInMaps(context: Context, lat: Double, lon: Double, label: String = "") {
        val q = if (label.isNotEmpty()) {
            "$lat,$lon(${Uri.encode(label)})"
        } else {
            "$lat,$lon"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$q"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal fun hasUsableCoords(lat: Double?, lon: Double?): Boolean {
        if (lat == null || lon == null) return false
        if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return false
        return kotlin.math.abs(lat) > 0.001 || kotlin.math.abs(lon) > 0.001
    }

    private fun launchMapsIntent(context: Context, intent: Intent) {
        val googleIntent = Intent(intent.action, intent.data).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
}
