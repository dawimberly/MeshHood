package com.meshhood

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * LAN transport — a THIRD pipe for the mesh that uses an ordinary WiFi network
 * (a home/community router or access point) when one is available.
 *
 * Why, on top of BLE + WiFi Direct: if the grid is partly up — power's on, the
 * router still works, but cell towers are swamped — then every phone on that
 * WiFi can reach every other phone across the whole building/block at full speed
 * with NO server and NO internet. It's the highest-bandwidth, longest-reach pipe
 * we have, and it's still completely serverless.
 *
 * Discovery uses Android NSD (mDNS / DNS-SD): each phone advertises a
 * "_meshhood._tcp" service on an ephemeral TCP port and watches for neighbors
 * advertising the same. On discovery it dials in over TCP and exchanges the exact
 * same neighborhood-encrypted, length-prefixed frames BLE and WiFi Direct carry,
 * handing received bytes straight to the shared decrypt + handle pipeline.
 *
 * It is purely opportunistic: if there's no WiFi, or multicast is blocked, this
 * quietly finds no peers and the BLE/WiFi-Direct mesh keeps running underneath.
 * Every operation is guarded so a failure never crashes the app.
 */
class LanTransport(
    private val context: Context,
    private val onBytes: (ByteArray) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        private const val TAG = "LanTransport"
        private const val SERVICE_TYPE = "_meshhood._tcp."
        private const val MAX_FRAME = 64 * 1024
    }

    private val nsd: NsdManager? by lazy {
        try {
            context.getSystemService(Context.NSD_SERVICE) as NsdManager
        } catch (t: Throwable) {
            Log.e(TAG, "no NSD service", t); null
        }
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var serverSocket: ServerSocket? = null
    private val outStreams = CopyOnWriteArrayList<DataOutputStream>()
    // Remote host addresses we already have a link to (avoid duplicate dials).
    private val connectedHosts = java.util.Collections.synchronizedSet(HashSet<String>())

    private val started = AtomicBoolean(false)
    private var localPort = 0
    // A unique-ish advertised name so we can recognize (and skip) our own service.
    private val myServiceName = "MeshHood-" + (1000..9999).random()
    @Volatile private var registeredName: String = myServiceName

    // NSD historically resolves one service at a time; serialize requests.
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val resolving = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            acquireMulticastLock()
            startServer()           // sets localPort
            registerService()
            startDiscovery()
            onStatus("WiFi LAN: searching")
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            onStatus("WiFi LAN: unavailable")
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("meshhood-lan").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "multicast lock failed (mDNS may still work)", t)
        }
    }

    private fun startServer() {
        val server = ServerSocket(0) // ephemeral port
        serverSocket = server
        localPort = server.localPort
        thread(name = "lan-server", isDaemon = true) {
            try {
                while (started.get() && !server.isClosed) {
                    val socket = server.accept()
                    registerHost(socket.inetAddress?.hostAddress)
                    handleSocket(socket)
                }
            } catch (t: Throwable) {
                if (started.get()) Log.e(TAG, "server error", t)
            }
        }
    }

    private fun registerService() {
        val mgr = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = myServiceName
            serviceType = SERVICE_TYPE
            port = localPort
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                registeredName = s.serviceName // system may de-conflict the name
                Log.i(TAG, "registered as ${s.serviceName} on $localPort")
            }
            override fun onRegistrationFailed(s: NsdServiceInfo, code: Int) {
                Log.w(TAG, "register failed: $code")
            }
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, code: Int) {}
        }
        try {
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (t: Throwable) {
            Log.e(TAG, "registerService threw", t)
        }
    }

    private fun startDiscovery() {
        val mgr = nsd ?: return
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType?.contains("_meshhood") != true) return
                if (info.serviceName == registeredName) return // our own advert
                resolveQueue.add(info)
                pumpResolveQueue()
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {
                Log.w(TAG, "discovery start failed: $code")
            }
            override fun onStopDiscoveryFailed(serviceType: String, code: Int) {}
        }
        try {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (t: Throwable) {
            Log.e(TAG, "discoverServices threw", t)
        }
    }

    private fun pumpResolveQueue() {
        val mgr = nsd ?: return
        if (!resolving.compareAndSet(false, true)) return
        val next = resolveQueue.poll()
        if (next == null) {
            resolving.set(false)
            return
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                resolving.set(false)
                val host = info.host?.hostAddress
                if (host != null && info.port > 0) connectTo(host, info.port)
                pumpResolveQueue()
            }
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                resolving.set(false)
                pumpResolveQueue()
            }
        }
        try {
            mgr.resolveService(next, listener)
        } catch (t: Throwable) {
            resolving.set(false)
            pumpResolveQueue()
        }
    }

    private fun registerHost(host: String?): Boolean {
        if (host == null) return false
        return connectedHosts.add(host)
    }

    private fun connectTo(host: String, port: Int) {
        if (!registerHost(host)) return // already linked to this host
        thread(name = "lan-client", isDaemon = true) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 6000)
                handleSocket(socket)
            } catch (t: Throwable) {
                connectedHosts.remove(host)
                Log.w(TAG, "connect to $host:$port failed", t)
            }
        }
    }

    /** Registers a socket for sending and starts a read loop for receiving. */
    private fun handleSocket(socket: Socket) {
        val host = socket.inetAddress?.hostAddress
        val out = DataOutputStream(socket.getOutputStream())
        outStreams.add(out)
        updatePeerStatus()
        thread(name = "lan-read", isDaemon = true) {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (started.get() && !socket.isClosed) {
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
                if (host != null) connectedHosts.remove(host)
                try { socket.close() } catch (_: Throwable) {}
                updatePeerStatus()
            }
        }
    }

    private fun updatePeerStatus() {
        val n = outStreams.size
        onStatus(if (n == 0) "WiFi LAN: searching" else "WiFi LAN: $n linked")
    }

    /** Send already-encrypted envelope bytes to all linked LAN peers. */
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

    fun stop() {
        started.set(false)
        val mgr = nsd
        try { discoveryListener?.let { mgr?.stopServiceDiscovery(it) } } catch (_: Throwable) {}
        try { registrationListener?.let { mgr?.unregisterService(it) } } catch (_: Throwable) {}
        discoveryListener = null
        registrationListener = null
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
        outStreams.clear()
        connectedHosts.clear()
        try { multicastLock?.release() } catch (_: Throwable) {}
        multicastLock = null
    }
}
