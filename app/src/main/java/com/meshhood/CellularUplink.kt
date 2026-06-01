package com.meshhood

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gateway-only HTTP relay transport over cellular data.
 *
 * Pushes encrypted mesh wire frames to a user-configured relay and polls for
 * frames from remote gateways. Relay sees only neighborhood-encrypted blobs.
 */
class CellularUplink(
    private val relayBaseUrl: () -> String,
    private val relayToken: () -> String,
    private val deviceId: () -> String,
    private val isEnabled: () -> Boolean,
    private val isDataReady: () -> Boolean,
    private val onBytes: (ByteArray) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "CellularUplink"
        const val POLL_INTERVAL_SEC = 45L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_PULL = 64
    }

    private val started = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "cellular-uplink").apply { isDaemon = true }
    }
    private var pollTask: ScheduledFuture<*>? = null
    private val pushQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var active = false
    @Volatile private var pullWatermark = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        pollTask = executor.scheduleWithFixedDelay(
            { safeTick() },
            5,
            POLL_INTERVAL_SEC,
            TimeUnit.SECONDS,
        )
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        pollTask?.cancel(false)
        pollTask = null
        pushQueue.clear()
        setActive(false)
        executor.shutdownNow()
    }

    fun send(bytes: ByteArray) {
        if (!isEnabled() || !isDataReady()) return
        pushQueue.add(bytes)
        if (started.get()) {
            executor.execute { safeFlushPush() }
        }
    }

    fun isActive(): Boolean = active

    private fun safeTick() {
        try {
            tick()
        } catch (t: Throwable) {
            Log.w(TAG, "poll tick failed", t)
            setActive(false)
        }
    }

    private fun safeFlushPush() {
        try {
            flushPush()
        } catch (t: Throwable) {
            Log.w(TAG, "push failed", t)
        }
    }

    private fun tick() {
        if (!isEnabled()) {
            setActive(false)
            return
        }
        if (!isDataReady()) {
            setActive(false)
            return
        }
        val base = normalizeBaseUrl(relayBaseUrl())
        if (base.isEmpty()) {
            setActive(false)
            return
        }
        flushPush()
        pull(base)
    }

    private fun flushPush() {
        val base = normalizeBaseUrl(relayBaseUrl())
        if (base.isEmpty()) return
        val batch = mutableListOf<String>()
        while (batch.size < 32) {
            val frame = pushQueue.poll() ?: break
            batch.add(Base64.encodeToString(frame, Base64.NO_WRAP))
        }
        if (batch.isEmpty()) return
        val body = JSONObject().apply {
            put("deviceId", deviceId())
            put("frames", JSONArray(batch))
        }.toString()
        val code = postJson("$base/v1/push", body)
        if (code !in 200..299) {
            Log.w(TAG, "push HTTP $code")
        } else {
            setActive(true)
        }
    }

    private fun pull(base: String) {
        val since = pullWatermark
        val url = "$base/v1/pull?deviceId=${encodeQuery(deviceId())}&since=$since&limit=$MAX_PULL"
        val conn = openGet(url) ?: return
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "pull HTTP $code")
                return
            }
            val text = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(text)
            val arr = root.optJSONArray("frames") ?: return
            var maxTs = since
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val payload = item.optString("payload", "")
                if (payload.isEmpty()) continue
                val ts = item.optLong("ts", 0L)
                if (ts > maxTs) maxTs = ts
                try {
                    onBytes(Base64.decode(payload, Base64.NO_WRAP))
                } catch (t: Throwable) {
                    Log.w(TAG, "bad frame payload", t)
                }
            }
            pullWatermark = maxTs
            if (arr.length() > 0) setActive(true)
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(urlStr: String, body: String): Int {
        val conn = openPost(urlStr) ?: return -1
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun openPost(urlStr: String): HttpURLConnection? =
        openConnection(urlStr, "POST")

    private fun openGet(urlStr: String): HttpURLConnection? =
        openConnection(urlStr, "GET")

    private fun openConnection(urlStr: String, method: String): HttpURLConnection? {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            val token = relayToken().trim()
            if (token.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            if (method == "POST") conn.doOutput = true
            conn
        } catch (t: Throwable) {
            Log.w(TAG, "open $method $urlStr failed", t)
            null
        }
    }

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        onActiveChanged(value)
    }

    internal fun normalizeBaseUrl(raw: String): String =
        raw.trim().removeSuffix("/")

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
