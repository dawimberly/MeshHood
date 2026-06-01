package com.meshhood.debug

import android.content.Intent
import com.meshhood.BuildConfig
import com.meshhood.MeshService

/** Debug-only adb inject hook — lives in src/debug, absent from release APKs. */
object DebugInject {

    const val ACTION_DEBUG_INJECT_ENVELOPE = "com.meshhood.DEBUG_INJECT_ENVELOPE"
    const val EXTRA_ENVELOPE_JSON = "envelope_json"
    const val EXTRA_ENVELOPE_JSON_B64 = "envelope_json_b64"

    fun injectDebugPayload(service: MeshService, json: String) {
        if (!BuildConfig.DEBUG) return
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return
        service.handleIncoming(trimmed)
    }

    fun envelopeFrom(intent: Intent): String? =
        intent.getStringExtra(EXTRA_ENVELOPE_JSON)?.trim()?.takeIf { it.isNotEmpty() }
            ?: intent.getStringExtra(EXTRA_ENVELOPE_JSON_B64)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { b64 ->
                    try {
                        String(java.util.Base64.getDecoder().decode(b64), Charsets.UTF_8)
                    } catch (_: Exception) {
                        null
                    }
                }
}
