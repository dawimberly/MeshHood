package com.meshhood

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat

object FeedStyler {

    fun spannable(context: Context, lines: List<FeedLine>, emptyFallback: CharSequence): CharSequence {
        if (lines.isEmpty()) return emptyFallback
        val sb = SpannableStringBuilder()
        val iceBg = ContextCompat.getColor(context, R.color.mesh_ice_card)
        val iceFg = ContextCompat.getColor(context, R.color.mesh_ice_text)
        val systemFg = ContextCompat.getColor(context, R.color.mesh_text_dim)
        val selfFg = ContextCompat.getColor(context, R.color.mesh_teal_light)
        val emergencyFg = ContextCompat.getColor(context, R.color.mesh_emergency)
        val neighborFg = ContextCompat.getColor(context, R.color.mesh_on_surface)
        val timeFg = ContextCompat.getColor(context, R.color.mesh_text_dim)
        val bodyFg = ContextCompat.getColor(context, R.color.mesh_on_surface_variant)
        val dividerFg = ContextCompat.getColor(context, R.color.mesh_stroke_subtle)
        val radius = context.resources.displayMetrics.density * 10f
        val pad = (context.resources.displayMetrics.density * 6).toInt()

        for (line in lines) {
            if (sb.isNotEmpty()) {
                val sepStart = sb.length
                sb.append("\n")
                sb.setSpan(
                    ForegroundColorSpan(dividerFg),
                    sepStart,
                    sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }

            val blockStart = sb.length
            val parts = line.displayParts()
            if (parts.time.isNotBlank()) {
                sb.append(parts.time)
                sb.append('\n')
            }
            val senderStart = sb.length
            sb.append(parts.sender)
            sb.append('\n')
            val senderEnd = sb.length
            sb.append(parts.text)
            val blockEnd = sb.length

            if (parts.time.isNotBlank()) {
                val timeEnd = blockStart + parts.time.length
                sb.setSpan(ForegroundColorSpan(timeFg), blockStart, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.82f), blockStart, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.NORMAL), blockStart, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val senderColor = when (line.kind) {
                FeedKind.EMERGENCY -> emergencyFg
                FeedKind.ICE -> iceFg
                FeedKind.SYSTEM -> systemFg
                FeedKind.SELF -> selfFg
                FeedKind.NEIGHBOR -> neighborFg
            }
            sb.setSpan(ForegroundColorSpan(senderColor), senderStart, senderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), senderStart, senderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.95f), senderStart, senderEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val bodyStart = senderEnd
            val bodyColor = when (line.kind) {
                FeedKind.EMERGENCY -> emergencyFg
                FeedKind.ICE -> iceFg
                FeedKind.SYSTEM -> systemFg
                FeedKind.SELF -> selfFg
                FeedKind.NEIGHBOR -> bodyFg
            }
            sb.setSpan(ForegroundColorSpan(bodyColor), bodyStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            when (line.kind) {
                FeedKind.EMERGENCY -> {
                    sb.setSpan(StyleSpan(Typeface.BOLD), bodyStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                FeedKind.ICE -> {
                    sb.setSpan(RoundedBackgroundSpan(iceBg, radius, pad), blockStart, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> Unit
            }
        }
        return sb
    }

    private class RoundedBackgroundSpan(
        private val color: Int,
        private val radius: Float,
        private val padding: Int,
    ) : LineBackgroundSpan {

        override fun drawBackground(
            canvas: Canvas,
            paint: Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lineNum: Int,
        ) {
            val oldColor = paint.color
            paint.color = color
            val rect = RectF(
                left.toFloat(),
                top.toFloat() + padding / 2f,
                right.toFloat(),
                bottom.toFloat() + padding / 2f,
            )
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.color = oldColor
        }
    }
}

private data class FeedDisplayParts(
    val time: String,
    val sender: String,
    val text: String,
)

private fun FeedLine.displayParts(): FeedDisplayParts {
    val cleanTime = time.trim().removePrefix("[").removeSuffix("]")
    return FeedDisplayParts(
        time = cleanTime,
        sender = sender.trim(),
        text = text.trim(),
    )
}
