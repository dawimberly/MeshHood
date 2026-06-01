package com.meshhood

/** Shared preference keys for gateway headless mode (gateway flavor). */
object GatewayHeadlessKeys {
    const val PREFS_NAME = "meshhood_store"
    const val KEY_HEADLESS = "gateway_headless"
    const val ACTION_SHOW_UI = "com.meshhood.gateway.SHOW_UI"
    const val GATEWAY_ACTIVITY = "com.meshhood.gateway.AgencyGatewayActivity"
    /** Intent extra: only activities launched with this may auto-finish for headless boot. */
    const val EXTRA_AUTO_HEADLESS_START = "com.meshhood.gateway.extra.AUTO_HEADLESS_START"
}