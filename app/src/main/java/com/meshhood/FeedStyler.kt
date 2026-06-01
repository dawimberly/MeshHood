package com.meshhood

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

object FeedStyler {

    private val MAPS_URL_PATTERN = Regex("""https://www\.google\.com/maps[^\s]+""")

    fun bindCard(
        context: Context,
        cardRoot: View,
        senderText: TextView,
        timeText: TextView,
        bodyText: TextView,
        badgeText: TextView,
        openMapsButton: MaterialButton?,
        line: FeedLine,
        onOpenMapsClick: (() -> Unit)? = null,
        onLinkClick: ((String) -> Unit)? = null,
    ) {
        val parts = line.displayParts()
        senderText.text = parts.sender
        if (parts.time.isBlank()) {
            timeText.visibility = View.GONE
            timeText.text = ""
        } else {
            timeText.visibility = View.VISIBLE
            timeText.text = parts.time
        }

        val timeFg = ContextCompat.getColor(context, R.color.mesh_text_dim)
        timeText.setTextColor(timeFg)

        var accentFg = ContextCompat.getColor(context, R.color.mesh_on_surface_variant)
        when (line.kind) {
            FeedKind.NEIGHBOR -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_neighbor)
                senderText.setTextColor(ContextCompat.getColor(context, R.color.mesh_on_surface))
                bodyText.setTextColor(ContextCompat.getColor(context, R.color.mesh_on_surface_variant))
                senderText.setTypeface(null, Typeface.BOLD)
                bodyText.setTypeface(null, Typeface.NORMAL)
                bodyText.textSize = 14f
                hideBadge(badgeText)
            }
            FeedKind.SELF -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_self)
                val selfFg = ContextCompat.getColor(context, R.color.mesh_teal_light)
                senderText.setTextColor(selfFg)
                bodyText.setTextColor(ContextCompat.getColor(context, R.color.mesh_on_surface_variant))
                senderText.setTypeface(null, Typeface.BOLD)
                bodyText.setTypeface(null, Typeface.NORMAL)
                bodyText.textSize = 14f
                hideBadge(badgeText)
            }
            FeedKind.SYSTEM -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_system)
                val systemFg = ContextCompat.getColor(context, R.color.mesh_text_dim)
                senderText.setTextColor(systemFg)
                bodyText.setTextColor(systemFg)
                senderText.setTypeface(null, Typeface.NORMAL)
                bodyText.setTypeface(null, Typeface.NORMAL)
                bodyText.textSize = 13f
                hideBadge(badgeText)
            }
            FeedKind.ICE -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_ice)
                val iceFg = ContextCompat.getColor(context, R.color.mesh_ice_text)
                senderText.setTextColor(iceFg)
                bodyText.setTextColor(iceFg)
                senderText.setTypeface(null, Typeface.BOLD)
                bodyText.setTypeface(null, Typeface.NORMAL)
                bodyText.textSize = 14f
                hideBadge(badgeText)
            }
            FeedKind.AGENCY -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_agency)
                val agencyFg = ContextCompat.getColor(context, R.color.mesh_primary)
                accentFg = agencyFg
                senderText.setTextColor(agencyFg)
                bodyText.setTextColor(agencyFg)
                senderText.setTypeface(null, Typeface.BOLD)
                bodyText.setTypeface(null, Typeface.BOLD)
                bodyText.textSize = 14f
                showBadge(
                    badgeText,
                    context.getString(R.string.feed_badge_agency),
                    R.drawable.feed_badge_bg_agency,
                    agencyFg,
                )
            }
            FeedKind.EMERGENCY -> {
                cardRoot.setBackgroundResource(R.drawable.feed_card_emergency)
                val emergencyFg = ContextCompat.getColor(context, R.color.mesh_emergency)
                accentFg = emergencyFg
                senderText.setTextColor(emergencyFg)
                bodyText.setTextColor(emergencyFg)
                senderText.setTypeface(null, Typeface.BOLD)
                bodyText.setTypeface(null, Typeface.BOLD)
                bodyText.textSize = 14f
                showBadge(
                    badgeText,
                    context.getString(R.string.feed_badge_emergency),
                    R.drawable.feed_badge_bg_emergency,
                    emergencyFg,
                )
            }
        }

        val showMapsActions = line.hasMapCoords() &&
            (line.kind == FeedKind.EMERGENCY || line.kind == FeedKind.AGENCY)
        if (showMapsActions) {
            applyMapsLinks(bodyText, parts.text, accentFg, onLinkClick)
            openMapsButton?.let { button ->
                button.visibility = View.VISIBLE
                button.setTextColor(accentFg)
                button.setOnClickListener { onOpenMapsClick?.invoke() }
            }
        } else {
            bodyText.text = parts.text
            bodyText.movementMethod = null
            openMapsButton?.visibility = View.GONE
            openMapsButton?.setOnClickListener(null)
        }
    }

    private fun applyMapsLinks(
        textView: TextView,
        text: String,
        linkColor: Int,
        onLinkClick: ((String) -> Unit)?,
    ) {
        val match = MAPS_URL_PATTERN.find(text)
        if (match == null || onLinkClick == null) {
            textView.text = text
            textView.movementMethod = null
            return
        }
        val url = match.value
        val spannable = SpannableString(text)
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onLinkClick(url)
            },
            match.range.first,
            match.range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        textView.text = spannable
        textView.setLinkTextColor(linkColor)
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun hideBadge(badgeText: TextView) {
        badgeText.visibility = View.GONE
    }

    private fun showBadge(
        badgeText: TextView,
        label: String,
        backgroundRes: Int,
        textColor: Int,
    ) {
        badgeText.visibility = View.VISIBLE
        badgeText.text = label
        badgeText.setBackgroundResource(backgroundRes)
        badgeText.setTextColor(textColor)
    }
}
