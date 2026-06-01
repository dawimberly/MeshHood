package com.meshhood.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.meshhood.MeshService

/**
 * ADB headless control without opening the launcher UI.
 *
 * Enable:
 * `adb shell am broadcast -a com.meshhood.gateway.HEADLESS_ON -n com.meshhood.gateway/.gateway.GatewayUiReceiver`
 *
 * Restore UI:
 * `adb shell am broadcast -a com.meshhood.gateway.SHOW_UI -n com.meshhood.gateway/.gateway.GatewayUiReceiver`
 */
class GatewayUiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            GatewayMode.ACTION_HEADLESS_ON -> {
                GatewayMode.activateHeadless(context)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MeshService::class.java),
                )
            }
            GatewayMode.ACTION_SHOW_UI -> {
                val open = Intent(context, AgencyGatewayActivity::class.java).apply {
                    action = GatewayMode.ACTION_SHOW_UI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(open)
            }
        }
    }
}