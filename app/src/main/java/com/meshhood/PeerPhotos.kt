package com.meshhood

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/** Cached neighbor profile thumbnails received over the mesh. */
object PeerPhotos {
    private fun dir(context: Context): File =
        File(context.filesDir, "peer_photos").also { it.mkdirs() }

    private fun safeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    fun file(context: Context, peerName: String): File =
        File(dir(context), "${safeName(peerName)}.jpg")

    fun hasPhoto(context: Context, peerName: String): Boolean {
        val f = file(context, peerName)
        return f.exists() && f.length() > 0L
    }

    fun saveBytes(context: Context, peerName: String, bytes: ByteArray): Boolean {
        return try {
            FileOutputStream(file(context, peerName)).use { it.write(bytes) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context, peerName: String) {
        file(context, peerName).delete()
    }
}
