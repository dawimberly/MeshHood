package com.meshhood

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView

object AvatarBinder {
    fun bind(
        context: Context,
        service: MeshService?,
        name: String,
        imageView: ImageView,
        initialView: TextView,
        badgeView: ImageView? = null,
    ) {
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        initialView.text = initial
        imageView.clipToOutline = true
        imageView.outlineProvider = ViewOutlineProvider.BACKGROUND

        val photoFile = when {
            service != null && name == service.myName && ProfilePhoto.hasPhoto(context) ->
                ProfilePhoto.file(context)
            service != null && PeerPhotos.hasPhoto(context, name) ->
                PeerPhotos.file(context, name)
            else -> null
        }

        if (photoFile != null) {
            val bmp = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                imageView.visibility = View.VISIBLE
                initialView.visibility = View.GONE
            } else {
                showInitial(imageView, initialView)
            }
        } else {
            showInitial(imageView, initialView)
        }

        badgeView?.visibility =
            if (service?.isPhotoVerified(name) == true) View.VISIBLE else View.GONE
    }

    private fun showInitial(imageView: ImageView, initialView: TextView) {
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        initialView.visibility = View.VISIBLE
    }
}
