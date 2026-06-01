package com.meshhood.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After reboot, restart the mesh hub when headless mode is enabled — without opening UI.
 */
class GatewayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        GatewayMode.ensureHeadlessMeshRunning(context)
    }
}
