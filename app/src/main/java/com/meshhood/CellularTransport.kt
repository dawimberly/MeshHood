package com.meshhood

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Fourth transport channel: cellular link monitoring + emergency SMS fallback.
 *
 * Gateway uplink relay transport lives in [CellularUplink] (HTTP over mobile data).
 * This class (1) reports cell data readiness in the status strip and (2) sends a plain
 * SMS to your ICE contact when mesh radios cannot reach anyone.
 */
class CellularTransport(
    private val context: Context,
    private val onLinkChanged: () -> Unit,
) {
    companion object {
        private const val TAG = "CellularTransport"
    }

    @Volatile
    private var dataReady = false

    @Volatile
    private var metered = true

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                dataReady = true
                publishStatus()
            }

            override fun onLost(network: Network) {
                dataReady = false
                publishStatus()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                dataReady = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                publishStatus()
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
            dataReady = cm.activeNetwork?.let { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@let false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: false
            metered = cm.activeNetwork?.let { net ->
                val caps = cm.getNetworkCapabilities(net) ?: return@let true
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } ?: true
        } catch (t: Throwable) {
            Log.e(TAG, "registerNetworkCallback failed", t)
            dataReady = false
        }
        publishStatus()
    }

    fun stop() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Throwable) {
            }
        }
        networkCallback = null
    }

    fun isDataReady(): Boolean = dataReady

    /** True when the active cellular network is treated as metered (typical mobile data). */
    fun isMetered(): Boolean = metered

    fun hasSim(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simState == TelephonyManager.SIM_STATE_READY
    }

    /** Link-only status for consumer builds and gateway fallback. */
    fun linkStatusLine(): String {
        return when {
            !hasSim() -> context.getString(R.string.cell_status_no_sim)
            dataReady -> context.getString(R.string.cell_status_ready)
            else -> context.getString(R.string.cell_status_offline)
        }
    }

    /** SMS emergency path — uses cellular even when mesh radios have no peers. */
    fun sendEmergencySms(
        senderName: String,
        ice: MeshService.Ice,
        lat: Double?,
        lon: Double?,
    ): Boolean {
        val phone = normalizePhone(ice.contactPhone)
        if (phone.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SEND_SMS not granted")
            return false
        }
        val loc = when {
            lat != null && lon != null -> " Location: $lat,$lon."
            else -> ""
        }
        val med = buildString {
            if (ice.bloodType.isNotBlank()) append(" Blood ${ice.bloodType}.")
            if (ice.allergies.isNotBlank()) append(" Allergies: ${ice.allergies}.")
            if (ice.medications.isNotBlank()) append(" Meds: ${ice.medications}.")
        }
        val body = "MESHHOOD SOS from $senderName.$loc$med Need help now. (Mesh + SMS alert)"
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendTextMessage(phone, null, body, null, null)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "SMS send failed", t)
            false
        }
    }

    private fun publishStatus() {
        onLinkChanged()
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        return if (digits.startsWith("+")) digits else digits.filter { it.isDigit() }
    }
}
