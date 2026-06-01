package com.meshhood.gateway

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.meshhood.GatewayHeadlessKeys
import com.meshhood.MeshService

/**
 * Gateway "Headless Mode": mesh runs in the background with no visible activity after boot.
 * Users can always reopen Official alerts from the launcher, notification, or SHOW_UI.
 *
 * Enable via Agency Gateway "Run headless on boot" or
 * `adb shell am broadcast -a com.meshhood.gateway.HEADLESS_ON ...`
 *
 * Open UI: app icon, notification tap/action, or
 * `adb shell am broadcast -a com.meshhood.gateway.SHOW_UI ...`
 */
object GatewayMode {
    const val ACTION_SHOW_UI = GatewayHeadlessKeys.ACTION_SHOW_UI
    const val ACTION_HEADLESS_ON = "com.meshhood.gateway.HEADLESS_ON"

    private const val KEY_GATEWAY_MODE = "gatewaymode"

    private val optionalPermissions = setOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )

    fun isHeadless(context: Context): Boolean =
        prefs(context).getBoolean(GatewayHeadlessKeys.KEY_HEADLESS, false)

    fun setHeadless(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(GatewayHeadlessKeys.KEY_HEADLESS, enabled).apply()
    }

    fun activateHeadless(context: Context) {
        prefs(context).edit()
            .putBoolean(GatewayHeadlessKeys.KEY_HEADLESS, true)
            .putBoolean(KEY_GATEWAY_MODE, true)
            .apply()
    }

    fun deactivateHeadless(context: Context) {
        setHeadless(context, false)
    }

    /**
     * When headless is enabled and mesh permissions are granted, starts [MeshService]
     * and ensures gateway mode. Never finishes activities or blocks future UI opens.
     *
     * @return true when the headless mesh hub was started (or is already running).
     */
    fun ensureHeadlessMeshRunning(context: Context): Boolean {
        if (!isHeadless(context)) return false
        ensureGatewayModePref(context)
        if (!criticalMeshPermissionsGranted(context)) return false
        ContextCompat.startForegroundService(
            context,
            Intent(context, MeshService::class.java),
        )
        return true
    }

    /**
     * Only auto-finishes [activity] when it was launched for automatic headless startup
     * (boot receiver with [GatewayHeadlessKeys.EXTRA_AUTO_HEADLESS_START]).
     *
     * @return true when the activity should not continue (UI bypassed).
     */
    fun enterHeadlessIfNeeded(activity: Activity): Boolean {
        if (activity.intent?.getBooleanExtra(GatewayHeadlessKeys.EXTRA_AUTO_HEADLESS_START, false) != true) {
            return false
        }
        if (!ensureHeadlessMeshRunning(activity)) return false
        activity.finish()
        return true
    }

    private fun ensureGatewayModePref(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_GATEWAY_MODE, false)) {
            p.edit().putBoolean(KEY_GATEWAY_MODE, true).apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(GatewayHeadlessKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private fun criticalMeshPermissionsGranted(context: Context): Boolean {
        val required = requiredMeshPermissions()
        val nonLocation = required
            .filter { it !in optionalPermissions }
            .filter {
                it != Manifest.permission.ACCESS_FINE_LOCATION &&
                    it != Manifest.permission.ACCESS_COARSE_LOCATION
            }
        val othersOk = nonLocation.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return othersOk && (fine || coarse)
    }

    private fun requiredMeshPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            perms.add(Manifest.permission.BLUETOOTH)
            perms.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return perms.toTypedArray()
    }
}
