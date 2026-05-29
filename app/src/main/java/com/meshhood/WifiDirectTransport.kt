package com.meshhood

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * WiFi Direct (Wi-Fi P2P) transport — a SECOND radio for the mesh, layered on
 * top of BLE without replacing it.
 *
 * Why: BLE is great for tiny, ultra-low-power discovery and short messages, but
 * it's slow and short-range. WiFi Direct gives ~100m range and megabits of
 * bandwidth with no router/internet — ideal when the grid is down. MeshHood's
 * resilience pitch is "use whatever radio still works", so transports run in
 * parallel and the same encrypted envelopes flow over either one.
 *
 * Auto-discovery uses DNS-SD: each device advertises a "_meshhood._tcp" service
 * and discovers peers advertising the same. When two connect, the group owner
 * opens a TCP server; the client dials in. Messages are length-prefixed bytes —
 * the exact same neighborhood-encrypted envelopes BLE carries — and received
 * bytes are handed straight to the existing decrypt + handle pipeline.
 *
 * NOTE: WiFi Direct is phone-to-phone; verifying the link requires a SECOND
 * Android device. Solo, this initializes, advertises, and discovers (it just
 * won't find a peer). All operations are guarded so a failure never crashes the
 * app or disturbs BLE.
 */
class WifiDirectTransport(
    private val context: Context,
    private val onBytes: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val TAG = "WifiDirect"
        private const val SERVICE_NAME = "_meshhood"
        private const val SERVICE_TYPE = "_tcp"
        private const val PORT = 8988
        private const val MAX_FRAME = 64 * 1024
    }

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private var serverSocket: ServerSocket? = null
    private val outStreams = CopyOnWriteArrayList<DataOutputStream>()

    @Volatile private var started = false
    private val connectedPeers = HashSet<String>()

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        try {
            manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
            channel = manager?.initialize(context, context.mainLooper, null)
            if (manager == null || channel == null) {
                onStatus("WiFi Direct unavailable")
                return
            }
            started = true
            registerReceiver()
            advertiseService()
            discoverPeers()
            onStatus("WiFi Direct: discovering")
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            onStatus("WiFi Direct: failed to start")
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                }
            }
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0
        context.registerReceiver(receiver, filter, flags)
    }

    @SuppressLint("MissingPermission")
    private fun advertiseService() {
        // Use a non-identifying label here; real identity travels encrypted in-band.
        val record = mapOf("name" to "node", "port" to PORT.toString())
        val info = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_NAME, "$SERVICE_TYPE.local.", record)
        manager?.addLocalService(channel, info, logListener("addLocalService"))
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return

        mgr.setDnsSdResponseListeners(
            ch,
            { instanceName, _, srcDevice ->
                if (instanceName.contains(SERVICE_NAME)) {
                    connectTo(srcDevice.deviceAddress)
                }
            },
            { _, _, _ -> }
        )

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        mgr.addServiceRequest(ch, serviceRequest, logListener("addServiceRequest"))
        mgr.discoverServices(ch, logListener("discoverServices"))
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(deviceAddress: String?) {
        if (deviceAddress == null) return
        if (!connectedPeers.add(deviceAddress)) return // already connecting/connected
        val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress }
        manager?.connect(channel, config, logListener("connect:$deviceAddress"))
        onStatus("WiFi Direct: connecting")
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.requestConnectionInfo(ch) { info ->
            if (info == null || !info.groupFormed) return@requestConnectionInfo
            if (info.isGroupOwner) {
                startServer()
                onStatus("WiFi Direct: connected (host)")
            } else {
                val host = info.groupOwnerAddress?.hostAddress ?: return@requestConnectionInfo
                connectToHost(host)
                onStatus("WiFi Direct: connected")
            }
        }
    }

    private fun startServer() {
        if (serverSocket != null) return
        thread(name = "wifi-direct-server", isDaemon = true) {
            try {
                val server = ServerSocket(PORT)
                serverSocket = server
                while (started && !server.isClosed) {
                    val socket = server.accept()
                    handleSocket(socket)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "server error", t)
            }
        }
    }

    private fun connectToHost(host: String) {
        thread(name = "wifi-direct-client", isDaemon = true) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), 8000)
                handleSocket(socket)
            } catch (t: Throwable) {
                Log.e(TAG, "client connect error", t)
            }
        }
    }

    /** Registers a socket for sending and starts a read loop for receiving. */
    private fun handleSocket(socket: Socket) {
        val out = DataOutputStream(socket.getOutputStream())
        outStreams.add(out)
        thread(name = "wifi-direct-read", isDaemon = true) {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (started && !socket.isClosed) {
                    val len = input.readInt()
                    if (len <= 0 || len > MAX_FRAME) break
                    val buf = ByteArray(len)
                    input.readFully(buf)
                    onBytes(buf)
                }
            } catch (_: Throwable) {
                // peer disconnected
            } finally {
                outStreams.remove(out)
                try { socket.close() } catch (_: Throwable) {}
            }
        }
    }

    /** Send already-encrypted envelope bytes to all connected WiFi Direct peers. */
    fun send(bytes: ByteArray) {
        if (outStreams.isEmpty()) return
        for (out in outStreams) {
            try {
                synchronized(out) {
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            } catch (_: Throwable) {
                outStreams.remove(out)
            }
        }
    }

    fun hasPeers(): Boolean = outStreams.isNotEmpty()

    @SuppressLint("MissingPermission")
    fun stop() {
        started = false
        try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Throwable) {}
        receiver = null
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        outStreams.clear()
        try {
            serviceRequest?.let { manager?.removeServiceRequest(channel, it, null) }
            manager?.clearLocalServices(channel, null)
            manager?.cancelConnect(channel, null)
        } catch (_: Throwable) {}
    }

    private fun logListener(op: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { Log.i(TAG, "$op ok") }
        override fun onFailure(reason: Int) { Log.w(TAG, "$op failed: $reason") }
    }
}
