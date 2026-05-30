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
        val googleIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$q")).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
        } else {
            openInMaps(context, lat, lon, label)
        }
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
}
