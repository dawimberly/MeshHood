package com.meshhood

import android.content.Context
import android.content.Intent
import android.net.Uri

object MapsHelper {
    /** Opens the user's default maps app (Google Maps, Apple Maps via browser, etc.). */
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
