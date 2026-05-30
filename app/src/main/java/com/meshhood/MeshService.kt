package com.meshhood

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.net.Uri
import android.content.Intent
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.content.SharedPreferences
import android.os.IBinder
import android.os.ParcelUuid
import android.text.format.DateFormat
import android.util.Base64
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MeshService : Service() {

    interface MeshCallback {
        fun onUpdate()
    }

    inner class LocalBinder : Binder() {
        val service: MeshService get() = this@MeshService
    }

    private val binder = LocalBinder()

    private var bluetoothGattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null
    private val subscribers = mutableSetOf<BluetoothDevice>()

    private val logEntries = mutableListOf<LogEntry>()

    /** One line in the neighborhood / group feed, tagged for filtering. */
    private data class LogEntry(
        val time: String,
        val sender: String,
        val text: String,
        val scope: String = SCOPE_EVERYONE,
        val emergency: Boolean = false
    ) {
        fun displayLine(): String = "[$time] $sender: $text"
    }

    /** Which feed tab is active (everyone, a DM thread, or a group id). */
    var feedScope: String = SCOPE_EVERYONE
        private set

    private var bleStatus: String = "Starting..."
    private var wifiStatus: String = ""
    private var lanStatus: String = ""
    var status: String = "Starting..."
        private set

    private var wifiDirect: WifiDirectTransport? = null
    private var lan: LanTransport? = null

    private val knownPeers = linkedSetOf<String>()

    // name -> peer's base64 X25519 public key (for truly private direct messages)
    private val peerKeys = HashMap<String, String>()

    // name -> peer's base64 Ed25519 verify key (to check kudos signatures)
    private val peerSignKeys = HashMap<String, String>()

    // "Good Neighbor" reputation: name -> count of verified kudos received.
    private val reputation = HashMap<String, Int>()
    // "giver|helper" pairs already counted (each neighbor credits a helper once).
    private val awardedKudos = HashSet<String>()

    // Capacity status: name -> "limited"/"homebound". Absent => "full" (default).
    // A LIMITED/HOMEBOUND claim only EXEMPTS someone from reciprocity once enough
    // neighbors have vouched for it — so an able person can't self-declare to mooch.
    private val statusOf = HashMap<String, String>()
    // subject -> set of neighbors who vouched for that person's capacity claim.
    private val vouchesFor = HashMap<String, MutableSet<String>>()
    // subject -> neighbors who vouched this person matches their profile photo.
    private val photoVouchesFor = HashMap<String, MutableSet<String>>()
    // Latest SHA-256 hash of each neighbor's profile photo (from signed mesh thumbs).
    private val peerPhotoHash = HashMap<String, String>()

    // ---- Identity & profile ----
    // Your display name (identity is the keypair; this is the human label). Empty
    // string until the user finishes onboarding, which is how we know to show it.
    var myName: String = DEFAULT_NAME
        private set
    private val mySkills = mutableListOf<String>()
    private val myShares = mutableListOf<String>()
    private val myCerts = mutableListOf<String>()
    private var myZone: MeshZone = MeshZone()
    private val geoLocator = GeoLocator(this)

    data class Profile(
        val name: String,
        val skills: List<String>,
        val shares: List<String>,
        val certs: List<String>
    )

    // name -> that neighbor's shared profile (skills / things to share / certs).
    private val peerProfiles = HashMap<String, Profile>()

    /**
     * Emergency / ICE ("In Case of Emergency") card — the life-saving medical
     * snapshot. It is SENSITIVE: it is never part of the normal profile
     * broadcast. It only leaves the phone attached to YOUR OWN emergency signal
     * (pressing the 🚨 button), so responders see your vitals exactly when it
     * matters and never before.
     */
    data class Ice(
        val bloodType: String = "",
        val allergies: String = "",
        val medications: String = "",
        val conditions: String = "",
        val contactName: String = "",
        val contactPhone: String = "",
        val notes: String = ""
    ) {
        fun isBlank(): Boolean = listOf(
            bloodType, allergies, medications, conditions, contactName, contactPhone, notes
        ).all { it.isBlank() }
    }

    private var myIce: Ice = Ice()
    // name -> the ICE card we received with that neighbor's emergency.
    private val peerIce = HashMap<String, Ice>()

    // ---- Groups (community overlay; mesh works fine with zero groups) ----
    /**
     * A neighborhood group — HOA block, building, mutual-aid crew, etc.
     * Admins are additive roles (verify, pin, coordinate, membership). They
     * never moderate or censor speech; the mesh keeps working with no admins.
     */
    data class Group(
        val id: String,
        val name: String,
        val founder: String,
        val admins: MutableSet<String>,
        val members: MutableSet<String>,
        val createdAt: Long
    )

    /** Pinned announcement inside a group (admin-only, signed). */
    data class GroupPin(val admin: String, val text: String, val ts: Long)

    // gid -> group metadata learned from the mesh.
    private val groups = HashMap<String, Group>()
    // gid -> subject -> set of verified cert strings (admin attestation).
    private val groupVerified = HashMap<String, HashMap<String, MutableSet<String>>>()
    // gid -> recent pinned posts (newest first).
    private val groupPins = HashMap<String, MutableList<GroupPin>>()

    private var callback: MeshCallback? = null

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val CHAR_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val CHANNEL_ID = "mesh_channel"
        const val MESSAGE_CHANNEL_ID = "mesh_messages"
        const val TRIAGE_CHANNEL_ID = "mesh_triage"
        const val NOTIFICATION_ID = 1
        const val TRIAGE_NOTIFICATION_ID = 2

        const val EVERYONE = "Everyone"
        const val SCOPE_EVERYONE = "everyone"
        const val DM_SCOPE_PREFIX = "dm:"
        // Fallback identity label before the user completes onboarding.
        const val DEFAULT_NAME = "Contact"

        private const val PREFS_NAME = "meshhood_store"
        private const val KEY_LOG = "log"
        private const val KEY_PEERS = "peers"
        private const val KEY_SIGN = "signpeers"
        private const val KEY_REP = "reputation"
        private const val KEY_AWARDED = "awarded"
        private const val KEY_STATUS = "capacity"
        private const val KEY_VOUCH = "vouches"
        private const val KEY_MY_NAME = "myname"
        private const val KEY_MY_SKILLS = "myskills"
        private const val KEY_MY_SHARES = "myshares"
        private const val KEY_MY_CERTS = "mycerts"
        private const val KEY_PEER_PROFILES = "peerprofiles"
        private const val KEY_MY_ICE = "myice"
        private const val KEY_PEER_ICE = "peerice"
        private const val KEY_GROUPS = "groups"
        private const val KEY_GROUP_VERIFIED = "groupverified"
        private const val KEY_GROUP_PINS = "grouppins"
        private const val KEY_FEED_SCOPE = "feedscope"
        private const val KEY_MY_ZONE = "myzone"
        private const val KEY_PHOTO_VOUCH = "photovouches"
        private const val KEY_PEER_PHOTO_HASH = "peerphotohash"
        private const val MAX_LOG_ENTRIES = 300
        private const val MAX_GROUP_PINS = 10

        // Capacity statuses (FULL is the implicit default).
        const val CAP_FULL = "full"
        const val CAP_LIMITED = "limited"
        const val CAP_HOMEBOUND = "homebound"

        // How many neighbors must vouch before a limited-capacity claim is honored.
        private const val VOUCH_THRESHOLD = 2
        // How much help an ABLE person can receive (giving none) before the gentle
        // "could pitch in" nudge appears. Exempt neighbors are never nudged.
        private const val NUDGE_RECEIVED_THRESHOLD = 3

        // Multi-hop relay: how many phones a message may pass through, and how
        // many recent message IDs we remember to avoid relaying the same one twice.
        private const val TTL_DEFAULT = 6
        private const val MAX_SEEN_IDS = 1000
    }

    // IDs of messages we've already processed/relayed, to kill infinite echoes.
    private val seenIds = LinkedHashSet<String>()
    private val rng = java.security.SecureRandom()

    // Serializes on-device LLM load + inference off the BLE/UI threads.
    private val llmExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private var messageNotificationId = 1000

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadState()
        startForeground(NOTIFICATION_ID, buildNotification("Starting mesh..."))
        startBle()
        startWifiDirect()
        startLan()
        // Load the optional on-device LLM in the background; falls back silently.
        llmExecutor.execute {
            if (LlmEngine.tryLoad(applicationContext)) callback?.onUpdate()
        }
        refreshLiveGeoAsync()
    }

    /** Rolling ZIP from GPS — merged with saved anchor in [effectiveZone]. */
    fun effectiveZone(): MeshZone = ZoneContext.effective(myZone, geoLocator.currentPostal())

    fun livePostal(): String = geoLocator.currentPostal()

    fun refreshLiveGeoAsync() {
        llmExecutor.execute {
            val priorPostal = geoLocator.currentPostal()
            geoLocator.refresh()
            if (geoLocator.currentPostal().isNotEmpty() && geoLocator.currentPostal() != priorPostal) {
                promoteFeedToFinestIfNeeded()
            }
            callback?.onUpdate()
        }
    }

    /** Snap feed to finest locality when GPS postal updates or on load. */
    private fun promoteFeedToFinestIfNeeded() {
        val finest = defaultAreaScope()
        if (finest == SCOPE_EVERYONE) return
        val promote = feedScope == SCOPE_EVERYONE ||
            (MeshZone.isZoneScope(feedScope) && MeshZone.isBroaderThan(feedScope, finest))
        if (!promote) return
        feedScope = finest
        prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
    }

    private fun startWifiDirect() {
        wifiDirect = WifiDirectTransport(
            context = this,
            onBytes = { bytes -> onTransportBytes(bytes) },
            onStatus = { s ->
                wifiStatus = s
                composeStatus()
            }
        ).also { it.start() }
    }

    private fun startLan() {
        lan = LanTransport(
            context = this,
            onBytes = { bytes -> onTransportBytes(bytes) },
            onStatus = { s ->
                lanStatus = s
                composeStatus()
            }
        ).also { it.start() }
    }

    /** Shared receive path for any transport: decrypt then handle. */
    private fun onTransportBytes(bytes: ByteArray) {
        val decrypted = Crypto.decrypt(bytes)
        if (decrypted == null) {
            appendLog(getString(R.string.default_contact_name), getString(R.string.log_wrong_key))
        } else {
            handleIncoming(decrypted)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun setCallback(cb: MeshCallback?) {
        callback = cb
    }

    fun getLogText(): String = getFeedText(feedScope)

    /** Filtered feed for a scope: everyone, a zone level, a DM thread, or one group (+ emergencies always). */
    fun getFeedText(filterScope: String): String {
        val blocks = mutableListOf<String>()
        if (filterScope != SCOPE_EVERYONE && !isDmScope(filterScope) && !MeshZone.isZoneScope(filterScope)) {
            if (groupOf(filterScope) != null) {
                val pins = pinsForGroup(filterScope)
                if (pins.isNotEmpty()) {
                    blocks.add(
                        pins.take(3).joinToString("\n") { p ->
                            "\uD83D\uDCCC ${p.text}\n   \u2014 ${p.admin}"
                        }
                    )
                }
            }
        }
        val home = effectiveZone()
        val entries = logEntries.mapIndexed { index, e -> index to e }
            .filter { (_, e) ->
                if (e.emergency) return@filter true
                // Your own public posts always show in area views (avoids scope mismatch).
                if (e.sender == "You" && !isDmScope(e.scope) && groups[e.scope] == null &&
                    (filterScope == SCOPE_EVERYONE || MeshZone.isZoneScope(filterScope))
                ) {
                    return@filter true
                }
                when {
                    isDmScope(filterScope) -> e.scope == filterScope
                    isGroupMember(filterScope) -> e.scope == filterScope
                    filterScope == SCOPE_EVERYONE || MeshZone.isZoneScope(filterScope) ->
                        MeshZone.visibleInView(e.scope, filterScope)
                    else -> e.scope == filterScope
                }
            }
            .sortedWith(
                compareBy<Pair<Int, LogEntry>> { (_, e) -> if (e.emergency) 0 else 1 }
                    .thenBy { (_, e) -> MeshZone.proximityRank(e.scope, filterScope, home) }
                    .thenBy { (index, _) -> index }
            )
        blocks.addAll(entries.map { (_, e) -> e.displayLine() })
        return blocks.joinToString("\n\n")
    }

    fun setFeedScope(scope: String) {
        feedScope = when {
            scope == SCOPE_EVERYONE -> SCOPE_EVERYONE
            isDmScope(scope) -> scope
            isGroupMember(scope) -> scope
            MeshZone.isZoneScope(scope) -> scope
            else -> SCOPE_EVERYONE
        }
        prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
        callback?.onUpdate()
    }

    /** Default area view: finest effective level (live ZIP when available), or Everyone. */
    fun defaultAreaScope(): String =
        if (effectiveZone().hasAny()) effectiveZone().defaultViewScope() else SCOPE_EVERYONE

    fun getMyZone(): MeshZone = myZone

    fun saveMyZone(zone: MeshZone) {
        // Anchor only — postal rolls from [GeoLocator], not saved on profile.
        myZone = zone.copy(postal = "")
        prefs.edit().putString(KEY_MY_ZONE, myZone.toJson().toString()).apply()
        if (effectiveZone().hasAny()) {
            feedScope = effectiveZone().defaultViewScope()
            prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
        }
        callback?.onUpdate()
    }

    /** Scope tag for outgoing public broadcasts (finest effective level). */
    private fun publicBroadcastScope(): String =
        ZoneContext.broadcastChannel(myZone, geoLocator.currentPostal())

    private fun currentGeoSnapshot(): GeoLocator.Snapshot? = geoLocator.current()

    fun feedScopeLabel(scope: String): String = when {
        scope == SCOPE_EVERYONE -> EVERYONE
        MeshZone.isZoneScope(scope) -> effectiveZone().labelForScope(scope)
        isDmScope(scope) -> peerFromDmScope(scope)
        else -> groupOf(scope)?.name ?: scope
    }

    fun dmScope(peer: String): String = DM_SCOPE_PREFIX + peer

    fun isDmScope(scope: String): Boolean = scope.startsWith(DM_SCOPE_PREFIX)

    fun peerFromDmScope(scope: String): String = scope.removePrefix(DM_SCOPE_PREFIX)

    /** Labels for area picker: locality (specific → broad), then Everyone, then groups. */
    fun feedScopeOptions(): List<Pair<String, String>> {
        val options = mutableListOf<Pair<String, String>>()
        options.addAll(effectiveZone().optionsMostSpecificFirst())
        options.add(SCOPE_EVERYONE to EVERYONE)
        for (g in myGroups()) options.add(g.id to g.name)
        return options
    }

    data class DmConversation(
        val peer: String,
        val preview: String,
        val time: String,
        val outgoing: Boolean,
    )

    /** Most-recent-first list of private threads for the Chats inbox. */
    fun dmConversations(): List<DmConversation> {
        val latestByPeer = linkedMapOf<String, LogEntry>()
        for (entry in logEntries) {
            if (!isDmScope(entry.scope)) continue
            latestByPeer[peerFromDmScope(entry.scope)] = entry
        }
        return latestByPeer.entries
            .map { (peer, entry) ->
                DmConversation(
                    peer = peer,
                    preview = dmPreview(entry),
                    time = entry.time,
                    outgoing = entry.sender.startsWith("You"),
                )
            }
            .sortedByDescending { conv ->
                logEntries.indexOfLast { isDmScope(it.scope) && peerFromDmScope(it.scope) == conv.peer }
            }
    }

    private fun dmPreview(entry: LogEntry): String {
        val body = entry.text.trim()
        return if (entry.sender.startsWith("You")) "You: $body" else body
    }

    /** Move older DM lines out of the Everyone feed into per-neighbor threads. */
    private fun migrateLegacyDmScopes() {
        var changed = false
        for (i in logEntries.indices) {
            val entry = logEntries[i]
            if (entry.scope != SCOPE_EVERYONE || entry.emergency) continue
            val peer = legacyDmPeer(entry.sender) ?: continue
            logEntries[i] = entry.copy(scope = dmScope(peer))
            changed = true
        }
        if (changed) saveLog()
    }

    private fun legacyDmPeer(sender: String): String? {
        if (sender.startsWith("You \u2192 ")) {
            return sender.removePrefix("You \u2192 ")
                .removeSuffix(" \uD83D\uDD10")
                .trim()
        }
        if (sender.startsWith("DM from ")) {
            return sender.removePrefix("DM from ")
                .removeSuffix(" \uD83D\uDD10")
                .trim()
        }
        return null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        val ongoing = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_mesh_status),
            NotificationManager.IMPORTANCE_LOW
        )
        ongoing.description = getString(R.string.channel_mesh_desc)
        manager.createNotificationChannel(ongoing)

        val messages = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            getString(R.string.channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        )
        messages.description = getString(R.string.notify_new_message)
        messages.enableVibration(true)
        manager.createNotificationChannel(messages)

        val triage = NotificationChannel(
            TRIAGE_CHANNEL_ID,
            getString(R.string.channel_area_status),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        triage.description = getString(R.string.notify_area_status)
        manager.createNotificationChannel(triage)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notifyIncoming(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(messageNotificationId++, notification)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshHood active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun setStatus(text: String) {
        bleStatus = text
        composeStatus()
    }

    private fun composeStatus() {
        status = listOf(bleStatus, wifiStatus, lanStatus)
            .filter { it.isNotEmpty() }
            .joinToString("  •  ")
        updateNotification(status)
        callback?.onUpdate()
    }

    private fun appendLog(
        sender: String,
        message: String,
        scope: String = SCOPE_EVERYONE,
        emergency: Boolean = false
    ) {
        val time = DateFormat.format("h:mm a", System.currentTimeMillis()).toString()
        logEntries.add(LogEntry(time, sender, message, scope, emergency))
        while (logEntries.size > MAX_LOG_ENTRIES) logEntries.removeAt(0)
        saveLog()
        callback?.onUpdate()
    }

    private fun parseLegacyLog(line: String): LogEntry {
        val time = line.substringAfter("[").substringBefore("]", "")
        val afterTime = line.substringAfter("] ", line)
        val sender = afterTime.substringBefore(": ", DEFAULT_NAME)
        val text = afterTime.substringAfter(": ", "")
        return LogEntry(time, sender, text)
    }

    fun clearFeed() {
        logEntries.clear()
        saveLog()
        callback?.onUpdate()
    }

    private fun loadState() {
        try {
            prefs.getString(KEY_LOG, null)?.let { raw ->
                val arr = JSONArray(raw)
                logEntries.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o != null) {
                        logEntries.add(
                            LogEntry(
                                time = o.optString("time", ""),
                                sender = o.optString("sender", DEFAULT_NAME),
                                text = o.optString("text", ""),
                                scope = o.optString("scope", SCOPE_EVERYONE),
                                emergency = o.optBoolean("emergency", false)
                            )
                        )
                    } else {
                        logEntries.add(parseLegacyLog(arr.getString(i)))
                    }
                }
            }
            migrateLegacyDmScopes()
            prefs.getString(KEY_PEERS, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) {
                    val pub = obj.optString(name, "")
                    knownPeers.add(name)
                    if (pub.isNotEmpty()) peerKeys[name] = pub
                }
            }
            prefs.getString(KEY_SIGN, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) peerSignKeys[name] = obj.optString(name, "")
            }
            prefs.getString(KEY_REP, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) reputation[name] = obj.optInt(name, 0)
            }
            prefs.getString(KEY_AWARDED, null)?.let { raw ->
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) awardedKudos.add(arr.getString(i))
            }
            prefs.getString(KEY_STATUS, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) statusOf[name] = obj.optString(name, CAP_FULL)
            }
            prefs.getString(KEY_VOUCH, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (subject in obj.keys()) {
                    val arr = obj.optJSONArray(subject) ?: continue
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    vouchesFor[subject] = set
                }
            }
            prefs.getString(KEY_PHOTO_VOUCH, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (subject in obj.keys()) {
                    val arr = obj.optJSONArray(subject) ?: continue
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    photoVouchesFor[subject] = set
                }
            }
            prefs.getString(KEY_PEER_PHOTO_HASH, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) {
                    peerPhotoHash[name] = obj.optString(name, "")
                }
            }
            prefs.getString(KEY_MY_NAME, "")?.takeIf { it.isNotEmpty() }?.let { myName = it }
            loadStringList(KEY_MY_SKILLS, mySkills)
            loadStringList(KEY_MY_SHARES, myShares)
            loadStringList(KEY_MY_CERTS, myCerts)
            prefs.getString(KEY_PEER_PROFILES, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) {
                    obj.optJSONObject(name)?.let { peerProfiles[name] = profileFromJson(name, it) }
                }
            }
            prefs.getString(KEY_MY_ICE, null)?.let { myIce = iceFromJson(JSONObject(it)) }
            prefs.getString(KEY_PEER_ICE, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (name in obj.keys()) {
                    obj.optJSONObject(name)?.let { peerIce[name] = iceFromJson(it) }
                }
            }
            prefs.getString(KEY_GROUPS, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (gid in obj.keys()) {
                    obj.optJSONObject(gid)?.let { groups[gid] = groupFromJson(gid, it) }
                }
            }
            prefs.getString(KEY_GROUP_VERIFIED, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (gid in obj.keys()) {
                    val bySubject = HashMap<String, MutableSet<String>>()
                    obj.optJSONObject(gid)?.let { subObj ->
                        for (subject in subObj.keys()) {
                            val arr = subObj.optJSONArray(subject) ?: continue
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) set.add(arr.getString(i))
                            bySubject[subject] = set
                        }
                    }
                    groupVerified[gid] = bySubject
                }
            }
            prefs.getString(KEY_GROUP_PINS, null)?.let { raw ->
                val obj = JSONObject(raw)
                for (gid in obj.keys()) {
                    val arr = obj.optJSONArray(gid) ?: continue
                    val pins = mutableListOf<GroupPin>()
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { pins.add(pinFromJson(it)) }
                    }
                    groupPins[gid] = pins
                }
            }
            prefs.getString(KEY_FEED_SCOPE, SCOPE_EVERYONE)?.let { feedScope = it }
            prefs.getString(KEY_MY_ZONE, null)?.let { raw ->
                myZone = MeshZone.fromJson(JSONObject(raw))
            }
            migrateDefaultZone()
            feedScope = sanitizeFeedScope(feedScope)
            prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
        } catch (_: Exception) {
            // Corrupt store — start fresh rather than crash.
        }
        rebuildCoordinatorFromLog()
    }

    /** Existing profiles from before area support — seed nation so the picker is usable. */
    private fun migrateDefaultZone() {
        if (myZone.hasAny() || !hasProfile()) return
        myZone = MeshZone(nation = "US")
        prefs.edit().putString(KEY_MY_ZONE, myZone.toJson().toString()).apply()
        if (feedScope == SCOPE_EVERYONE) {
            feedScope = defaultAreaScope()
            prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
        }
    }

    /** True once state is set and/or live GPS postal is available. */
    fun hasLocalArea(): Boolean =
        myZone.state.isNotBlank() || geoLocator.currentPostal().isNotBlank()

    /** Drop stale scopes; default to most specific locality when area is configured. */
    private fun sanitizeFeedScope(scope: String): String {
        val finest = defaultAreaScope()
        return when {
            scope == SCOPE_EVERYONE ->
                if (effectiveZone().hasAny()) finest else scope
            isDmScope(scope) -> scope
            MeshZone.isZoneScope(scope) -> when {
                MeshZone.valueFromScope(scope).isBlank() -> finest
                finest != SCOPE_EVERYONE && MeshZone.isBroaderThan(scope, finest) -> finest
                else -> scope
            }
            groups.containsKey(scope) && isGroupMember(scope) -> scope
            else -> finest
        }
    }

    /** Replay the saved public messages through the Coordinator after a restart. */
    private fun rebuildCoordinatorFromLog() {
        Coordinator.reset()
        for (entry in logEntries) {
            val sender = entry.sender
            val text = entry.text
            if (sender.isEmpty() || text.isEmpty()) continue
            // Skip private DMs; replay only public broadcasts/emergencies.
            if (sender.startsWith("DM from") || sender.startsWith("You \u2192")) continue
            if (entry.scope != SCOPE_EVERYONE && !entry.emergency &&
                !MeshZone.isZoneScope(entry.scope)) continue
            val name = when {
                sender == "You" || sender == "EMERGENCY" -> myName
                sender.contains(" \u00b7 ") -> sender.substringAfter(" \u00b7 ")
                else -> sender
            }
            Coordinator.process(name, text)
        }
    }

    private fun saveLog() {
        val arr = JSONArray()
        for (e in logEntries) {
            arr.put(JSONObject().apply {
                put("time", e.time)
                put("sender", e.sender)
                put("text", e.text)
                put("scope", e.scope)
                put("emergency", e.emergency)
            })
        }
        prefs.edit().putString(KEY_LOG, arr.toString()).apply()
    }

    private fun savePeers() {
        val obj = JSONObject()
        for (name in knownPeers) obj.put(name, peerKeys[name] ?: "")
        val signObj = JSONObject()
        for ((name, spub) in peerSignKeys) signObj.put(name, spub)
        prefs.edit()
            .putString(KEY_PEERS, obj.toString())
            .putString(KEY_SIGN, signObj.toString())
            .apply()
    }

    private fun saveReputation() {
        val rep = JSONObject()
        for ((name, count) in reputation) rep.put(name, count)
        prefs.edit()
            .putString(KEY_REP, rep.toString())
            .putString(KEY_AWARDED, JSONArray(awardedKudos.toList()).toString())
            .apply()
    }

    private fun saveCapacity() {
        val status = JSONObject()
        for ((name, cap) in statusOf) status.put(name, cap)
        val vouch = JSONObject()
        for ((subject, set) in vouchesFor) vouch.put(subject, JSONArray(set.toList()))
        prefs.edit()
            .putString(KEY_STATUS, status.toString())
            .putString(KEY_VOUCH, vouch.toString())
            .apply()
    }

    private fun savePhotoState() {
        val vouch = JSONObject()
        for ((subject, set) in photoVouchesFor) vouch.put(subject, JSONArray(set.toList()))
        val hashes = JSONObject()
        for ((name, hash) in peerPhotoHash) hashes.put(name, hash)
        prefs.edit()
            .putString(KEY_PHOTO_VOUCH, vouch.toString())
            .putString(KEY_PEER_PHOTO_HASH, hashes.toString())
            .apply()
    }

    private fun loadStringList(key: String, into: MutableList<String>) {
        prefs.getString(key, null)?.let { raw ->
            val arr = JSONArray(raw)
            into.clear()
            for (i in 0 until arr.length()) into.add(arr.getString(i))
        }
    }

    private fun profileFromJson(name: String, o: JSONObject): Profile = Profile(
        name = name,
        skills = jsonToList(o.optJSONArray("skills")),
        shares = jsonToList(o.optJSONArray("shares")),
        certs = jsonToList(o.optJSONArray("certs"))
    )

    private fun jsonToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun saveMyProfilePrefs() {
        prefs.edit()
            .putString(KEY_MY_NAME, myName)
            .putString(KEY_MY_SKILLS, JSONArray(mySkills).toString())
            .putString(KEY_MY_SHARES, JSONArray(myShares).toString())
            .putString(KEY_MY_CERTS, JSONArray(myCerts).toString())
            .apply()
    }

    private fun savePeerProfiles() {
        val obj = JSONObject()
        for ((name, p) in peerProfiles) {
            obj.put(name, JSONObject().apply {
                put("skills", JSONArray(p.skills))
                put("shares", JSONArray(p.shares))
                put("certs", JSONArray(p.certs))
            })
        }
        prefs.edit().putString(KEY_PEER_PROFILES, obj.toString()).apply()
    }

    private fun iceToJson(ice: Ice): JSONObject = JSONObject().apply {
        put("blood", ice.bloodType)
        put("allergies", ice.allergies)
        put("meds", ice.medications)
        put("conditions", ice.conditions)
        put("contactName", ice.contactName)
        put("contactPhone", ice.contactPhone)
        put("notes", ice.notes)
    }

    private fun iceFromJson(o: JSONObject): Ice = Ice(
        bloodType = o.optString("blood", ""),
        allergies = o.optString("allergies", ""),
        medications = o.optString("meds", ""),
        conditions = o.optString("conditions", ""),
        contactName = o.optString("contactName", ""),
        contactPhone = o.optString("contactPhone", ""),
        notes = o.optString("notes", "")
    )

    private fun savePeerIce() {
        val obj = JSONObject()
        for ((name, ice) in peerIce) obj.put(name, iceToJson(ice))
        prefs.edit().putString(KEY_PEER_ICE, obj.toString()).apply()
    }

    private fun groupFromJson(gid: String, o: JSONObject): Group {
        val admins = mutableSetOf<String>()
        o.optJSONArray("admins")?.let { arr ->
            for (i in 0 until arr.length()) admins.add(arr.getString(i))
        }
        val members = mutableSetOf<String>()
        o.optJSONArray("members")?.let { arr ->
            for (i in 0 until arr.length()) members.add(arr.getString(i))
        }
        return Group(
            id = gid,
            name = o.optString("name", gid),
            founder = o.optString("founder", ""),
            admins = admins,
            members = members,
            createdAt = o.optLong("createdAt", 0L)
        )
    }

    private fun groupToJson(g: Group): JSONObject = JSONObject().apply {
        put("name", g.name)
        put("founder", g.founder)
        put("admins", JSONArray(g.admins.toList()))
        put("members", JSONArray(g.members.toList()))
        put("createdAt", g.createdAt)
    }

    private fun pinFromJson(o: JSONObject): GroupPin = GroupPin(
        admin = o.optString("admin", ""),
        text = o.optString("text", ""),
        ts = o.optLong("ts", 0L)
    )

    private fun pinToJson(p: GroupPin): JSONObject = JSONObject().apply {
        put("admin", p.admin)
        put("text", p.text)
        put("ts", p.ts)
    }

    private fun saveGroups() {
        val obj = JSONObject()
        for ((gid, g) in groups) obj.put(gid, groupToJson(g))
        val verified = JSONObject()
        for ((gid, bySubject) in groupVerified) {
            val subObj = JSONObject()
            for ((subject, certs) in bySubject) subObj.put(subject, JSONArray(certs.toList()))
            verified.put(gid, subObj)
        }
        val pins = JSONObject()
        for ((gid, list) in groupPins) {
            pins.put(gid, JSONArray(list.map { pinToJson(it) }))
        }
        prefs.edit()
            .putString(KEY_GROUPS, obj.toString())
            .putString(KEY_GROUP_VERIFIED, verified.toString())
            .putString(KEY_GROUP_PINS, pins.toString())
            .apply()
    }

    fun hasIce(): Boolean = !myIce.isBlank()

    fun getMyIce(): Ice = myIce

    fun iceOf(name: String): Ice? = if (name == myName) myIce.takeIf { !it.isBlank() } else peerIce[name]

    fun saveIce(ice: Ice) {
        myIce = ice
        prefs.edit().putString(KEY_MY_ICE, iceToJson(ice).toString()).apply()
        appendLog("You", "updated Emergency Card")
        callback?.onUpdate()
    }

    /** One-line summary for notifications / feed. */
    fun iceSummary(ice: Ice): String {
        val parts = mutableListOf<String>()
        if (ice.bloodType.isNotBlank()) parts.add("Blood ${ice.bloodType}")
        if (ice.allergies.isNotBlank()) parts.add("Allergies: ${ice.allergies}")
        if (ice.medications.isNotBlank()) parts.add("Meds: ${ice.medications}")
        if (ice.conditions.isNotBlank()) parts.add("Conditions: ${ice.conditions}")
        if (ice.contactName.isNotBlank() || ice.contactPhone.isNotBlank())
            parts.add("ICE contact: ${ice.contactName} ${ice.contactPhone}".trim())
        return parts.joinToString(" · ")
    }

    private fun startBle() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            setStatus("Turn Bluetooth ON, then reopen")
            return
        }
        setupGattServer(bluetoothManager)
        startAdvertising(adapter)
    }

    @SuppressLint("MissingPermission")
    private fun setupGattServer(bluetoothManager: BluetoothManager) {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        service.addCharacteristic(characteristic)
        messageCharacteristic = characteristic

        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        bluetoothGattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                setStatus(getString(R.string.status_connected))
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                subscribers.remove(device)
                setStatus(getString(R.string.status_advertising))
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHAR_UUID) {
                onTransportBytes(value)
            }
            if (responseNeeded) {
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value
                    )
                } catch (_: SecurityException) {
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                val enabled = value.isNotEmpty() && value[0].toInt() != 0
                if (enabled) {
                    subscribers.add(device)
                    // Announce our public key so neighbors can send us private DMs.
                    sendKeyAnnouncement()
                } else {
                    subscribers.remove(device)
                }
            }
            if (responseNeeded) {
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value
                    )
                } catch (_: SecurityException) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(adapter: BluetoothAdapter) {
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            setStatus("BLE advertising not supported")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                setStatus(getString(R.string.status_advertising))
            }

            override fun onStartFailure(errorCode: Int) {
                setStatus("Advertising failed (code $errorCode)")
            }
        }
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    fun getPeers(): List<String> = knownPeers.toList()

    fun reputationOf(name: String): Int = reputation[name] ?: 0

    // ---- Reciprocity ("did help flow both ways?") ----

    /** Distinct neighbors this person HELPED (kudos received). */
    fun helpsGivenOf(name: String): Int = reputation[name] ?: 0

    /** Distinct neighbors who HELPED this person (kudos this person gave). */
    fun helpsReceivedOf(name: String): Int =
        awardedKudos.count { it.startsWith("$name|") }

    data class Reciprocity(val given: Int, val received: Int, val glyph: String, val label: String)

    /**
     * A capacity-aware reciprocity rating from the verified kudos flow.
     *
     * Ethics rules baked in here:
     *  - Emergencies bypass all of this (never called during a 🚨).
     *  - A vouched limited/homebound neighbor is EXEMPT — they're shown as cared
     *    for, never flagged for receiving a lot and giving little.
     *  - An unverified limited claim still gets the benefit of the doubt (no
     *    nudge), but is shown as needing vouches so neighbors can confirm it.
     *  - Only an ABLE (full-capacity) person who takes repeatedly and gives
     *    nothing gets the gentle "could pitch in" prompt.
     */
    fun reciprocityOf(name: String): Reciprocity {
        val given = helpsGivenOf(name)
        val received = helpsReceivedOf(name)
        if (isExempt(name)) {
            return Reciprocity(given, received, "\uD83D\uDC9B", "Cared for") // 💛 community supports them
        }
        if (isUnverifiedClaim(name)) {
            val word = if (capacityOf(name) == CAP_HOMEBOUND) "Homebound" else "Limited"
            return Reciprocity(given, received, "\uD83D\uDFE1", "$word · needs vouches")
        }
        val (glyph, label) = when {
            given > 0 && received > 0 -> "\uD83D\uDD04" to "Pays it forward"   // 🔄 closed the loop
            given > 0 -> "\uD83C\uDF1F" to "Generous"                          // 🌟 gives, hasn't needed help
            received >= NUDGE_RECEIVED_THRESHOLD && given == 0 ->
                "\u26A0\uFE0F" to "Could pitch in"                             // ⚠️ able + takes a lot + gives none
            received > 0 -> "\uD83E\uDD1D" to "Receiving help"                 // 🤝 helped, not yet given back
            else -> "\uD83C\uDF31" to "Newcomer"                               // 🌱 no activity yet
        }
        return Reciprocity(given, received, glyph, label)
    }

    /**
     * An opportunity-framed nudge (never a shaming one) for an able neighbor who
     * has received a lot of help but given none. Returns null for everyone else,
     * including all exempt/limited neighbors.
     */
    fun opportunityNudge(name: String): String? {
        if (name == myName || isExempt(name) || isUnverifiedClaim(name)) return null
        if (capacityOf(name) != CAP_FULL) return null
        val given = helpsGivenOf(name)
        val received = helpsReceivedOf(name)
        if (given == 0 && received >= NUDGE_RECEIVED_THRESHOLD) {
            return "$name has received help $received times and is able to pitch in — invite them to the next crew."
        }
        return null
    }

    /** All names that appear anywhere in the reputation/kudos record. */
    fun ratedNames(): List<String> {
        val names = sortedSetOf<String>()
        names.addAll(reputation.keys)
        names.addAll(statusOf.keys)
        names.addAll(vouchesFor.keys)
        for (pair in awardedKudos) {
            val i = pair.indexOf('|')
            if (i > 0) {
                names.add(pair.substring(0, i))
                names.add(pair.substring(i + 1))
            }
        }
        names.remove("")
        return names.toList()
    }

    /** Send a signed "Good Neighbor" kudos crediting [helper]. */
    fun thank(helper: String) {
        if (helper == myName) return
        val ts = System.currentTimeMillis()
        val payload = SignKeys.kudosPayload(myName, helper, ts)
        val sig = SignKeys.sign(payload) ?: return
        // Apply locally too (each giver credits a helper once).
        applyKudos(myName, helper)
        appendLog("You", "\uD83D\uDE4F ${getString(R.string.kudos_log, helper)}")
        val id = newId()
        markSeen(id)
        flood(kudosEnvelope(id, TTL_DEFAULT, myName, helper, ts, sig))
    }

    private fun applyKudos(giver: String, helper: String): Boolean {
        val pairKey = "$giver|$helper"
        if (!awardedKudos.add(pairKey)) return false
        reputation[helper] = (reputation[helper] ?: 0) + 1
        saveReputation()
        callback?.onUpdate()
        return true
    }

    private fun handleKudos(obj: JSONObject, ttl: Int) {
        val giver = obj.optString("from", "")
        val helper = obj.optString("to", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (giver.isEmpty() || helper.isEmpty() || sig.isEmpty()) return

        val verifyKey = peerSignKeys[giver]
        if (verifyKey != null) {
            // We can check it: only count (and relay) if the signature is valid.
            val payload = SignKeys.kudosPayload(giver, helper, ts)
            if (!SignKeys.verify(payload, sig, verifyKey)) {
                android.util.Log.w("Reputation", "Rejected forged kudos from $giver")
                return // forged — drop, do not relay
            }
            if (applyKudos(giver, helper)) {
                appendLog("Reputation", "$helper +1 \u2b50 (thanked by $giver)")
            }
            relay(obj, ttl)
        } else {
            // We don't have the giver's verify key yet — can't check, so just
            // forward and let a node that knows the giver verify it.
            relay(obj, ttl)
        }
    }

    // ---- Capacity status & vouching ----

    fun capacityOf(name: String): String = statusOf[name] ?: CAP_FULL

    fun vouchCountFor(name: String): Int = vouchesFor[name]?.size ?: 0

    /** A limited/homebound claim is honored only once enough neighbors vouch. */
    fun isExempt(name: String): Boolean {
        val cap = capacityOf(name)
        if (cap == CAP_FULL) return false
        return vouchCountFor(name) >= VOUCH_THRESHOLD
    }

    /** True for a limited/homebound claim that still needs neighbor vouches. */
    fun isUnverifiedClaim(name: String): Boolean {
        val cap = capacityOf(name)
        return cap != CAP_FULL && vouchCountFor(name) < VOUCH_THRESHOLD
    }

    /** Set and broadcast my own capacity status (signed so no one can spoof it). */
    fun setMyStatus(cap: String) {
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(statusPayload(myName, cap, ts)) ?: return
        statusOf[myName] = cap
        saveCapacity()
        appendLog("You", "set status: ${capacityLabel(cap)}")
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(statusEnvelope(id, TTL_DEFAULT, myName, cap, ts, sig))
    }

    /** Vouch that [subject]'s limited-capacity claim is genuine (signed). */
    fun vouchFor(subject: String) {
        if (subject == myName) return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(vouchPayload(myName, subject, ts)) ?: return
        if (applyVouch(myName, subject)) {
            appendLog("You", "\u2713 vouched for $subject's status")
        }
        val id = newId()
        markSeen(id)
        flood(vouchEnvelope(id, TTL_DEFAULT, myName, subject, ts, sig))
    }

    private fun applyVouch(voucher: String, subject: String): Boolean {
        val set = vouchesFor.getOrPut(subject) { mutableSetOf() }
        val added = set.add(voucher)
        if (added) {
            saveCapacity()
            callback?.onUpdate()
        }
        return added
    }

    private fun statusPayload(name: String, cap: String, ts: Long) = "status|$name|$cap|$ts"
    private fun vouchPayload(voucher: String, subject: String, ts: Long) = "vouch|$voucher|$subject|$ts"

    private fun capacityLabel(cap: String): String = when (cap) {
        CAP_LIMITED -> "\uD83D\uDFE1 Limited"
        CAP_HOMEBOUND -> "\uD83C\uDFE0 Homebound"
        else -> "\uD83D\uDFE2 Full"
    }

    private fun handleStatus(obj: JSONObject, ttl: Int) {
        val from = obj.optString("from", "")
        val cap = obj.optString("cap", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (from.isEmpty() || cap.isEmpty()) return
        val verifyKey = peerSignKeys[from]
        if (verifyKey != null) {
            if (!SignKeys.verify(statusPayload(from, cap, ts), sig, verifyKey)) {
                android.util.Log.w("Capacity", "Rejected forged status from $from")
                return
            }
            if (statusOf[from] != cap) {
                if (cap == CAP_FULL) statusOf.remove(from) else statusOf[from] = cap
                saveCapacity()
                appendLog("Status", "$from is now ${capacityLabel(cap)}")
                callback?.onUpdate()
            }
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleVouch(obj: JSONObject, ttl: Int) {
        val voucher = obj.optString("from", "")
        val subject = obj.optString("to", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (voucher.isEmpty() || subject.isEmpty()) return
        val verifyKey = peerSignKeys[voucher]
        if (verifyKey != null) {
            if (!SignKeys.verify(vouchPayload(voucher, subject, ts), sig, verifyKey)) {
                android.util.Log.w("Capacity", "Rejected forged vouch from $voucher")
                return
            }
            if (applyVouch(voucher, subject)) {
                appendLog("Vouch", "$voucher vouched for $subject (${vouchCountFor(subject)}/$VOUCH_THRESHOLD)")
            }
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    fun photoVouchCountFor(name: String): Int = photoVouchesFor[name]?.size ?: 0

    fun hasPhotoFor(name: String): Boolean =
        if (name == myName) hasProfilePhoto() else PeerPhotos.hasPhoto(this, name)

    /** True once enough neighbors vouch the person matches their profile photo. */
    fun isPhotoVerified(name: String): Boolean =
        hasPhotoFor(name) && photoVouchCountFor(name) >= VOUCH_THRESHOLD

    fun isProfilePhotoVerified(): Boolean = isPhotoVerified(myName)

    /** Vouch that [subject]'s profile photo looks like them in person (signed). */
    fun vouchProfilePhoto(subject: String) {
        if (subject == myName) return
        val hash = photoHashFor(subject) ?: return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(photoVouchPayload(myName, subject, hash, ts)) ?: return
        if (applyPhotoVouch(myName, subject, hash)) {
            appendLog("You", "\u2713 vouched for $subject's photo")
        }
        val id = newId()
        markSeen(id)
        flood(photoVouchEnvelope(id, TTL_DEFAULT, myName, subject, hash, ts, sig))
    }

    private fun photoHashFor(name: String): String? =
        if (name == myName) ProfilePhoto.contentHash(this) else peerPhotoHash[name]

    private fun applyPhotoVouch(voucher: String, subject: String, hash: String): Boolean {
        if (photoHashFor(subject) != hash) return false
        val set = photoVouchesFor.getOrPut(subject) { mutableSetOf() }
        val added = set.add(voucher)
        if (added) {
            savePhotoState()
            callback?.onUpdate()
        }
        return added
    }

    private fun notePeerPhotoHash(name: String, hash: String) {
        val prior = peerPhotoHash[name]
        if (prior == hash) return
        peerPhotoHash[name] = hash
        photoVouchesFor.remove(name)
        if (prior != null) PeerPhotos.delete(this, name)
        savePhotoState()
    }

    private fun photoThumbPayload(from: String, hash: String, ts: Long) = "photothumb|$from|$hash|$ts"
    private fun photoVouchPayload(voucher: String, subject: String, hash: String, ts: Long) =
        "photovouch|$voucher|$subject|$hash|$ts"

    private fun handlePhotoThumb(obj: JSONObject, ttl: Int) {
        val from = obj.optString("from", "")
        val hash = obj.optString("hash", "")
        val thumbB64 = obj.optString("thumb", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (from.isEmpty() || hash.isEmpty() || thumbB64.isEmpty() || from == myName) {
            relay(obj, ttl)
            return
        }
        val verifyKey = peerSignKeys[from]
        if (verifyKey == null) {
            relay(obj, ttl)
            return
        }
        if (!SignKeys.verify(photoThumbPayload(from, hash, ts), sig, verifyKey)) {
            android.util.Log.w("Photo", "Rejected forged photo thumb from $from")
            return
        }
        val bytes = try {
            Base64.decode(thumbB64, Base64.NO_WRAP)
        } catch (_: Exception) {
            relay(obj, ttl)
            return
        }
        if (PeerPhotos.saveBytes(this, from, bytes)) {
            notePeerPhotoHash(from, hash)
            trackPeer(from)
            appendLog("Photo", "$from shared a profile photo")
            callback?.onUpdate()
        }
        relay(obj, ttl)
    }

    private fun handlePhotoVouch(obj: JSONObject, ttl: Int) {
        val voucher = obj.optString("from", "")
        val subject = obj.optString("to", "")
        val hash = obj.optString("hash", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (voucher.isEmpty() || subject.isEmpty() || hash.isEmpty()) return
        val verifyKey = peerSignKeys[voucher]
        if (verifyKey != null) {
            if (!SignKeys.verify(photoVouchPayload(voucher, subject, hash, ts), sig, verifyKey)) {
                android.util.Log.w("Photo", "Rejected forged photo vouch from $voucher")
                return
            }
            if (applyPhotoVouch(voucher, subject, hash)) {
                appendLog(
                    "Photo",
                    "$voucher vouched for $subject's photo (${photoVouchCountFor(subject)}/$VOUCH_THRESHOLD)",
                )
            }
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    // ---- Crew help-calls ("who can come help shovel Edna's driveway?") ----

    fun callForHelp(task: String) {
        val id = newId()
        markSeen(id)
        appendLog("You", "\uD83D\uDCE3 Help call: $task")
        flood(crewEnvelope(id, TTL_DEFAULT, myName, task))
    }

    fun joinCrew(task: String) {
        val id = newId()
        markSeen(id)
        appendLog("You", "\u270B joined: $task")
        flood(crewJoinEnvelope(id, TTL_DEFAULT, myName, task))
    }

    // ---- Groups (community overlay) ----

    /** Prefix for group recipients in sendMessage(). */
    fun groupRecipient(gid: String): String = "group:$gid"

    fun isGroupRecipient(to: String): Boolean = to.startsWith("group:")

    fun groupIdFromRecipient(to: String): String = to.removePrefix("group:")

    fun myGroups(): List<Group> = groups.values
        .filter { myName in it.members }
        .sortedBy { it.name.lowercase() }

    fun allKnownGroups(): List<Group> = groups.values.sortedBy { it.name.lowercase() }

    fun groupOf(gid: String): Group? = groups[gid]

    fun isGroupMember(gid: String): Boolean = groups[gid]?.members?.contains(myName) == true

    fun isGroupAdmin(gid: String): Boolean = groups[gid]?.admins?.contains(myName) == true

    /** Certs an admin of [gid] has verified for [subject]. */
    fun verifiedCertsIn(gid: String, subject: String): Set<String> =
        groupVerified[gid]?.get(subject) ?: emptySet()

    /** All groups [name] belongs to (that we know about). */
    fun groupsFor(name: String): List<Group> =
        groups.values.filter { name in it.members }.sortedBy { it.name.lowercase() }

    fun pinsForGroup(gid: String): List<GroupPin> = groupPins[gid]?.toList() ?: emptyList()

    fun verifiedSummaryForGroup(gid: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        groupVerified[gid]?.forEach { (subject, certs) ->
            certs.forEach { cert -> result.add(subject to cert) }
        }
        return result.sortedBy { it.first }
    }

    /** Create a new group; founder becomes admin + first member. */
    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val gid = newId()
        val ts = System.currentTimeMillis()
        val payload = groupCreatePayload(gid, trimmed, myName, ts)
        val sig = SignKeys.sign(payload) ?: return
        applyGroupCreate(gid, trimmed, myName, ts)
        setFeedScope(gid)
        appendLog("Group", "created \"$trimmed\"")
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(groupCreateEnvelope(id, TTL_DEFAULT, gid, trimmed, myName, ts, sig))
    }

    /** Join an existing group (self-join; signed so no one can spoof your membership). */
    fun joinGroup(gid: String) {
        if (groups[gid] == null || isGroupMember(gid)) return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(groupJoinPayload(gid, myName, ts)) ?: return
        applyGroupJoin(gid, myName)
        appendLog("Group", "joined ${groups[gid]?.name ?: gid}", scope = gid)
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(groupJoinEnvelope(id, TTL_DEFAULT, gid, myName, ts, sig))
    }

    /** Post to a group you belong to. */
    fun sendGroupMessage(gid: String, text: String) {
        if (!isGroupMember(gid)) return
        val g = groups[gid] ?: return
        val id = newId()
        markSeen(id)
        appendLog("You", text, scope = gid)
        feedCoordinator(myName, "[${g.name}] $text")
        flood(groupMsgEnvelope(id, TTL_DEFAULT, gid, myName, text))
    }

    /** Admin: pin an announcement at the top of the group feed. */
    fun pinGroupAnnouncement(gid: String, text: String) {
        if (!isGroupAdmin(gid)) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(groupPinPayload(gid, myName, trimmed, ts)) ?: return
        applyGroupPin(gid, myName, trimmed, ts)
        appendLog("📌 Pin", trimmed, scope = gid)
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(groupPinEnvelope(id, TTL_DEFAULT, gid, myName, trimmed, ts, sig))
    }

    /**
     * Admin: verify one of [subject]'s self-declared certs inside [gid].
     * This is amplification only — it does not remove or edit their profile.
     */
    fun verifyCertInGroup(gid: String, subject: String, cert: String) {
        if (!isGroupAdmin(gid)) return
        val trimmed = cert.trim()
        if (trimmed.isEmpty() || subject == myName) return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(groupVerifyPayload(gid, myName, subject, trimmed, ts)) ?: return
        applyGroupVerify(gid, subject, trimmed)
        appendLog("✓ Verified", "$subject's \"$trimmed\"", scope = gid)
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(groupVerifyEnvelope(id, TTL_DEFAULT, gid, myName, subject, trimmed, ts, sig))
    }

    /** Admin: promote [target] to co-admin (founder stays admin). */
    fun promoteGroupAdmin(gid: String, target: String) {
        if (!isGroupAdmin(gid)) return
        val g = groups[gid] ?: return
        if (target !in g.members || target in g.admins) return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(groupAdminPayload(gid, myName, target, ts)) ?: return
        applyGroupAdmin(gid, target)
        appendLog("Admin", "promoted $target", scope = gid)
        callback?.onUpdate()
        val id = newId()
        markSeen(id)
        flood(groupAdminEnvelope(id, TTL_DEFAULT, gid, myName, target, ts, sig))
    }

    private fun applyGroupCreate(gid: String, name: String, founder: String, ts: Long) {
        groups[gid] = Group(
            id = gid,
            name = name,
            founder = founder,
            admins = mutableSetOf(founder),
            members = mutableSetOf(founder),
            createdAt = ts
        )
        saveGroups()
    }

    private fun applyGroupJoin(gid: String, member: String): Boolean {
        val g = groups[gid] ?: return false
        if (!g.members.add(member)) return false
        saveGroups()
        return true
    }

    private fun applyGroupAdmin(gid: String, target: String): Boolean {
        val g = groups[gid] ?: return false
        if (!g.admins.add(target)) return false
        saveGroups()
        return true
    }

    private fun applyGroupPin(gid: String, admin: String, text: String, ts: Long) {
        val list = groupPins.getOrPut(gid) { mutableListOf() }
        list.add(0, GroupPin(admin, text, ts))
        while (list.size > MAX_GROUP_PINS) list.removeAt(list.size - 1)
        saveGroups()
    }

    private fun applyGroupVerify(gid: String, subject: String, cert: String) {
        val bySubject = groupVerified.getOrPut(gid) { HashMap() }
        val set = bySubject.getOrPut(subject) { mutableSetOf() }
        if (!set.add(cert)) return
        saveGroups()
    }

    private fun groupCreatePayload(gid: String, name: String, founder: String, ts: Long) =
        "groupcreate|$gid|$name|$founder|$ts"

    private fun groupJoinPayload(gid: String, member: String, ts: Long) =
        "groupjoin|$gid|$member|$ts"

    private fun groupPinPayload(gid: String, admin: String, text: String, ts: Long) =
        "grouppin|$gid|$admin|$text|$ts"

    private fun groupVerifyPayload(gid: String, admin: String, subject: String, cert: String, ts: Long) =
        "groupverify|$gid|$admin|$subject|$cert|$ts"

    private fun groupAdminPayload(gid: String, admin: String, target: String, ts: Long) =
        "groupadmin|$gid|$admin|$target|$ts"

    private fun handleGroupCreate(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val name = obj.optString("name", "")
        val founder = obj.optString("from", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (gid.isEmpty() || name.isEmpty() || founder.isEmpty() || sig.isEmpty()) return
        if (groups.containsKey(gid)) { relay(obj, ttl); return }
        val verifyKey = peerSignKeys[founder]
        if (verifyKey != null) {
            if (!SignKeys.verify(groupCreatePayload(gid, name, founder, ts), sig, verifyKey)) {
                android.util.Log.w("Group", "Rejected forged group create from $founder")
                return
            }
            applyGroupCreate(gid, name, founder, ts)
            trackPeer(founder)
            appendLog("Group", "\"$name\" created by $founder")
            callback?.onUpdate()
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleGroupJoin(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val member = obj.optString("from", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        if (gid.isEmpty() || member.isEmpty() || sig.isEmpty()) return
        if (groups[gid] == null) {
            relay(obj, ttl)
            return
        }
        val verifyKey = peerSignKeys[member]
        if (verifyKey != null) {
            if (!SignKeys.verify(groupJoinPayload(gid, member, ts), sig, verifyKey)) {
                android.util.Log.w("Group", "Rejected forged group join from $member")
                return
            }
            if (applyGroupJoin(gid, member)) {
                trackPeer(member)
                appendLog("Group", "$member joined", scope = gid)
                callback?.onUpdate()
            }
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleGroupAdmin(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val admin = obj.optString("from", "")
        val target = obj.optString("to", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        val g = groups[gid] ?: return relay(obj, ttl)
        if (admin.isEmpty() || target.isEmpty() || admin !in g.admins || sig.isEmpty()) return
        val verifyKey = peerSignKeys[admin]
        if (verifyKey != null) {
            if (!SignKeys.verify(groupAdminPayload(gid, admin, target, ts), sig, verifyKey)) {
                android.util.Log.w("Group", "Rejected forged group admin from $admin")
                return
            }
            if (target in g.members && applyGroupAdmin(gid, target)) {
                appendLog("Admin", "$admin promoted $target", scope = gid)
                callback?.onUpdate()
            }
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleGroupPin(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val admin = obj.optString("from", "")
        val text = obj.optString("text", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        val g = groups[gid] ?: return relay(obj, ttl)
        if (admin.isEmpty() || text.isEmpty() || admin !in g.admins || sig.isEmpty()) return
        val verifyKey = peerSignKeys[admin]
        if (verifyKey != null) {
            if (!SignKeys.verify(groupPinPayload(gid, admin, text, ts), sig, verifyKey)) {
                android.util.Log.w("Group", "Rejected forged group pin from $admin")
                return
            }
            applyGroupPin(gid, admin, text, ts)
            appendLog("📌 Pin", "$text — $admin", scope = gid)
            callback?.onUpdate()
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleGroupVerify(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val admin = obj.optString("from", "")
        val subject = obj.optString("to", "")
        val cert = obj.optString("cert", "")
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        val g = groups[gid] ?: return relay(obj, ttl)
        if (admin.isEmpty() || subject.isEmpty() || cert.isEmpty() || admin !in g.admins || sig.isEmpty()) return
        val verifyKey = peerSignKeys[admin]
        if (verifyKey != null) {
            if (!SignKeys.verify(groupVerifyPayload(gid, admin, subject, cert, ts), sig, verifyKey)) {
                android.util.Log.w("Group", "Rejected forged group verify from $admin")
                return
            }
            applyGroupVerify(gid, subject, cert)
            appendLog("✓ Verified", "$subject's \"$cert\"", scope = gid)
            callback?.onUpdate()
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    private fun handleGroupMsg(obj: JSONObject, ttl: Int) {
        val gid = obj.optString("gid", "")
        val from = obj.optString("from", "")
        val text = obj.optString("text", "")
        val g = groups[gid] ?: return relay(obj, ttl)
        if (from.isEmpty() || text.isEmpty() || from !in g.members || from == myName) {
            relay(obj, ttl)
            return
        }
        trackPeer(from)
        appendLog(from, text, scope = gid)
        feedCoordinator(from, "[${g.name}] $text")
        relay(obj, ttl)
    }

    // ---- Identity / profile ----

    fun hasProfile(): Boolean = prefs.contains(KEY_MY_NAME)

    fun hasProfilePhoto(): Boolean = ProfilePhoto.hasPhoto(this)

    /** Save a local profile photo; clears vouches until neighbors confirm again. */
    fun saveProfilePhoto(uri: Uri): Boolean {
        if (!ProfilePhoto.saveFromUri(this, uri)) return false
        photoVouchesFor.remove(myName)
        savePhotoState()
        broadcastPhotoThumb()
        callback?.onUpdate()
        return true
    }

    fun clearProfilePhoto() {
        ProfilePhoto.delete(this)
        photoVouchesFor.remove(myName)
        peerPhotoHash.remove(myName)
        savePhotoState()
        callback?.onUpdate()
    }

    fun myProfile(): Profile = Profile(myName, mySkills.toList(), myShares.toList(), myCerts.toList())

    fun profileOf(name: String): Profile? =
        if (name == myName) myProfile() else peerProfiles[name]

    /** Everyone we can show in the directory (self + known neighbors). */
    fun directory(): List<String> {
        val names = sortedSetOf<String>()
        names.add(myName)
        names.addAll(knownPeers)
        names.addAll(peerProfiles.keys)
        return names.toList()
    }

    /** Save my profile from onboarding/edit, then announce it to the mesh. */
    fun saveProfile(name: String, skills: List<String>, shares: List<String>, certs: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) myName = trimmed
        mySkills.clear(); mySkills.addAll(skills.map { it.trim() }.filter { it.isNotEmpty() })
        myShares.clear(); myShares.addAll(shares.map { it.trim() }.filter { it.isNotEmpty() })
        myCerts.clear(); myCerts.addAll(certs.map { it.trim() }.filter { it.isNotEmpty() })
        saveMyProfilePrefs()
        appendLog("You", "updated profile (${myName})")
        feedMyOffers()
        broadcastProfile()
        callback?.onUpdate()
    }

    private fun feedMyOffers() {
        val items = mySkills + myShares
        if (items.isNotEmpty()) feedCoordinator(myName, "offering " + items.joinToString(", "))
    }

    fun broadcastProfile() {
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(profilePayload(myName, ts, mySkills, myShares, myCerts)) ?: return
        val id = newId()
        markSeen(id)
        flood(profileEnvelope(id, TTL_DEFAULT, myName, ts, mySkills, myShares, myCerts, sig))
        broadcastPhotoThumb()
    }

    fun broadcastPhotoThumb() {
        if (!hasProfilePhoto()) return
        val hash = ProfilePhoto.contentHash(this) ?: return
        val thumb = ProfilePhoto.meshThumbnailBytes(this) ?: return
        val ts = System.currentTimeMillis()
        val sig = SignKeys.sign(photoThumbPayload(myName, hash, ts)) ?: return
        val id = newId()
        markSeen(id)
        flood(photoThumbEnvelope(id, TTL_DEFAULT, myName, hash, thumb, ts, sig))
    }

    private fun profilePayload(
        from: String, ts: Long,
        skills: List<String>, shares: List<String>, certs: List<String>
    ): String = "profile|$from|$ts|" +
        skills.joinToString(",") + "|" + shares.joinToString(",") + "|" + certs.joinToString(",")

    private fun handleProfile(obj: JSONObject, ttl: Int) {
        val from = obj.optString("from", "")
        if (from.isEmpty() || from == myName) { relay(obj, ttl); return }
        val ts = obj.optLong("kts", 0L)
        val sig = obj.optString("sig", "")
        val skills = jsonToList(obj.optJSONArray("skills"))
        val shares = jsonToList(obj.optJSONArray("shares"))
        val certs = jsonToList(obj.optJSONArray("certs"))
        val verifyKey = peerSignKeys[from]
        if (verifyKey != null) {
            // Field-binding signature: any tampering by a relay invalidates it.
            if (!SignKeys.verify(profilePayload(from, ts, skills, shares, certs), sig, verifyKey)) {
                android.util.Log.w("Profile", "Rejected tampered/forged profile from $from")
                return
            }
            peerProfiles[from] = Profile(from, skills, shares, certs)
            trackPeer(from)
            savePeerProfiles()
            if (skills.isNotEmpty() || shares.isNotEmpty()) {
                feedCoordinator(from, "offering " + (skills + shares).joinToString(", "))
            }
            appendLog("Profile", "$from shared their profile")
            callback?.onUpdate()
            relay(obj, ttl)
        } else {
            relay(obj, ttl)
        }
    }

    fun sendMessage(text: String, to: String) {
        if (isGroupRecipient(to)) {
            sendGroupMessage(groupIdFromRecipient(to), text)
            return
        }
        val isDirect = to != EVERYONE
        val id = newId()
        markSeen(id)
        if (!isDirect) {
            val scope = publicBroadcastScope()
            feedScope = scope
            prefs.edit().putString(KEY_FEED_SCOPE, feedScope).apply()
            appendLog("You", text, scope = scope)
            feedCoordinator(myName, text)
            flood(broadcastEnvelope(id, TTL_DEFAULT, myName, text, scope, currentGeoSnapshot()))
            return
        }

        // Direct message: seal from/to/text inside a body encrypted with the
        // per-pair X25519 key. Relays see only an opaque blob + id + ttl.
        val peerPub = peerKeys[to]
        val pairKey = peerPub?.let { DeviceKeys.sharedKeyWith(it) }
        if (pairKey != null) {
            val inner = JSONObject().apply {
                put("from", myName)
                put("to", to)
                put("text", text)
                put("ts", System.currentTimeMillis())
            }.toString()
            val body = Base64.encodeToString(Crypto.encryptWithKey(pairKey, inner), Base64.NO_WRAP)
            appendLog("You \u2192 $to \uD83D\uDD10", text, scope = dmScope(to))
            flood(sealedDmEnvelope(id, TTL_DEFAULT, body))
        } else {
            // No key yet for this peer — fall back to a non-private DM.
            appendLog("You \u2192 $to", text, scope = dmScope(to))
            flood(plainDmEnvelope(id, TTL_DEFAULT, myName, to, text))
        }
    }

    private fun sendKeyAnnouncement() {
        val pub = DeviceKeys.myPublicKeyB64 ?: return
        val id = newId()
        markSeen(id)
        flood(keyEnvelope(id, TTL_DEFAULT, myName, pub, SignKeys.myVerifyKeyB64))
    }

    @SuppressLint("MissingPermission")
    fun sendEmergency() {
        val location = lastKnownLocation()
        val payload = if (location != null) {
            "\uD83D\uDEA8 NEED HELP — at ${"%.5f".format(location.first)}, ${"%.5f".format(location.second)}"
        } else {
            "\uD83D\uDEA8 NEED HELP — location unknown"
        }
        val id = newId()
        markSeen(id)
        appendLog("EMERGENCY", payload, scope = publicBroadcastScope(), emergency = true)
        feedCoordinator(myName, payload)
        // Attach the ICE card so responders see vitals the moment the alert lands.
        val scope = publicBroadcastScope()
        val geo = currentGeoSnapshot()
        val envelope = baseEnvelope("broadcast", id, TTL_DEFAULT).apply {
            put("from", myName)
            put("text", payload)
            MessageChannel.attach(this, scope, geo)
            if (!myIce.isBlank()) put("ice", iceToJson(myIce))
        }.toString()
        flood(envelope)
    }

    // ---- Envelope builders ----

    private fun baseEnvelope(type: String, id: String, ttl: Int): JSONObject =
        JSONObject().apply {
            put("v", 1)
            put("type", type)
            put("id", id)
            put("ttl", ttl)
            put("ts", System.currentTimeMillis())
        }

    private fun broadcastEnvelope(
        id: String,
        ttl: Int,
        from: String,
        text: String,
        channel: String = SCOPE_EVERYONE,
        geo: GeoLocator.Snapshot? = null,
    ): String =
        baseEnvelope("broadcast", id, ttl).apply {
            put("from", from)
            put("text", text)
            MessageChannel.attach(this, channel, geo)
        }.toString()

    private fun sealedDmEnvelope(id: String, ttl: Int, body: String): String =
        baseEnvelope("dm", id, ttl).apply {
            put("enc", "x25519")
            put("body", body)
        }.toString()

    private fun plainDmEnvelope(id: String, ttl: Int, from: String, to: String, text: String): String =
        baseEnvelope("dm", id, ttl).apply {
            put("from", from)
            put("to", to)
            put("text", text)
        }.toString()

    private fun keyEnvelope(id: String, ttl: Int, from: String, pub: String, spub: String?): String =
        baseEnvelope("key", id, ttl).apply {
            put("from", from)
            put("pub", pub)
            if (spub != null) put("spub", spub)
        }.toString()

    private fun kudosEnvelope(id: String, ttl: Int, giver: String, helper: String, ts: Long, sig: String): String =
        baseEnvelope("kudos", id, ttl).apply {
            put("from", giver)
            put("to", helper)
            put("kts", ts)
            put("sig", sig)
        }.toString()

    private fun statusEnvelope(id: String, ttl: Int, from: String, cap: String, ts: Long, sig: String): String =
        baseEnvelope("status", id, ttl).apply {
            put("from", from)
            put("cap", cap)
            put("kts", ts)
            put("sig", sig)
        }.toString()

    private fun vouchEnvelope(id: String, ttl: Int, from: String, subject: String, ts: Long, sig: String): String =
        baseEnvelope("vouch", id, ttl).apply {
            put("from", from)
            put("to", subject)
            put("kts", ts)
            put("sig", sig)
        }.toString()

    private fun crewEnvelope(id: String, ttl: Int, from: String, task: String): String =
        baseEnvelope("crew", id, ttl).apply {
            put("from", from)
            put("text", task)
        }.toString()

    private fun crewJoinEnvelope(id: String, ttl: Int, from: String, task: String): String =
        baseEnvelope("crewjoin", id, ttl).apply {
            put("from", from)
            put("text", task)
        }.toString()

    private fun profileEnvelope(
        id: String, ttl: Int, from: String, ts: Long,
        skills: List<String>, shares: List<String>, certs: List<String>, sig: String
    ): String = baseEnvelope("profile", id, ttl).apply {
        put("from", from)
        put("kts", ts)
        put("skills", JSONArray(skills))
        put("shares", JSONArray(shares))
        put("certs", JSONArray(certs))
        put("sig", sig)
    }.toString()

    private fun photoThumbEnvelope(
        id: String, ttl: Int, from: String, hash: String, thumb: ByteArray, ts: Long, sig: String
    ): String = baseEnvelope("photothumb", id, ttl).apply {
        put("from", from)
        put("hash", hash)
        put("kts", ts)
        put("sig", sig)
        put("thumb", Base64.encodeToString(thumb, Base64.NO_WRAP))
    }.toString()

    private fun photoVouchEnvelope(
        id: String, ttl: Int, voucher: String, subject: String, hash: String, ts: Long, sig: String
    ): String = baseEnvelope("photovouch", id, ttl).apply {
        put("from", voucher)
        put("to", subject)
        put("hash", hash)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupCreateEnvelope(
        id: String, ttl: Int, gid: String, name: String, founder: String, ts: Long, sig: String
    ): String = baseEnvelope("groupcreate", id, ttl).apply {
        put("gid", gid)
        put("name", name)
        put("from", founder)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupJoinEnvelope(
        id: String, ttl: Int, gid: String, member: String, ts: Long, sig: String
    ): String = baseEnvelope("groupjoin", id, ttl).apply {
        put("gid", gid)
        put("from", member)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupAdminEnvelope(
        id: String, ttl: Int, gid: String, admin: String, target: String, ts: Long, sig: String
    ): String = baseEnvelope("groupadmin", id, ttl).apply {
        put("gid", gid)
        put("from", admin)
        put("to", target)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupPinEnvelope(
        id: String, ttl: Int, gid: String, admin: String, text: String, ts: Long, sig: String
    ): String = baseEnvelope("grouppin", id, ttl).apply {
        put("gid", gid)
        put("from", admin)
        put("text", text)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupVerifyEnvelope(
        id: String, ttl: Int, gid: String, admin: String, subject: String, cert: String, ts: Long, sig: String
    ): String = baseEnvelope("groupverify", id, ttl).apply {
        put("gid", gid)
        put("from", admin)
        put("to", subject)
        put("cert", cert)
        put("kts", ts)
        put("sig", sig)
    }.toString()

    private fun groupMsgEnvelope(
        id: String, ttl: Int, gid: String, from: String, text: String
    ): String = baseEnvelope("groupmsg", id, ttl).apply {
        put("gid", gid)
        put("from", from)
        put("text", text)
    }.toString()

    // ---- Relay plumbing ----

    private fun newId(): String {
        val b = ByteArray(8)
        rng.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Returns true if this ID is new (and records it); false if already seen. */
    private fun markSeen(id: String): Boolean {
        if (id.isEmpty()) return true
        if (!seenIds.add(id)) return false
        while (seenIds.size > MAX_SEEN_IDS) {
            val it = seenIds.iterator(); it.next(); it.remove()
        }
        return true
    }

    private fun flood(envelope: String) = notifySubscribers(envelope)

    /** Forward an already-parsed envelope one more hop, if it has TTL left. */
    private fun relay(obj: JSONObject, ttl: Int) {
        if (ttl <= 0) return
        obj.put("ttl", ttl - 1)
        flood(obj.toString())
    }

    private fun trackPeer(from: String) {
        if (from == myName || from.isEmpty()) return
        val sizeBefore = knownPeers.size
        knownPeers.add(from)
        if (knownPeers.size != sizeBefore) {
            savePeers()
            callback?.onUpdate()
        }
    }

    private fun handleIncoming(raw: String) {
        val obj = try {
            JSONObject(raw)
        } catch (_: Exception) {
            // Not JSON (legacy/plain) — show it once, can't relay (no id).
            appendLog(DEFAULT_NAME, raw)
            notifyIncoming(getString(R.string.notify_new_message), raw)
            return
        }

        val id = obj.optString("id", "")
        if (!markSeen(id)) return // duplicate echo — drop, do not relay again

        val type = obj.optString("type", "broadcast")
        val ttl = obj.optInt("ttl", 0)

        when (type) {
            "key" -> {
                val from = obj.optString("from", "")
                val pub = obj.optString("pub", "")
                val spub = obj.optString("spub", "")
                if (from != myName && pub.isNotEmpty()) {
                    val isNew = peerKeys.put(from, pub) == null
                    if (spub.isNotEmpty()) peerSignKeys[from] = spub
                    knownPeers.add(from)
                    savePeers()
                    callback?.onUpdate()
                    if (isNew) {
                        sendKeyAnnouncement()
                        if (hasProfile()) broadcastProfile()
                        if (hasProfilePhoto()) broadcastPhotoThumb()
                    }
                }
                relay(obj, ttl)
            }

            "kudos" -> handleKudos(obj, ttl)

            "status" -> handleStatus(obj, ttl)

            "vouch" -> handleVouch(obj, ttl)

            "photothumb" -> handlePhotoThumb(obj, ttl)

            "photovouch" -> handlePhotoVouch(obj, ttl)

            "profile" -> handleProfile(obj, ttl)

            "crew" -> {
                val from = obj.optString("from", DEFAULT_NAME)
                val task = obj.optString("text", "")
                if (from != myName) {
                    trackPeer(from)
                    appendLog("\uD83D\uDCE3 Help call from $from", task)
                    notifyIncoming("\uD83D\uDCE3 Help needed nearby", "$from: $task")
                }
                relay(obj, ttl)
            }

            "crewjoin" -> {
                val from = obj.optString("from", DEFAULT_NAME)
                val task = obj.optString("text", "")
                if (from != myName) {
                    trackPeer(from)
                    appendLog("\u270B $from is in", task)
                }
                relay(obj, ttl)
            }

            "groupcreate" -> handleGroupCreate(obj, ttl)

            "groupjoin" -> handleGroupJoin(obj, ttl)

            "groupadmin" -> handleGroupAdmin(obj, ttl)

            "grouppin" -> handleGroupPin(obj, ttl)

            "groupverify" -> handleGroupVerify(obj, ttl)

            "groupmsg" -> handleGroupMsg(obj, ttl)

            "broadcast" -> {
                val from = obj.optString("from", DEFAULT_NAME)
                val text = obj.optString("text", "")
                if (from != myName) {
                    trackPeer(from)
                    // An emergency may carry the sender's ICE card — store + surface it.
                    obj.optJSONObject("ice")?.let { iceObj ->
                        val ice = iceFromJson(iceObj)
                        if (!ice.isBlank()) {
                            peerIce[from] = ice
                            savePeerIce()
                            appendLog("\uD83E\uDE7A $from medical", iceSummary(ice))
                        }
                    }
                    displayIncoming(
                        isDirect = false,
                        isPrivate = false,
                        from = from,
                        text = text,
                        zoneScope = MessageChannel.channelFromGeo(obj),
                    )
                }
                relay(obj, ttl)
            }

            "dm" -> {
                val enc = obj.optString("enc", "")
                if (enc == "x25519") {
                    // Try to open with each known peer key. If it opens, it was
                    // for us. Either way we ALWAYS relay (so we don't reveal the
                    // recipient by being the one who stops forwarding).
                    val opened = tryOpenSealedDm(obj.optString("body", ""))
                    if (opened != null) {
                        trackPeer(opened.first)
                        displayIncoming(true, isPrivate = true, from = opened.first, text = opened.second)
                    }
                    relay(obj, ttl)
                } else {
                    // Cleartext fallback DM (no key was available at send time).
                    val to = obj.optString("to", "")
                    if (to.equals(myName, ignoreCase = true)) {
                        val from = obj.optString("from", DEFAULT_NAME)
                        trackPeer(from)
                        displayIncoming(true, isPrivate = false, from = from, text = obj.optString("text", ""))
                    }
                    relay(obj, ttl)
                }
            }
        }
    }

    /**
     * Attempts to decrypt a sealed DM body with each known peer's per-pair key.
     * Returns (sender, text) on success, or null if it isn't for us.
     */
    private fun tryOpenSealedDm(body: String): Pair<String, String>? {
        if (body.isEmpty()) return null
        val data = try {
            Base64.decode(body, Base64.NO_WRAP)
        } catch (_: Exception) {
            return null
        }
        for ((name, pub) in peerKeys) {
            val key = DeviceKeys.sharedKeyWith(pub) ?: continue
            val plain = Crypto.decryptWithKey(key, data) ?: continue
            return try {
                val inner = JSONObject(plain)
                Pair(inner.optString("from", name), inner.optString("text", plain))
            } catch (_: Exception) {
                Pair(name, plain)
            }
        }
        return null
    }

    fun coordinatorSummary(): String = Coordinator.summary()

    fun llmActive(): Boolean = LlmEngine.isReady

    private var lastTriageHeadline = ""

    /** Feed PUBLIC messages (broadcasts/emergencies) to the resource matcher. */
    private fun feedCoordinator(from: String, text: String) {
        if (LlmEngine.isReady) {
            // Heavy inference off-thread; LLM classifies, rules are the fallback.
            llmExecutor.execute {
                val changed = LlmEngine.extract(text)?.let { p ->
                    Coordinator.processParsed(from, text, p.intent, p.categories, p.location)
                } ?: Coordinator.process(from, text)
                if (changed) onCoordinatorChanged()
            }
        } else if (Coordinator.process(from, text)) {
            onCoordinatorChanged()
        }
    }

    private fun onCoordinatorChanged() {
        android.util.Log.i("Coordinator", "\n" + Coordinator.summary())
        callback?.onUpdate()
        updateTriageNotification()
    }

    private fun updateTriageNotification() {
        if (!Coordinator.hasContent()) return
        val headline = Coordinator.headline()
        if (headline == lastTriageHeadline) return // nothing meaningful changed
        lastTriageHeadline = headline

        val notification = NotificationCompat.Builder(this, TRIAGE_CHANNEL_ID)
            .setContentTitle("\uD83E\uDDED ${getString(R.string.notify_area_status)}")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(Coordinator.summary()))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(TRIAGE_NOTIFICATION_ID, notification)
    }

    private fun displayIncoming(
        isDirect: Boolean,
        isPrivate: Boolean,
        from: String,
        text: String,
        zoneScope: String = SCOPE_EVERYONE,
    ) {
        val isEmergency = text.contains("NEED HELP", ignoreCase = true)
        if (!isDirect) feedCoordinator(from, text)
        val label = when {
            isDirect && isPrivate -> "DM from $from \uD83D\uDD10"
            isDirect -> "DM from $from"
            else -> from
        }
        val scope = if (isDirect) dmScope(from) else zoneScope
        appendLog(label, text, scope = scope, emergency = isEmergency)
        val title = when {
            isEmergency -> getString(R.string.notify_emergency)
            isDirect -> getString(R.string.notify_direct_message, from)
            else -> getString(R.string.notify_new_message)
        }
        notifyIncoming(title, text)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Pair<Double, Double>? {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) return Pair(loc.latitude, loc.longitude)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun notifySubscribers(plaintextEnvelope: String) {
        val bytes = Crypto.encrypt(plaintextEnvelope)
        // Push over the other parallel transports (WiFi Direct + LAN).
        wifiDirect?.send(bytes)
        lan?.send(bytes)
        val characteristic = messageCharacteristic ?: return
        val server = bluetoothGattServer ?: return
        if (subscribers.isEmpty()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                for (device in subscribers) {
                    server.notifyCharacteristicChanged(device, characteristic, false, bytes)
                }
            } else {
                characteristic.value = bytes
                for (device in subscribers) {
                    server.notifyCharacteristicChanged(device, characteristic, false)
                }
            }
        } catch (_: SecurityException) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            wifiDirect?.stop()
        } catch (_: Exception) {
        }
        try {
            lan?.stop()
        } catch (_: Exception) {
        }
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
            advertiseCallback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
            bluetoothGattServer?.close()
        } catch (_: SecurityException) {
        }
    }
}
