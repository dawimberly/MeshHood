package com.meshhood

import android.app.Activity
import android.content.Context

/** Gateway-flavor hook from shared activities without a compile-time dependency on gateway sources. */
object GatewayHeadlessEntry {
    fun ensureHeadlessMeshRunning(activity: Activity): Boolean {
        if (!BuildConfig.AGENCY_GATEWAY) return false
        return try {
            val cls = Class.forName("com.meshhood.gateway.GatewayMode")
            val method = cls.getMethod("ensureHeadlessMeshRunning", Context::class.java)
            method.invoke(null, activity) as Boolean
        } catch (_: Exception) {
            false
        }
    }

    fun enterHeadlessIfNeeded(activity: Activity): Boolean {
        if (!BuildConfig.AGENCY_GATEWAY) return false
        return try {
            val cls = Class.forName("com.meshhood.gateway.GatewayMode")
            val method = cls.getMethod("enterHeadlessIfNeeded", Activity::class.java)
            method.invoke(null, activity) as Boolean
        } catch (_: Exception) {
            false
        }
    }
}
