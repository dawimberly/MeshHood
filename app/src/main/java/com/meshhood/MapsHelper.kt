package com.meshhood

import android.content.Context
import android.content.Intent
import android.net.Uri

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

    /** Search Google Maps near [lat],[lon], or near the user when coords are omitted. */
    fun searchNearby(context: Context, query: String, lat: Double? = null, lon: Double? = null) {
        val encoded = Uri.encode(query.trim())
        if (encoded.isEmpty()) return
        val uri = if (lat != null && lon != null) {
            Uri.parse("geo:$lat,$lon?q=$encoded")
        } else {
            Uri.parse("geo:0,0?q=$encoded")
        }
        launchMapsIntent(context, Intent(Intent.ACTION_VIEW, uri))
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

    /** Best-effort lat/lon extraction from free text (e.g. agency alert bodies). */
    fun parseCoordsFromText(text: String): Pair<Double, Double>? {
        val match = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""").find(text) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) return null
        return lat to lon
    }

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
