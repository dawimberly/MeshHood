package com.meshhood

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Local profile photo on disk; verification comes from neighbor photo vouches. */
object ProfilePhoto {
    private const val FILE_NAME = "profile_photo.jpg"
    private const val THUMB_SIZE = 128

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasPhoto(context: Context): Boolean {
        val f = file(context)
        return f.exists() && f.length() > 0L
    }

    /** Copy picker URI into app-private storage. */
    fun saveFromUri(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file(context)).use { output -> input.copyTo(output) }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context) {
        file(context).delete()
    }

    fun contentHash(context: Context): String? {
        if (!hasPhoto(context)) return null
        return try {
            val bytes = file(context).readBytes()
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) {
            null
        }
    }

    /** Small JPEG for mesh broadcast (~5–15 KB). */
    fun meshThumbnailBytes(context: Context): ByteArray? {
        if (!hasPhoto(context)) return null
        val src = BitmapFactory.decodeFile(file(context).absolutePath) ?: return null
        val side = minOf(src.width, src.height).coerceAtLeast(1)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val square = Bitmap.createBitmap(src, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(square, THUMB_SIZE, THUMB_SIZE, true)
        if (square !== src) square.recycle()
        if (scaled !== src) src.recycle()
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            scaled.recycle()
            out.toByteArray()
        }
    }
}
