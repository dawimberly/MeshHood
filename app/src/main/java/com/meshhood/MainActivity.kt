package com.meshhood

import android.Manifest
import android.graphics.Typeface
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class MainActivity : AppCompatActivity(), MeshService.MeshCallback {

    private lateinit var statusText: TextView
    private lateinit var userNameText: TextView
    private lateinit var feedTitleText: TextView
    private lateinit var areaPickerRow: View
    private lateinit var areaPickerLabel: TextView
    private lateinit var feedBackButton: ImageButton
    private lateinit var chatsIconButton: ImageButton
    private lateinit var feedList: RecyclerView
    private lateinit var feedEmptyText: TextView
    private lateinit var feedPostCountText: TextView
    private lateinit var feedSortRow: View
    private lateinit var feedSortRecentButton: MaterialButton
    private lateinit var feedSortNearbyButton: MaterialButton
    private val feedAdapter = FeedAdapter()
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var recipientButton: Button
    private lateinit var coordinatorButton: Button
    private lateinit var menuButton: ImageButton
    private lateinit var transportStrip: View
    private lateinit var signalBars: List<View>
    private lateinit var meshNetworkLabel: TextView
    private lateinit var networkReadinessDot: View
    private lateinit var networkReadinessLabel: TextView
    private lateinit var networkReadinessHelp: ImageButton
    private lateinit var neighborCountText: TextView
    private lateinit var bottomNavHome: View
    private lateinit var bottomNavNearby: View
    private lateinit var bottomNavResources: View
    private lateinit var bottomNavAlert: View
    private lateinit var bottomNavHomeIconWrap: View
    private lateinit var bottomNavNearbyIconWrap: View
    private lateinit var bottomNavResourcesIconWrap: View
    private lateinit var bottomNavAlertIconWrap: View
    private lateinit var bottomNavHomeIcon: ImageView
    private lateinit var bottomNavHomeLabel: TextView
    private lateinit var bottomNavNearbyIcon: ImageView
    private lateinit var bottomNavNearbyLabel: TextView
    private lateinit var bottomNavResourcesIcon: ImageView
    private lateinit var bottomNavResourcesLabel: TextView
    private lateinit var bottomNavAlertIcon: ImageView
    private lateinit var bottomNavAlertLabel: TextView
    private lateinit var profileAvatarButton: FrameLayout
    private lateinit var profileAvatarImage: ImageView
    private lateinit var profileAvatarInitial: TextView
    private lateinit var profileAvatarVerifiedBadge: ImageView
    private lateinit var composeAvatarImage: ImageView
    private lateinit var composeAvatarInitial: TextView
    private lateinit var dmPeerAvatarButton: FrameLayout
    private lateinit var dmPeerAvatarImage: ImageView
    private lateinit var dmPeerAvatarInitial: TextView
    private lateinit var dmPeerAvatarVerifiedBadge: ImageView
    private var dmPeerAvatarClickName: String? = null

    private var currentRecipient = MeshService.EVERYONE

    private var meshService: MeshService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MeshService.LocalBinder
            meshService = localBinder.service
            bound = true
            meshService?.setCallback(this@MainActivity)
            refreshUi()
            maybePromptOnboarding()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            meshService = null
        }
    }

    private val optionalPermissions = setOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.SEND_SMS,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val critical = requiredPermissions().filter { it !in optionalPermissions }
        val locationOk = hasLocationPermission()
        val othersOk = critical
            .filter {
                it != Manifest.permission.ACCESS_FINE_LOCATION &&
                    it != Manifest.permission.ACCESS_COARSE_LOCATION
            }
            .all { results[it] == true }
        if (othersOk && locationOk) {
            startAndBindService()
        } else {
            statusText.text = getString(R.string.permissions_denied)
            Toast.makeText(this, R.string.permissions_bluetooth_required, Toast.LENGTH_LONG).show()
        }
    }

    private var dialogPhotoRefresh: (() -> Unit)? = null

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            dialogPhotoRefresh = null
            return@registerForActivityResult
        }
        val service = meshService
        if (service == null || !service.saveProfilePhoto(uri)) {
            Toast.makeText(this, R.string.profile_photo_failed, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.profile_photo_saved, Toast.LENGTH_SHORT).show()
            refreshAvatar()
            dialogPhotoRefresh?.invoke()
        }
        dialogPhotoRefresh = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        userNameText = findViewById(R.id.userNameText)
        feedTitleText = findViewById(R.id.feedTitleText)
        areaPickerRow = findViewById(R.id.areaPickerRow)
        areaPickerLabel = findViewById(R.id.areaPickerLabel)
        feedBackButton = findViewById(R.id.feedBackButton)
        chatsIconButton = findViewById(R.id.chatsIconButton)
        feedList = findViewById(R.id.feedList)
        feedEmptyText = findViewById(R.id.feedEmptyText)
        feedPostCountText = findViewById(R.id.feedPostCountText)
        feedSortRow = findViewById(R.id.feedSortRow)
        feedSortRecentButton = findViewById(R.id.feedSortRecentButton)
        feedSortNearbyButton = findViewById(R.id.feedSortNearbyButton)
        feedList.layoutManager = LinearLayoutManager(this)
        feedList.adapter = feedAdapter
        feedAdapter.onLineLongClick = { line ->
            if (line.hasMapCoords()) {
                showFeedLocationActions(line)
                true
            } else {
                false
            }
        }
        feedSortRecentButton.setOnClickListener { onFeedSortSelected(FeedSort.RECENT) }
        feedSortNearbyButton.setOnClickListener { onFeedSortSelected(FeedSort.NEARBY) }
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        recipientButton = findViewById(R.id.recipientButton)
        coordinatorButton = findViewById(R.id.coordinatorButton)
        menuButton = findViewById(R.id.menuButton)
        transportStrip = findViewById(R.id.transportStrip)
        signalBars = listOf(
            findViewById(R.id.signalBar1),
            findViewById(R.id.signalBar2),
            findViewById(R.id.signalBar3),
            findViewById(R.id.signalBar4),
            findViewById(R.id.signalBar5),
        )
        meshNetworkLabel = findViewById(R.id.meshNetworkLabel)
        networkReadinessDot = findViewById(R.id.networkReadinessDot)
        networkReadinessLabel = findViewById(R.id.networkReadinessLabel)
        networkReadinessHelp = findViewById(R.id.networkReadinessHelp)
        neighborCountText = findViewById(R.id.neighborCountText)
        bindTransportStripPlaceholder()
        bottomNavHome = findViewById(R.id.bottomNavHome)
        bottomNavNearby = findViewById(R.id.bottomNavNearby)
        bottomNavResources = findViewById(R.id.bottomNavResources)
        bottomNavAlert = findViewById(R.id.bottomNavAlert)
        bottomNavHomeIconWrap = findViewById(R.id.bottomNavHomeIconWrap)
        bottomNavNearbyIconWrap = findViewById(R.id.bottomNavNearbyIconWrap)
        bottomNavResourcesIconWrap = findViewById(R.id.bottomNavResourcesIconWrap)
        bottomNavAlertIconWrap = findViewById(R.id.bottomNavAlertIconWrap)
        bottomNavHomeIcon = findViewById(R.id.bottomNavHomeIcon)
        bottomNavHomeLabel = findViewById(R.id.bottomNavHomeLabel)
        bottomNavNearbyIcon = findViewById(R.id.bottomNavNearbyIcon)
        bottomNavNearbyLabel = findViewById(R.id.bottomNavNearbyLabel)
        bottomNavResourcesIcon = findViewById(R.id.bottomNavResourcesIcon)
        bottomNavResourcesLabel = findViewById(R.id.bottomNavResourcesLabel)
        bottomNavAlertIcon = findViewById(R.id.bottomNavAlertIcon)
        bottomNavAlertLabel = findViewById(R.id.bottomNavAlertLabel)
        profileAvatarButton = findViewById(R.id.profileAvatarButton)
        profileAvatarImage = findViewById(R.id.profileAvatarImage)
        profileAvatarInitial = findViewById(R.id.profileAvatarInitial)
        profileAvatarVerifiedBadge = findViewById(R.id.profileAvatarVerifiedBadge)
        composeAvatarImage = findViewById(R.id.composeAvatarImage)
        composeAvatarInitial = findViewById(R.id.composeAvatarInitial)
        dmPeerAvatarButton = findViewById(R.id.dmPeerAvatarButton)
        dmPeerAvatarImage = findViewById(R.id.dmPeerAvatarImage)
        dmPeerAvatarInitial = findViewById(R.id.dmPeerAvatarInitial)
        dmPeerAvatarVerifiedBadge = findViewById(R.id.dmPeerAvatarVerifiedBadge)
        dmPeerAvatarImage.clipToOutline = true
        dmPeerAvatarImage.outlineProvider = ViewOutlineProvider.BACKGROUND
        dmPeerAvatarButton.setOnClickListener {
            dmPeerAvatarClickName?.let { showProfileDetail(it) }
        }
        profileAvatarImage.clipToOutline = true
        profileAvatarImage.outlineProvider = ViewOutlineProvider.BACKGROUND

        profileAvatarButton.setOnClickListener {
            val service = meshService ?: return@setOnClickListener
            showProfileDialog(onboarding = !service.hasProfile())
        }

        sendButton.setOnClickListener { onSendClicked() }
        recipientButton.setOnClickListener { onRecipientClicked() }
        coordinatorButton.setOnClickListener { onCoordinatorClicked() }
        menuButton.setOnClickListener { onFeedLongPress() }
        feedBackButton.setOnClickListener { returnToArea() }
        val openAreaPicker = View.OnClickListener { onAreaPickerClicked() }
        areaPickerRow.setOnClickListener(openAreaPicker)
        areaPickerLabel.setOnClickListener(openAreaPicker)
        chatsIconButton.setOnClickListener { onChatsClicked() }
        val focusComposer = {
            inputField.requestFocus()
            scrollFeedToBottom()
        }
        findViewById<View>(R.id.feedPanel).setOnClickListener { focusComposer() }
        feedList.setOnClickListener { focusComposer() }
        bottomNavHome.setOnClickListener { onHomeTabClicked() }
        bottomNavNearby.setOnClickListener {
            selectNavTab(NavTab.NEARBY)
            startActivity(Intent(this, MapActivity::class.java))
        }
        bottomNavResources.setOnClickListener {
            selectNavTab(NavTab.RESOURCES)
            onCoordinatorClicked()
        }
        bottomNavAlert.setOnClickListener {
            selectNavTab(NavTab.ALERT)
            onAlertTabClicked()
        }
        selectNavTab(NavTab.HOME)
        feedList.setOnLongClickListener { onFeedLongPress(); true }
        findViewById<View>(R.id.feedPanel).setOnLongClickListener { onFeedLongPress(); true }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val service = meshService
                if (service != null && service.isDmScope(service.feedScope)) {
                    returnToArea()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestPermissionsAndStart()
    }

    private fun requiredPermissions(): Array<String> {
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun criticalPermissionsGranted(): Boolean {
        val nonLocation = requiredPermissions()
            .filter { it !in optionalPermissions }
            .filter {
                it != Manifest.permission.ACCESS_FINE_LOCATION &&
                    it != Manifest.permission.ACCESS_COARSE_LOCATION
            }
        return nonLocation.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } && hasLocationPermission()
    }

    private fun requestPermissionsAndStart() {
        if (criticalPermissionsGranted()) {
            startAndBindService()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, MeshService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun onSendClicked() {
        val service = meshService ?: return
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        // Area feed = public send. Avoid stale DM recipient hiding posts from view.
        if (!service.isDmScope(service.feedScope)) {
            currentRecipient = MeshService.EVERYONE
            updateRecipientButton()
        }
        service.sendMessage(text, currentRecipient)
        if (currentRecipient != MeshService.EVERYONE) {
            syncFeedScopeToRecipient()
        }
        inputField.text.clear()
    }

    private fun onAreaPickerClicked() {
        val service = meshService
        if (service == null) {
            Toast.makeText(this, R.string.status_connecting, Toast.LENGTH_SHORT).show()
            return
        }
        if (!service.hasLocalArea()) {
            showQuickAreaDialog(onDone = { showAreaPickerList() })
            return
        }
        showAreaPickerList()
    }

    private fun showAreaPickerList() {
        val service = meshService ?: return
        val options = service.feedScopeOptions()
        if (options.isEmpty()) return
        val scopes = options.map { it.first }
        val selectedBullet = getString(R.string.area_picker_selected)
        val labels = options.map { (scope, label) ->
            val mark = if (scope == service.feedScope) selectedBullet else "   "
            val name = when {
                scope == MeshService.SCOPE_EVERYONE -> label
                MeshZone.isZoneScope(scope) -> label
                else -> getString(R.string.area_picker_group, label)
            }
            mark + name
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.area_picker_title))
            .setItems(labels) { _, which ->
                onFeedScopeSelected(scopes[which])
            }
            .show()
    }

    /** Compact state + ZIP setup - unlocks the geographic levels in Area. */
    private fun showQuickAreaDialog(onDone: (() -> Unit)? = null) {
        val service = meshService ?: return
        val zone = service.getMyZone()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.quick_area_message)
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        })
        val stateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                UsStates.labels(),
            )
            setSelection(UsStates.indexOf(zone.state))
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.zone_state_label)
            textSize = 13f
            setPadding(0, 0, 0, dp(4))
        })
        container.addView(stateSpinner)
        val liveZipText = TextView(this).apply {
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        container.addView(liveZipText)
        service.refreshLiveGeoAsync()
        liveZipText.text = liveZipLabel(service.livePostal())
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.quick_area_title))
            .setView(container)
            .setPositiveButton(R.string.quick_area_save, null)
            .setNegativeButton(R.string.quick_area_skip) { _, _ -> onDone?.invoke() }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val state = UsStates.abbrAt(stateSpinner.selectedItemPosition)
                        if (state.isEmpty() && service.livePostal().isEmpty()) {
                            Toast.makeText(this, R.string.quick_area_required, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        service.saveMyZone(
                            zone.copy(
                                nation = zone.nation.ifEmpty { "US" },
                                state = state,
                            )
                        )
                        Toast.makeText(this, R.string.quick_area_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        refreshUi()
                        onDone?.invoke()
                    }
                }
                dialog.show()
            }
    }

    private fun onChatsClicked() {
        val service = meshService ?: return
        val sheet = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_chats, null)
        val emptyText = content.findViewById<TextView>(R.id.chatsEmptyText)
        val list = content.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conversationList)
        val conversations = service.dmConversations()
        if (conversations.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            list.visibility = View.VISIBLE
            list.layoutManager = LinearLayoutManager(this)
            list.adapter = ConversationAdapter(
                service,
                conversations,
                onClick = { conv ->
                    sheet.dismiss()
                    openDmConversation(conv.peer)
                },
                onAvatarClick = { peer ->
                    sheet.dismiss()
                    showProfileDetail(peer)
                },
            )
        }
        sheet.setContentView(content)
        sheet.show()
    }

    private fun openDmConversation(peer: String) {
        val service = meshService ?: return
        currentRecipient = peer
        service.setFeedScope(service.dmScope(peer))
        updateRecipientButton()
        refreshUi()
    }

    private fun returnToArea() {
        val service = meshService ?: return
        currentRecipient = MeshService.EVERYONE
        service.setFeedScope(service.defaultAreaScope())
        updateRecipientButton()
        refreshUi()
    }

    private fun onHomeTabClicked() {
        returnToArea()
        selectNavTab(NavTab.HOME)
        scrollFeedToTop()
    }

    private fun onRecipientClicked() {
        val service = meshService ?: return
        val peers = service.getPeers()
        val myGroups = service.myGroups()
        val groupEntries = myGroups.map { g -> service.groupRecipient(g.id) to g.name }
        val names = listOf(MeshService.EVERYONE) + groupEntries.map { it.first } + peers
        val labels = listOf(MeshService.EVERYONE) +
            groupEntries.map { it.second } +
            peers.map { peerLabel(it) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.recipient_picker_title))
            .setItems(labels.toTypedArray()) { _, which ->
                currentRecipient = names[which]
                syncFeedScopeToRecipient()
                updateRecipientButton()
            }
            .show()
    }

    private fun syncFeedScopeToRecipient() {
        val service = meshService ?: return
        when {
            service.isGroupRecipient(currentRecipient) ->
                service.setFeedScope(service.groupIdFromRecipient(currentRecipient))
            currentRecipient == MeshService.EVERYONE ->
                service.setFeedScope(service.defaultAreaScope())
            else ->
                service.setFeedScope(service.dmScope(currentRecipient))
        }
    }

    private fun recipientLabel(): String {
        val service = meshService ?: return currentRecipient
        if (MeshService.EVERYONE == currentRecipient) return currentRecipient
        if (service.isGroupRecipient(currentRecipient)) {
            return service.groupOf(service.groupIdFromRecipient(currentRecipient))?.name
                ?: getString(R.string.recipient_group_fallback)
        }
        return currentRecipient
    }

    private fun updateRecipientButton() {
        val label = recipientLabel()
        recipientButton.text = if (label == MeshService.EVERYONE) {
            getString(R.string.recipient_default)
        } else {
            label
        }
    }

    private fun peerLabel(name: String): String {
        val service = meshService ?: return name
        val r = service.reciprocityOf(name)
        if (r.given == 0 && r.received == 0) return name
        val stars = if (r.given > 0) "  ${"*".repeat(r.given.coerceAtMost(5))}${if (r.given > 5) "+" else ""}" else ""
        return "$name$stars  ${r.glyph}"
    }

    private fun onFeedLongPress() {
        val options = arrayOf(
            getString(R.string.menu_send_thanks),
            getString(R.string.menu_call_help),
            getString(R.string.menu_directory),
            getString(R.string.menu_my_groups),
            getString(R.string.menu_create_group),
            getString(R.string.menu_thanks_board),
            getString(R.string.menu_my_capacity),
            getString(R.string.menu_set_area),
            getString(R.string.menu_map),
            getString(R.string.menu_offline_maps_guide),
            getString(R.string.menu_edit_profile),
            getString(R.string.menu_emergency_card),
            getString(R.string.menu_vouch),
            getString(R.string.menu_vouch_photo),
            getString(R.string.menu_clear_feed),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onThankNeighbor()
                    1 -> onCallForHelp()
                    2 -> onDirectory()
                    3 -> onMyGroups()
                    4 -> onCreateGroup()
                    5 -> onGoodNeighborsBoard()
                    6 -> onMyStatus()
                    7 -> showQuickAreaDialog()
                    8 -> onMap()
                    9 -> MapsHelper.openOfflineMapsGuide(this)
                    10 -> showProfileDialog(onboarding = false)
                    11 -> onEmergencyCard()
                    12 -> onVouch()
                    13 -> onVouchPhoto()
                    14 -> onClearFeedRequested()
                }
            }
            .show()
    }

    private fun onMap() {
        startActivity(Intent(this, MapActivity::class.java))
    }

    private fun onCallForHelp() {
        val service = meshService ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.call_help_hint)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_call_help))
            .setView(input)
            .setPositiveButton(R.string.action_send) { _, _ ->
                val task = input.text.toString().trim()
                if (task.isNotEmpty()) service.callForHelp(task)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onMyStatus() {
        val service = meshService ?: return
        val labels = arrayOf(
            getString(R.string.capacity_full),
            getString(R.string.capacity_limited),
            getString(R.string.capacity_homebound),
        )
        val values = arrayOf(MeshService.CAP_FULL, MeshService.CAP_LIMITED, MeshService.CAP_HOMEBOUND)
        val current = service.capacityOf(service.myName)
        val checked = values.indexOf(current).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.capacity_title))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                service.setMyStatus(values[which])
                dialog.dismiss()
                if (values[which] != MeshService.CAP_FULL) {
                    Toast.makeText(this, getString(R.string.toast_vouch_confirm), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun onVouch() {
        val service = meshService ?: return
        // Offer neighbors who have declared a limited/homebound status.
        val candidates = service.getPeers().filter {
            service.capacityOf(it) != MeshService.CAP_FULL
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_limited_status), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = candidates.map {
            val cap = if (service.capacityOf(it) == MeshService.CAP_HOMEBOUND) {
                getString(R.string.capacity_label_homebound)
            } else {
                getString(R.string.capacity_label_limited)
            }
            "$it - $cap (${service.vouchCountFor(it)} vouches)"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_vouch_status))
            .setItems(labels) { _, which ->
                service.vouchFor(candidates[which])
                Toast.makeText(this, getString(R.string.toast_vouched_for, candidates[which]), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun onVouchPhoto() {
        val service = meshService ?: return
        val candidates = service.getPeers().filter { service.hasPhotoFor(it) }
        if (candidates.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_photos_to_vouch), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = candidates.map { name ->
            val verified = if (service.isPhotoVerified(name)) " (verified)" else ""
            val count = service.photoVouchCountFor(name)
            "$name$verified ($count/2 vouches)"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_vouch_photo))
            .setItems(labels) { _, which ->
                val name = candidates[which]
                service.vouchProfilePhoto(name)
                Toast.makeText(
                    this,
                    getString(R.string.profile_photo_vouch_done, name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .show()
    }

    private fun onGoodNeighborsBoard() {
        val service = meshService ?: return
        val names = service.ratedNames()
        if (names.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_reputation, Toast.LENGTH_SHORT).show()
            return
        }
        val ranked = names
            .map { it to service.reciprocityOf(it) }
            .sortedWith(compareByDescending<Pair<String, MeshService.Reciprocity>> { it.second.given }
                .thenByDescending { it.second.received }
                .thenBy { it.first })
        val board = ranked.joinToString("\n") { (name, r) ->
            "${r.glyph} $name - helped ${r.given}, received ${r.received}  - ${r.label}"
        }
        val nudges = names.mapNotNull { service.opportunityNudge(it) }
        val body = if (nudges.isEmpty()) board
        else board + "\n\n- Opportunities -\n" + nudges.joinToString("\n") { "- $it" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_thanks_board))
            .setMessage(body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun onFeedSortSelected(mode: FeedSort) {
        val service = meshService ?: return
        if (service.feedSort() == mode) return
        service.setFeedSort(mode)
        updateFeedSortUi(mode)
        refreshUi()
    }

    private fun updateFeedSortUi(mode: FeedSort) {
        listOf(
            feedSortRecentButton to FeedSort.RECENT,
            feedSortNearbyButton to FeedSort.NEARBY,
        ).forEach { (button, sort) ->
            val selected = mode == sort
            button.backgroundTintList = ColorStateList.valueOf(
                getColor(if (selected) R.color.mesh_nav_selected else R.color.mesh_surface),
            )
            button.strokeColor = ColorStateList.valueOf(
                getColor(if (selected) R.color.mesh_teal else R.color.mesh_stroke_subtle),
            )
            button.setTextColor(
                getColor(if (selected) R.color.mesh_teal_light else R.color.mesh_on_surface),
            )
        }
    }

    private fun showFeedLocationActions(line: FeedLine) {
        val lat = line.mapLat ?: return
        val lon = line.mapLon ?: return
        val label = line.sender.ifBlank { getString(R.string.feed_location_actions_title) }
        val options = arrayOf(
            getString(R.string.map_open_system),
            getString(R.string.map_navigate_here),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.map_pin_actions_title, label))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> MapsHelper.openInGoogleMaps(this, lat, lon, label)
                    1 -> MapsHelper.navigateTo(this, lat, lon)
                }
            }
            .show()
    }

    private fun onThankNeighbor() {
        val service = meshService ?: return
        val peers = service.getPeers()
        if (peers.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_contacts), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = peers.map { peerLabel(it) }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_send_thanks))
            .setItems(labels) { _, which ->
                service.thank(peers[which])
                Toast.makeText(this, getString(R.string.toast_thanked, peers[which]), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---- Identity / profile / directory ----

    private val skillOptions = listOf(
        "Medical / first aid", "Nurse / EMT", "Electrician", "Plumber",
        "Carpentry / repair", "Cooking / food", "Water / purification",
        "Power / generator", "Transport / driving", "Childcare", "Elder care",
        "Ham radio", "Search & rescue", "Translation"
    )

    private fun maybePromptOnboarding() {
        val service = meshService ?: return
        if (!service.hasProfile()) showProfileDialog(onboarding = true)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun liveZipLabel(postal: String): String =
        if (postal.isNotEmpty()) getString(R.string.live_zip_value, postal)
        else getString(R.string.live_zip_pending)

    private fun launchPhotoPicker(onSaved: (() -> Unit)? = null) {
        dialogPhotoRefresh = onSaved
        pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun bindProfileAvatar(
        imageView: ImageView,
        initialView: TextView,
        badgeView: ImageView?,
        displayName: String,
    ) {
        AvatarBinder.bind(this, meshService, displayName, imageView, initialView, badgeView)
    }

    private fun refreshAvatar() {
        val service = meshService
        val name = when {
            service != null && service.hasProfile() -> service.myName
            else -> "?"
        }
        bindProfileAvatar(
            profileAvatarImage,
            profileAvatarInitial,
            profileAvatarVerifiedBadge,
            name,
        )
        bindProfileAvatar(
            composeAvatarImage,
            composeAvatarInitial,
            null,
            name,
        )
    }

    private fun showProfileDialog(onboarding: Boolean) {
        val service = meshService ?: return
        val profile = service.myProfile()
        val zone = service.getMyZone()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            setPadding(0, dp(12), 0, dp(4))
            textSize = 13f
        }

        val nameField = EditText(this).apply {
            hint = getString(R.string.profile_name_hint)
            setText(if (onboarding) "" else profile.name)
        }

        val photoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val previewSize = dp(56)
        val previewFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(previewSize, previewSize)
            setBackgroundResource(R.drawable.avatar_stroke)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val dialogAvatarImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundResource(R.drawable.avatar_circle)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val dialogAvatarInitial = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundResource(R.drawable.avatar_circle)
            gravity = android.view.Gravity.CENTER
            setTextColor(getColor(R.color.mesh_teal_light))
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val dialogVerifiedBadge = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            }
            setImageResource(R.drawable.ic_verified_badge)
            contentDescription = getString(R.string.profile_photo_verified_cd)
            visibility = View.GONE
        }
        previewFrame.addView(dialogAvatarImage)
        previewFrame.addView(dialogAvatarInitial)
        previewFrame.addView(dialogVerifiedBadge)
        photoRow.addView(previewFrame)

        val photoActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = dp(12) }
        }
        val photoStatus = TextView(this).apply {
            textSize = 12f
        }
        val changePhotoBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (service.hasProfilePhoto()) {
                getString(R.string.profile_photo_change)
            } else {
                getString(R.string.profile_photo_add)
            }
            isAllCaps = false
        }
        val removePhotoBtn = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.profile_photo_remove)
            isAllCaps = false
            visibility = if (service.hasProfilePhoto()) View.VISIBLE else View.GONE
        }
        fun refreshDialogAvatar(nameHint: String = nameField.text.toString()) {
            bindProfileAvatar(dialogAvatarImage, dialogAvatarInitial, dialogVerifiedBadge, nameHint)
            photoStatus.text = photoStatusLine(service, service.myName)
            changePhotoBtn.text = if (service.hasProfilePhoto()) {
                getString(R.string.profile_photo_change)
            } else {
                getString(R.string.profile_photo_add)
            }
            removePhotoBtn.visibility = if (service.hasProfilePhoto()) View.VISIBLE else View.GONE
        }
        changePhotoBtn.setOnClickListener {
            launchPhotoPicker { refreshDialogAvatar(nameField.text.toString()) }
        }
        removePhotoBtn.setOnClickListener {
            service.clearProfilePhoto()
            refreshDialogAvatar(nameField.text.toString())
            refreshAvatar()
        }
        photoActions.addView(photoStatus)
        photoActions.addView(changePhotoBtn)
        photoActions.addView(removePhotoBtn)
        photoActions.addView(TextView(this).apply {
            text = getString(R.string.profile_photo_verify_hint)
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
        })
        photoRow.addView(photoActions)
        container.addView(photoRow)

        refreshDialogAvatar(if (onboarding) "" else profile.name)
        nameField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                refreshDialogAvatar(s?.toString() ?: "")
            }
        })
        container.addView(label(getString(R.string.profile_label_name)))
        container.addView(nameField)

        container.addView(label(getString(R.string.location_share_label)))
        container.addView(TextView(this).apply {
            text = getString(R.string.location_share_profile_hint)
            textSize = 12f
            setPadding(0, 0, 0, dp(4))
        })

        container.addView(label(getString(R.string.gateway_mode_label)))
        val gatewayCheck = CheckBox(this).apply {
            isChecked = service.isGatewayMode()
            text = getString(R.string.gateway_mode_hint)
            textSize = 12f
            setOnCheckedChangeListener { _, checked ->
                if (service.isGatewayMode() != checked) {
                    service.setGatewayMode(checked)
                    Toast.makeText(
                        this@MainActivity,
                        if (checked) R.string.gateway_mode_on_toast else R.string.gateway_mode_off_toast,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        container.addView(gatewayCheck)

        container.addView(label(getString(R.string.profile_area_heading)))
        container.addView(TextView(this).apply {
            text = getString(R.string.profile_area_hint)
            textSize = 11f
            setPadding(0, 0, 0, dp(4))
        })

        fun zoneField(hint: String, value: String) = EditText(this).apply {
            this.hint = hint
            setText(value)
        }

        val nationField = zoneField(getString(R.string.zone_nation_hint), zone.nation.ifEmpty { "US" })
        val nationalField = zoneField(getString(R.string.zone_national_hint), zone.nationalRegion)
        val stateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                UsStates.labels(),
            )
            setSelection(UsStates.indexOf(zone.state))
        }
        val regionField = zoneField(getString(R.string.zone_region_hint), zone.region)
        val localField = zoneField(getString(R.string.zone_local_hint), zone.local)

        container.addView(label(getString(R.string.zone_state_label)))
        container.addView(stateSpinner)
        val liveZipText = TextView(this).apply {
            text = liveZipLabel(service.livePostal())
            textSize = 12f
            setPadding(0, dp(2), 0, dp(8))
        }
        container.addView(liveZipText)
        service.refreshLiveGeoAsync()
        container.addView(label(getString(R.string.zone_nation_label)))
        container.addView(nationField)
        container.addView(label(getString(R.string.zone_national_label)))
        container.addView(nationalField)
        container.addView(label(getString(R.string.zone_region_label)))
        container.addView(regionField)
        container.addView(label(getString(R.string.zone_local_label)))
        container.addView(localField)

        // Blood type lives on the (private) Emergency Card, but we surface the
        // picker here too since people expect it during profile setup.
        val ice = service.getMyIce()
        container.addView(label(getString(R.string.profile_label_blood_type)))
        val bloodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bloodTypes)
            setSelection(bloodTypes.indexOf(ice.bloodType).coerceAtLeast(0))
        }
        container.addView(bloodSpinner)
        container.addView(TextView(this).apply {
            text = getString(R.string.profile_blood_private_hint)
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })

        container.addView(label(getString(R.string.profile_label_skills)))
        val skillBoxes = skillOptions.map { skill ->
            CheckBox(this).apply {
                text = skill
                isChecked = profile.skills.contains(skill)
            }.also { container.addView(it) }
        }

        val sharesField = EditText(this).apply {
            hint = getString(R.string.profile_shares_hint)
            setText(profile.shares.joinToString(", "))
        }
        container.addView(label(getString(R.string.profile_label_shares)))
        container.addView(sharesField)

        val certsField = EditText(this).apply {
            hint = getString(R.string.profile_certs_hint)
            setText(profile.certs.joinToString(", "))
        }
        container.addView(label(getString(R.string.toast_certs_note)))
        container.addView(certsField)

        val scroll = ScrollView(this).apply { addView(container) }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(
                if (onboarding) getString(R.string.profile_title_onboarding)
                else getString(R.string.profile_title_edit),
            )
            .setView(scroll)
            .setPositiveButton(R.string.action_save, null)
        if (onboarding) {
            builder.setNegativeButton(R.string.quick_area_skip, null)
            builder.setCancelable(false)
        } else {
            builder.setNegativeButton(R.string.action_cancel, null)
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.profile_name_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val skills = skillBoxes.filter { it.isChecked }.map { it.text.toString() }
                val shares = sharesField.text.toString().split(",")
                val certs = certsField.text.toString().split(",")
                service.saveProfile(name, skills, shares, certs)
                val savedZone = MeshZone(
                    nation = nationField.text.toString().trim().ifEmpty { "US" },
                    nationalRegion = nationalField.text.toString().trim(),
                    state = UsStates.abbrAt(stateSpinner.selectedItemPosition),
                    region = regionField.text.toString().trim(),
                    local = localField.text.toString().trim(),
                )
                service.saveMyZone(savedZone)
                // Persist blood type onto the private Emergency Card, keeping its
                // other fields untouched.
                val bt = bloodTypes[bloodSpinner.selectedItemPosition].let { if (it == "Unknown") "" else it }
                service.saveIce(service.getMyIce().copy(bloodType = bt))
                Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                refreshAvatar()
                dialog.dismiss()
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                if (onboarding) {
                    // Skip: assign a private handle so the app is usable immediately.
                    val handle = "House-" + (10..99).random()
                    service.saveProfile(handle, emptyList(), emptyList(), emptyList())
                    service.saveMyZone(MeshZone(nation = "US"))
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun onDirectory() {
        val service = meshService ?: return
        val names = service.directory()
        if (names.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_contacts), Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_directory, null)
        val list = content.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.directoryList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = DirectoryAdapter(service, names) { name ->
            sheet.dismiss()
            showProfileDetail(name)
        }
        sheet.setContentView(content)
        sheet.show()
    }

    private fun photoStatusLine(service: MeshService, name: String): String = when {
        !service.hasPhotoFor(name) -> getString(R.string.profile_photo_none)
        service.isPhotoVerified(name) -> getString(R.string.profile_photo_verified_cd)
        else -> getString(R.string.profile_photo_vouch_progress, service.photoVouchCountFor(name))
    }

    private fun locationStatusLine(service: MeshService, name: String): String = when {
        name == service.myName -> {
            val n = service.mutualLocationPeers().size
            if (n > 0) getString(R.string.map_sharing_hint, n)
            else getString(R.string.map_hidden_hint)
        }
        service.isMutualLocationWith(name) ->
            if (service.peerLocationOf(name) != null) {
                getString(R.string.profile_location_shared, "recent")
            } else {
                getString(R.string.profile_location_mutual)
            }
        service.hasIncomingLocationOffer(name) ->
            getString(R.string.profile_location_pending_in)
        service.hasOutgoingLocationOffer(name) ->
            getString(R.string.profile_location_pending_out)
        else -> getString(R.string.profile_location_hidden)
    }

    private fun showProfileDetail(name: String) {
        val service = meshService ?: return
        val p = service.profileOf(name)
        val r = service.reciprocityOf(name)
        val cap = service.capacityOf(name)
        val capLabel = when (cap) {
            MeshService.CAP_HOMEBOUND -> getString(R.string.capacity_label_homebound)
            MeshService.CAP_LIMITED -> getString(R.string.capacity_label_limited)
            else -> getString(R.string.profile_capacity_full)
        }
        val capLine = when {
            service.isExempt(name) -> getString(R.string.profile_capacity_exempt, capLabel)
            service.isUnverifiedClaim(name) -> getString(R.string.profile_capacity_needs_vouches, capLabel)
            else -> getString(R.string.profile_capacity_full)
        }
        val sb = StringBuilder()
        sb.append(
            getString(
                R.string.profile_reciprocity_line,
                r.glyph,
                r.label,
                r.given,
                r.received,
            ),
        )
        sb.append("\n")
        sb.append(getString(R.string.profile_capacity_line, capLine))
        sb.append("\n\n")
        if (p != null && p.skills.isNotEmpty()) {
            sb.append(getString(R.string.profile_skills_line, p.skills.joinToString(", ")))
            sb.append("\n")
        }
        if (p != null && p.shares.isNotEmpty()) {
            sb.append(getString(R.string.profile_shares_line, p.shares.joinToString(", ")))
            sb.append("\n")
        }
        if (p != null && p.certs.isNotEmpty()) {
            val verified = service.myGroups().flatMap { g ->
                service.verifiedCertsIn(g.id, name).map { g.name to it }
            }
            val certLines = p.certs.map { cert ->
                val badge = verified.find { it.second == cert }?.let { " (${it.first})" } ?: ""
                "$cert$badge"
            }
            sb.append(getString(R.string.profile_certs_line, certLines.joinToString(", ")))
            sb.append("\n")
        }
        val memberships = service.groupsFor(name)
        if (memberships.isNotEmpty()) {
            sb.append(getString(R.string.profile_groups_line, memberships.joinToString(", ") { it.name }))
            sb.append("\n")
        }
        if (p == null || (p.skills.isEmpty() && p.shares.isEmpty() && p.certs.isEmpty())) {
            if (memberships.isEmpty()) sb.append(getString(R.string.profile_no_details))
        }
        sb.append("\n\n")
        sb.append(getString(R.string.profile_map_prefix, locationStatusLine(service, name)))
        val peerLoc = service.peerLocationOf(name)
        val ice = service.iceOf(name)
        if (ice != null && !ice.isBlank()) {
            sb.append("\n\n")
            sb.append(getString(R.string.profile_emergency_card_heading))
            sb.append("\n")
            sb.append(service.iceSummary(ice))
            if (ice.notes.isNotBlank()) {
                sb.append("\n")
                sb.append(getString(R.string.profile_notes_line, ice.notes))
            }
        }
        val header = layoutInflater.inflate(R.layout.profile_detail_header, null)
        AvatarBinder.bind(
            this,
            service,
            name,
            header.findViewById(R.id.detailAvatarImage),
            header.findViewById(R.id.detailAvatarInitial),
            header.findViewById(R.id.detailVerifiedBadge),
        )
        header.findViewById<TextView>(R.id.detailNameText).text =
            if (name == service.myName) getString(R.string.profile_you_suffix, name) else name
        header.findViewById<TextView>(R.id.detailPhotoStatusText).text = photoStatusLine(service, name)
        val body = TextView(this).apply {
            text = sb.toString().trim()
            setPadding(dp(20), dp(12), dp(20), dp(8))
            textSize = 14f
        }
        val scroll = ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(header)
                addView(body)
            })
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(R.string.action_close, null)
        if (name != service.myName) {
            when {
                service.hasIncomingLocationOffer(name) -> {
                    builder.setNeutralButton(getString(R.string.loc_action_accept)) { _, _ ->
                        service.acceptMutualLocation(name)
                        Toast.makeText(this, getString(R.string.loc_accept_sent, name), Toast.LENGTH_SHORT).show()
                    }
                    builder.setNegativeButton(getString(R.string.loc_action_reject)) { _, _ ->
                        service.rejectMutualLocation(name)
                    }
                }
                else -> {
                    when {
                        service.isMutualLocationWith(name) && peerLoc != null && peerLoc.hasCoords() -> {
                            builder.setNeutralButton(getString(R.string.loc_action_revoke)) { _, _ ->
                                service.revokeMutualLocation(name)
                                Toast.makeText(this, getString(R.string.loc_revoked, name), Toast.LENGTH_SHORT).show()
                            }
                            builder.setNegativeButton(getString(R.string.profile_open_maps)) { _, _ ->
                                MapsHelper.openInGoogleMaps(this, peerLoc.lat, peerLoc.lon, name)
                            }
                        }
                        service.isMutualLocationWith(name) -> {
                            builder.setNeutralButton(R.string.action_thank) { _, _ -> service.thank(name) }
                            builder.setNegativeButton(getString(R.string.loc_action_revoke)) { _, _ ->
                                service.revokeMutualLocation(name)
                                Toast.makeText(this, getString(R.string.loc_revoked, name), Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            builder.setNeutralButton(R.string.action_thank) { _, _ -> service.thank(name) }
                            if (!service.hasOutgoingLocationOffer(name)) {
                                builder.setNegativeButton(getString(R.string.loc_action_request)) { _, _ ->
                                    service.requestMutualLocation(name)
                                    Toast.makeText(
                                        this,
                                        getString(R.string.loc_request_sent, name),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            service.myLocationSnapshot()?.let { snap ->
                if (snap.hasCoords()) {
                    builder.setNeutralButton(getString(R.string.profile_open_maps)) { _, _ ->
                        MapsHelper.openInGoogleMaps(this, snap.lat, snap.lon, name)
                    }
                }
            }
            if (service.hasMutualLocationPeers()) {
                builder.setNegativeButton(getString(R.string.loc_action_revoke)) { _, _ ->
                    service.revokeAllMutualLocation()
                    Toast.makeText(this, R.string.location_share_off_toast, Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.show()
    }

    // ---- Groups ----

    private fun onCreateGroup() {
        val service = meshService ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.group_create_hint)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_create_title)
            .setMessage(R.string.group_create_message)
            .setView(input)
            .setPositiveButton(R.string.group_create_button) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.group_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    service.createGroup(name)
                    Toast.makeText(this, getString(R.string.group_created, name), Toast.LENGTH_SHORT).show()
                    refreshUi()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onMyGroups() {
        val service = meshService ?: return
        val mine = service.myGroups()
        val discover = service.allKnownGroups().filter { g -> g.id !in mine.map { it.id } }
        if (mine.isEmpty() && discover.isEmpty()) {
            Toast.makeText(this, R.string.group_none_yet, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf<String>()
        val ids = mutableListOf<String?>()
        if (mine.isNotEmpty()) {
            labels.add(getString(R.string.group_section_yours))
            ids.add(null)
            for (g in mine) {
                val role = if (service.isGroupAdmin(g.id)) {
                    getString(R.string.group_role_admin)
                } else {
                    getString(R.string.group_role_member)
                }
                labels.add(getString(R.string.group_list_entry, g.name, role, g.members.size))
                ids.add(g.id)
            }
        }
        if (discover.isNotEmpty()) {
            labels.add(getString(R.string.group_section_join))
            ids.add(null)
            for (g in discover) {
                labels.add(getString(R.string.group_discover_entry, g.name, g.members.size))
                ids.add(g.id)
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_my_groups))
            .setItems(labels.toTypedArray()) { _, which ->
                val gid = ids[which] ?: return@setItems
                if (mine.any { it.id == gid }) showGroupDetail(gid)
                else {
                    service.joinGroup(gid)
                    service.setFeedScope(gid)
                    syncRecipientToFeedScope()
                    Toast.makeText(
                        this,
                        getString(R.string.group_joined, service.groupOf(gid)?.name ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshUi()
                }
            }
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showGroupDetail(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val sb = StringBuilder()
        sb.append(getString(R.string.group_founder_line, g.founder))
        sb.append("\n")
        val memberLine = g.members.joinToString(", ") { m ->
            val tags = mutableListOf<String>()
            if (m in g.admins) tags.add(getString(R.string.group_role_admin))
            if (m == service.myName) tags.add(getString(R.string.group_role_you))
            if (tags.isEmpty()) m else getString(R.string.group_member_tag, m, tags.joinToString(", "))
        }
        sb.append(getString(R.string.group_members_line, g.members.size, memberLine))
        sb.append("\n\n")
        val pins = service.pinsForGroup(gid)
        if (pins.isNotEmpty()) {
            sb.append(getString(R.string.group_pinned_heading))
            sb.append("\n")
            for (p in pins.take(5)) {
                sb.append(getString(R.string.group_pinned_item, p.text, p.admin))
                sb.append("\n")
            }
            sb.append("\n")
        }
        val verified = service.verifiedSummaryForGroup(gid)
        if (verified.isNotEmpty()) {
            sb.append(getString(R.string.group_verified_heading))
            sb.append("\n")
            verified.forEach { (subject, cert) ->
                sb.append(getString(R.string.group_verified_item, subject, cert))
                sb.append("\n")
            }
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(g.name)
            .setMessage(sb.toString().trim())
            .setPositiveButton(R.string.action_close, null)
        if (service.isGroupAdmin(gid)) {
            builder.setNeutralButton(R.string.group_admin_button) { _, _ -> showGroupAdminMenu(gid) }
        }
        builder.show()
    }

    private fun showGroupAdminMenu(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val options = arrayOf(
            getString(R.string.group_admin_pin),
            getString(R.string.group_admin_verify),
            getString(R.string.group_admin_promote),
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.group_admin_title, g.name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onPinAnnouncement(gid)
                    1 -> onVerifyCert(gid)
                    2 -> onPromoteAdmin(gid)
                }
            }
            .show()
    }

    private fun onPinAnnouncement(gid: String) {
        val service = meshService ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.group_pin_hint)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_pin_title)
            .setView(input)
            .setPositiveButton(R.string.group_pin_button) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    service.pinGroupAnnouncement(gid, text)
                    Toast.makeText(this, R.string.group_pinned_toast, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onVerifyCert(gid: String) {
        val service = meshService ?: return
        val members = service.groupOf(gid)?.members?.filter { it != service.myName } ?: emptyList()
        if (members.isEmpty()) {
            Toast.makeText(this, R.string.group_no_members_verify, Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_pick_member)
            .setItems(members.toTypedArray()) { _, which -> pickCertToVerify(gid, members[which]) }
            .show()
    }

    private fun pickCertToVerify(gid: String, subject: String) {
        val service = meshService ?: return
        val profile = service.profileOf(subject)
        val certs = profile?.certs?.filter { it.isNotBlank() } ?: emptyList()
        if (certs.isEmpty()) {
            Toast.makeText(this, getString(R.string.group_no_certs, subject), Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_verify_cert_title)
            .setItems(certs.toTypedArray()) { _, which ->
                service.verifyCertInGroup(gid, subject, certs[which])
                Toast.makeText(this, R.string.group_verified_toast, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun onPromoteAdmin(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val candidates = g.members.filter { it != service.myName && it !in g.admins }
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.group_no_promote, Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.group_promote_title)
            .setItems(candidates.toTypedArray()) { _, which ->
                service.promoteGroupAdmin(gid, candidates[which])
                Toast.makeText(
                    this,
                    getString(R.string.group_promoted, candidates[which]),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .show()
    }

    private val bloodTypes = listOf("Unknown", "O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")

    private fun onEmergencyCard() {
        val service = meshService ?: return
        val ice = service.getMyIce()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            setPadding(0, dp(12), 0, dp(4))
            textSize = 13f
        }
        fun field(hint: String, value: String) = EditText(this).apply {
            this.hint = hint
            setText(value)
        }

        container.addView(TextView(this).apply {
            text = getString(R.string.emergency_card_privacy_hint)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })

        container.addView(label(getString(R.string.profile_label_blood_type)))
        val bloodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bloodTypes)
            setSelection(bloodTypes.indexOf(ice.bloodType).coerceAtLeast(0))
        }
        container.addView(bloodSpinner)

        container.addView(label(getString(R.string.emergency_card_label_allergies)))
        val allergies = field(getString(R.string.emergency_card_hint_allergies), ice.allergies)
            .also { container.addView(it) }
        container.addView(label(getString(R.string.emergency_card_label_medications)))
        val meds = field(getString(R.string.emergency_card_hint_medications), ice.medications)
            .also { container.addView(it) }
        container.addView(label(getString(R.string.emergency_card_label_conditions)))
        val conditions = field(getString(R.string.emergency_card_hint_conditions), ice.conditions)
            .also { container.addView(it) }
        container.addView(label(getString(R.string.emergency_card_label_contact_name)))
        val contactName = field(getString(R.string.emergency_card_hint_contact_name), ice.contactName)
            .also { container.addView(it) }
        container.addView(label(getString(R.string.emergency_card_label_contact_phone)))
        val contactPhone = field(getString(R.string.emergency_card_hint_contact_phone), ice.contactPhone)
            .also { container.addView(it) }
        container.addView(label(getString(R.string.emergency_card_label_notes)))
        val notes = field(getString(R.string.emergency_card_hint_notes), ice.notes).also { container.addView(it) }

        val scroll = ScrollView(this).apply { addView(container) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.emergency_card_title)
            .setView(scroll)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val bt = bloodTypes[bloodSpinner.selectedItemPosition].let { if (it == "Unknown") "" else it }
                service.saveIce(
                    MeshService.Ice(
                        bloodType = bt,
                        allergies = allergies.text.toString().trim(),
                        medications = meds.text.toString().trim(),
                        conditions = conditions.text.toString().trim(),
                        contactName = contactName.text.toString().trim(),
                        contactPhone = contactPhone.text.toString().trim(),
                        notes = notes.text.toString().trim()
                    )
                )
                Toast.makeText(this, R.string.emergency_card_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onAlertTabClicked() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.nav_alert)
            .setMessage(R.string.emergency_subtitle)
            .setPositiveButton(R.string.emergency_label) { _, _ -> onEmergencyClicked() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun filledSignalBars(ts: TransportState): Int {
        if (ts.meshBars > 0) {
            return ((ts.meshBars * 5f + 3f) / 4f).toInt().coerceIn(0, 5)
        }
        return if (transportSearching(ts)) 1 else 0
    }

    private fun transportSearching(ts: TransportState): Boolean =
        ts.ble == ChannelState.SEARCHING ||
            ts.wifiDirect == ChannelState.SEARCHING ||
            ts.lan == ChannelState.SEARCHING ||
            ts.cellular == ChannelState.SEARCHING

    private fun tintSignalBar(bar: View, filled: Boolean, color: Int) {
        val tint = if (filled) color else getColor(R.color.mesh_channel_off)
        bar.backgroundTintList = ColorStateList.valueOf(tint)
        bar.alpha = if (filled) 1f else 0.35f
    }

    private fun meshNetworkLabelFor(ts: TransportState, gatewayMode: Boolean): String = when {
        gatewayMode && (ts.lan == ChannelState.ACTIVE || ts.lan == ChannelState.SEARCHING) ->
            getString(R.string.mesh_network_gateway)
        ts.neighborCount > 0 -> getString(R.string.mesh_network_label)
        ts.cellular == ChannelState.ACTIVE || ts.cellular == ChannelState.SEARCHING ->
            getString(R.string.mesh_network_cell)
        ts.wifiDirect == ChannelState.ACTIVE || ts.lan == ChannelState.ACTIVE ->
            getString(R.string.mesh_network_wifi)
        transportSearching(ts) -> getString(R.string.mesh_network_label)
        else -> getString(R.string.mesh_network_label)
    }

    private fun meshSignalColor(ts: TransportState): Int {
        val colorRes = when {
            ts.meshBars >= 3 -> R.color.mesh_channel_active
            ts.meshBars >= 1 -> R.color.mesh_on_surface
            transportSearching(ts) -> R.color.mesh_on_surface
            ts.ble == ChannelState.ERROR || ts.wifiDirect == ChannelState.ERROR ||
                ts.lan == ChannelState.ERROR || ts.cellular == ChannelState.ERROR ->
                R.color.mesh_channel_error
            else -> R.color.mesh_on_surface_variant
        }
        return getColor(colorRes)
    }

    /** Shows readiness before MeshService binds so the strip is never empty/clipped. */
    private fun bindTransportStripPlaceholder() {
        networkReadinessDot.visibility = View.VISIBLE
        networkReadinessLabel.visibility = View.VISIBLE
        networkReadinessLabel.setText(R.string.network_readiness_searching)
        networkReadinessLabel.setTextColor(getColor(R.color.mesh_channel_search))
        val dotDrawable = ContextCompat.getDrawable(this, R.drawable.network_readiness_dot)?.mutate()
        if (dotDrawable != null) {
            DrawableCompat.setTint(dotDrawable, getColor(R.color.mesh_channel_search))
            networkReadinessDot.background = dotDrawable
        }
        networkReadinessHelp.visibility = View.GONE
    }

    private fun refreshTransportStrip(service: MeshService) {
        val ts = service.transportState()
        val color = meshSignalColor(ts)
        val lit = filledSignalBars(ts)
        signalBars.forEachIndexed { index, bar ->
            tintSignalBar(bar, index < lit, color)
        }
        meshNetworkLabel.text = meshNetworkLabelFor(ts, service.isGatewayMode())
        meshNetworkLabel.setTextColor(color)
        NetworkStatusIndicator.update(
            this,
            networkReadinessDot,
            networkReadinessLabel,
            networkReadinessHelp,
            service,
        )
        if (ts.neighborCount > 0) {
            neighborCountText.visibility = View.VISIBLE
            neighborCountText.text = ts.neighborCount.toString()
        } else {
            neighborCountText.visibility = View.GONE
            neighborCountText.text = ""
        }
        transportStrip.contentDescription = getString(
            R.string.mesh_status_detail,
            "${networkReadinessLabel.text} · ${meshNetworkLabel.text}",
            lit,
            signalBars.size,
        )
    }

    private fun onEmergencyClicked() {
        meshService?.sendEmergency()
        Toast.makeText(this, R.string.toast_emergency_sent, Toast.LENGTH_SHORT).show()
    }

    private fun onCoordinatorClicked() {
        val service = meshService ?: return
        val engine = if (service.llmActive()) "on-device AI (Gemma)" else "fast matcher"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.coordinator_dialog_title, engine))
            .setMessage(service.coordinatorSummary())
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun onClearFeedRequested() {
        val service = meshService ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.clear_feed_title)
            .setMessage(R.string.clear_feed_message)
            .setPositiveButton(R.string.action_clear) { _, _ -> service.clearFeed() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onUpdate() {
        runOnUiThread { refreshUi() }
    }

    private fun syncRecipientToFeedScope() {
        val service = meshService ?: return
        currentRecipient = when {
            service.feedScope == MeshService.SCOPE_EVERYONE -> MeshService.EVERYONE
            service.isDmScope(service.feedScope) -> service.peerFromDmScope(service.feedScope)
            else -> service.groupRecipient(service.feedScope)
        }
        updateRecipientButton()
    }

    private fun onFeedScopeSelected(scope: String) {
        val service = meshService ?: return
        service.setFeedScope(scope)
        syncRecipientToFeedScope()
        refreshUi()
    }

    private enum class NavTab { HOME, NEARBY, RESOURCES, ALERT }

    private fun selectNavTab(tab: NavTab) {
        data class TabUi(
            val iconWrap: View,
            val icon: ImageView,
            val label: TextView,
            val tab: NavTab,
            val activeColor: Int,
            val idleColor: Int,
        )
        val tabs = listOf(
            TabUi(bottomNavHomeIconWrap, bottomNavHomeIcon, bottomNavHomeLabel, NavTab.HOME, R.color.mesh_teal, R.color.mesh_on_surface_variant),
            TabUi(bottomNavNearbyIconWrap, bottomNavNearbyIcon, bottomNavNearbyLabel, NavTab.NEARBY, R.color.mesh_teal, R.color.mesh_on_surface_variant),
            TabUi(bottomNavResourcesIconWrap, bottomNavResourcesIcon, bottomNavResourcesLabel, NavTab.RESOURCES, R.color.mesh_amber, R.color.mesh_on_surface_variant),
            TabUi(bottomNavAlertIconWrap, bottomNavAlertIcon, bottomNavAlertLabel, NavTab.ALERT, R.color.mesh_emergency, R.color.mesh_emergency),
        )
        for (t in tabs) {
            val selected = t.tab == tab
            t.iconWrap.setBackgroundResource(if (selected) R.drawable.nav_icon_selected_bg else android.R.color.transparent)
            val color = ContextCompat.getColor(this, if (selected) t.activeColor else t.idleColor)
            t.icon.setColorFilter(color)
            t.label.setTextColor(color)
            t.label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun refreshUi() {
        val service = meshService ?: return
        if (service.hasProfile()) {
            userNameText.text = service.myName
            userNameText.visibility = View.VISIBLE
        } else {
            userNameText.visibility = View.GONE
        }
        refreshAvatar()
        refreshTransportStrip(service)
        val scope = service.feedScope
        val inDm = service.isDmScope(scope)
        feedBackButton.visibility = if (inDm) View.VISIBLE else View.GONE
        dmPeerAvatarButton.visibility = if (inDm) View.VISIBLE else View.GONE
        areaPickerRow.visibility = if (inDm) View.GONE else View.VISIBLE
        feedTitleText.visibility = if (inDm) View.VISIBLE else View.GONE
        if (!inDm) {
            val scopeLabel = service.feedScopeLabel(scope)
            areaPickerLabel.text = scopeLabel
            areaPickerRow.contentDescription =
                getString(R.string.area_picker_title) + ": " + scopeLabel
            areaPickerRow.isClickable = true
            areaPickerRow.isEnabled = true
        }
        val dmCount = service.dmConversations().size
        chatsIconButton.visibility = if (inDm) View.GONE else View.VISIBLE
        chatsIconButton.contentDescription = if (dmCount > 0) {
            getString(R.string.chats_button_count, dmCount)
        } else {
            getString(R.string.chats_button)
        }
        chatsIconButton.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (dmCount > 0) R.color.mesh_teal else R.color.mesh_on_surface_variant,
            ),
        )
        if (inDm) {
            val peer = service.peerFromDmScope(scope)
            dmPeerAvatarClickName = peer
            feedTitleText.text = getString(R.string.feed_title_dm, peer)
            AvatarBinder.bind(
                this,
                service,
                peer,
                dmPeerAvatarImage,
                dmPeerAvatarInitial,
                dmPeerAvatarVerifiedBadge,
            )
        } else {
            dmPeerAvatarClickName = null
        }
        feedSortRow.visibility = if (inDm) View.GONE else View.VISIBLE
        if (!inDm) {
            updateFeedSortUi(service.feedSort())
        }
        val lines = service.feedLines(scope)
        val emptyFallback = when {
            scope == MeshService.SCOPE_EVERYONE || MeshZone.isZoneScope(scope) ->
                getString(R.string.feed_empty)
            service.isDmScope(scope) ->
                getString(R.string.feed_empty_dm)
            else ->
                getString(R.string.feed_empty_group)
        }
        feedEmptyText.text = emptyFallback
        if (lines.isEmpty()) {
            feedList.visibility = View.GONE
            feedEmptyText.visibility = View.VISIBLE
            feedPostCountText.visibility = View.GONE
        } else {
            feedAdapter.submitList(lines)
            feedList.visibility = View.VISIBLE
            feedEmptyText.visibility = View.GONE
            feedPostCountText.text = getString(R.string.feed_post_count, lines.size)
            feedPostCountText.visibility = View.VISIBLE
            if (inDm) scrollFeedToBottom() else scrollFeedToTop()
        }
    }

    private fun scrollFeedToBottom() {
        feedList.post {
            val count = feedAdapter.itemCount
            if (count > 0) {
                feedList.scrollToPosition(count - 1)
            }
        }
    }

    private fun scrollFeedToTop() {
        feedList.post {
            if (feedAdapter.itemCount > 0) {
                feedList.scrollToPosition(0)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        meshService?.setCallback(this)
        meshService?.refreshLiveGeoAsync()
        if (criticalPermissionsGranted() && !bound) {
            startAndBindService()
        }
        if (bound) refreshUi()
    }

    override fun onDestroy() {
        NetworkStatusIndicator.release(networkReadinessDot)
        super.onDestroy()
        meshService?.setCallback(null)
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}

