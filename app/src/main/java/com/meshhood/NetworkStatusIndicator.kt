package com.meshhood

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Binds readiness dot, label, and help affordance on the transport strip.
 */
object NetworkStatusIndicator {

    private var pulseAnimator: ObjectAnimator? = null

    fun update(
        activity: AppCompatActivity,
        dot: View,
        label: TextView,
        helpButton: ImageButton,
        service: MeshService,
    ) {
        val ts = service.transportState()
        val readiness = NetworkReadiness.compute(
            ts,
            service.lanStatusLine(),
            service.isGatewayMode(),
        )
        val color = ContextCompat.getColor(activity, readiness.colorRes())
        val dotDrawable = ContextCompat.getDrawable(activity, R.drawable.network_readiness_dot)?.mutate()
        if (dotDrawable != null) {
            DrawableCompat.setTint(dotDrawable, color)
            dot.background = dotDrawable
        } else {
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
        label.text = activity.getString(readiness.labelRes())
        label.setTextColor(color)
        label.visibility = View.VISIBLE
        dot.visibility = View.VISIBLE
        dot.alpha = 1f
        dot.contentDescription = label.text

        if (readiness == NetworkReadiness.Searching) {
            startPulse(dot)
        } else {
            stopPulse(dot)
            dot.alpha = 1f
        }

        val showHelp = readiness != NetworkReadiness.Ready
        helpButton.visibility = if (showHelp) View.VISIBLE else View.GONE
        helpButton.setOnClickListener {
            if (showHelp) showHelpDialog(activity, service.isGatewayMode())
        }
    }

    fun showHelpDialog(activity: AppCompatActivity, gatewayMode: Boolean) {
        val message = buildString {
            append(activity.getString(R.string.network_readiness_help_body))
            if (gatewayMode || BuildConfig.AGENCY_GATEWAY) {
                append("\n\n")
                append(activity.getString(R.string.network_readiness_help_gateway))
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.network_readiness_help_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun startPulse(dot: View) {
        if (pulseAnimator?.target == dot) return
        stopPulse(dot)
        pulseAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulse(dot: View) {
        pulseAnimator?.let {
            if (it.target == dot) {
                it.cancel()
                pulseAnimator = null
            }
        }
    }

    fun release(dot: View) {
        stopPulse(dot)
    }
}
