package com.meshhood

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), MeshService.MeshCallback {

    private lateinit var statusText: TextView
    private lateinit var userNameText: TextView
    private lateinit var feedTitleText: TextView
    private lateinit var areaPickerButton: MaterialButton
    private lateinit var feedBackButton: ImageButton
    private lateinit var chatsButton: MaterialButton
    private lateinit var messageText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var emergencyButton: Button
    private lateinit var recipientButton: Button
    private lateinit var coordinatorButton: Button
    private lateinit var menuButton: ImageButton

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
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val critical = requiredPermissions().filter { it !in optionalPermissions }
        if (critical.all { results[it] == true }) {
            startAndBindService()
        } else {
            statusText.text = "Permissions denied"
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        userNameText = findViewById(R.id.userNameText)
        feedTitleText = findViewById(R.id.feedTitleText)
        areaPickerButton = findViewById(R.id.areaPickerButton)
        feedBackButton = findViewById(R.id.feedBackButton)
        chatsButton = findViewById(R.id.chatsButton)
        messageText = findViewById(R.id.messageText)
        logScroll = findViewById(R.id.logScroll)
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        emergencyButton = findViewById(R.id.emergencyButton)
        recipientButton = findViewById(R.id.recipientButton)
        coordinatorButton = findViewById(R.id.coordinatorButton)
        menuButton = findViewById(R.id.menuButton)

        sendButton.setOnClickListener { onSendClicked() }
        emergencyButton.setOnClickListener { onEmergencyClicked() }
        recipientButton.setOnClickListener { onRecipientClicked() }
        coordinatorButton.setOnClickListener { onCoordinatorClicked() }
        menuButton.setOnClickListener { onFeedLongPress() }
        feedBackButton.setOnClickListener { returnToArea() }
        areaPickerButton.setOnClickListener { onAreaPickerClicked() }
        chatsButton.setOnClickListener { onChatsClicked() }
        logScroll.setOnLongClickListener { onFeedLongPress(); true }
        messageText.setOnLongClickListener { onFeedLongPress(); true }

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
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return perms.toTypedArray()
    }

    private fun criticalPermissionsGranted(): Boolean {
        return requiredPermissions()
            .filter { it !in optionalPermissions }
            .all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
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
        val service = meshService ?: return
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
        val labels = options.map { (scope, label) ->
            val mark = if (scope == service.feedScope) "● " else "   "
            val name = when {
                scope == MeshService.SCOPE_EVERYONE -> label
                MeshZone.isZoneScope(scope) -> "📍 $label"
                else -> "🏘️ $label"
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

    /** Compact state + ZIP setup — unlocks the geographic levels in Area. */
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
            list.adapter = ConversationAdapter(conversations) { conv ->
                sheet.dismiss()
                openDmConversation(conv.peer)
            }
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

    private fun onRecipientClicked() {
        val service = meshService ?: return
        val peers = service.getPeers()
        val myGroups = service.myGroups()
        val groupEntries = myGroups.map { g -> service.groupRecipient(g.id) to "🏘️ ${g.name}" }
        val names = listOf(MeshService.EVERYONE) + groupEntries.map { it.first } + peers
        val labels = listOf(MeshService.EVERYONE) +
            groupEntries.map { it.second } +
            peers.map { nameWithStars(it, service.reputationOf(it)) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Send to")
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
            return service.groupOf(service.groupIdFromRecipient(currentRecipient))?.name ?: "Group"
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
        val stars = if (r.given > 0) "  ${"⭐".repeat(r.given.coerceAtMost(5))}${if (r.given > 5) "+" else ""}" else ""
        return "$name$stars  ${r.glyph}"
    }

    // Kept for callers that only have a raw reputation count handy.
    private fun nameWithStars(name: String, rep: Int): String = peerLabel(name)

    private fun onFeedLongPress() {
        val options = arrayOf(
            "🙏 ${getString(R.string.menu_send_thanks)}",
            "📣 ${getString(R.string.menu_call_help)}",
            "👥 ${getString(R.string.menu_directory)}",
            "🏘️ ${getString(R.string.menu_my_groups)}",
            "➕ ${getString(R.string.menu_create_group)}",
            "🏅 ${getString(R.string.menu_thanks_board)}",
            "🫂 ${getString(R.string.menu_my_capacity)}",
            "📍 ${getString(R.string.menu_set_area)}",
            "✏️ ${getString(R.string.menu_edit_profile)}",
            "🆘 ${getString(R.string.menu_emergency_card)}",
            "✓ ${getString(R.string.menu_vouch)}",
            "🧹 ${getString(R.string.menu_clear_feed)}"
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
                    8 -> showProfileDialog(onboarding = false)
                    9 -> onEmergencyCard()
                    10 -> onVouch()
                    11 -> onClearFeedRequested()
                }
            }
            .show()
    }

    private fun onCallForHelp() {
        val service = meshService ?: return
        val input = EditText(this).apply {
            hint = "e.g. Shoveling Edna's driveway, Sat 9am — who's in?"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📣 Call for help")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val task = input.text.toString().trim()
                if (task.isNotEmpty()) service.callForHelp(task)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onMyStatus() {
        val service = meshService ?: return
        val labels = arrayOf("🟢 Full — I can help", "🟡 Limited — I can help a little", "🏠 Homebound — I mostly need help")
        val values = arrayOf(MeshService.CAP_FULL, MeshService.CAP_LIMITED, MeshService.CAP_HOMEBOUND)
        val current = service.capacityOf(service.myName)
        val checked = values.indexOf(current).coerceAtLeast(0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Your capacity")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                service.setMyStatus(values[which])
                dialog.dismiss()
                if (values[which] != MeshService.CAP_FULL) {
                    Toast.makeText(this, getString(R.string.toast_vouch_confirm), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
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
            val cap = if (service.capacityOf(it) == MeshService.CAP_HOMEBOUND) "Homebound" else "Limited"
            "$it — $cap (${service.vouchCountFor(it)} vouches)"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_vouch_status))
            .setItems(labels) { _, which ->
                service.vouchFor(candidates[which])
                Toast.makeText(this, "Vouched for ${candidates[which]} ✓", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun onGoodNeighborsBoard() {
        val service = meshService ?: return
        val names = service.ratedNames()
        if (names.isEmpty()) {
            Toast.makeText(this, "No reputation activity yet", Toast.LENGTH_SHORT).show()
            return
        }
        val ranked = names
            .map { it to service.reciprocityOf(it) }
            .sortedWith(compareByDescending<Pair<String, MeshService.Reciprocity>> { it.second.given }
                .thenByDescending { it.second.received }
                .thenBy { it.first })
        val board = ranked.joinToString("\n") { (name, r) ->
            "${r.glyph} $name — helped ${r.given}, received ${r.received}  · ${r.label}"
        }
        val nudges = names.mapNotNull { service.opportunityNudge(it) }
        val body = if (nudges.isEmpty()) board
        else board + "\n\n— Opportunities —\n" + nudges.joinToString("\n") { "• $it" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🏅 ${getString(R.string.title_thanks_board)}")
            .setMessage(body)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun onThankNeighbor() {
        val service = meshService ?: return
        val peers = service.getPeers()
        if (peers.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_contacts), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = peers.map { nameWithStars(it, service.reputationOf(it)) }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_send_thanks))
            .setItems(labels) { _, which ->
                service.thank(peers[which])
                Toast.makeText(this, "Thanked ${peers[which]} 🙏", Toast.LENGTH_SHORT).show()
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
            hint = "Your name or handle (required)"
            setText(if (onboarding) "" else profile.name)
        }
        container.addView(label("Name"))
        container.addView(nameField)

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
        container.addView(label("Blood type"))
        val bloodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bloodTypes)
            setSelection(bloodTypes.indexOf(ice.bloodType).coerceAtLeast(0))
        }
        container.addView(bloodSpinner)
        container.addView(TextView(this).apply {
            text = "Kept private on your phone — only shared when you send an emergency."
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })

        container.addView(label("Skills you can offer"))
        val skillBoxes = skillOptions.map { skill ->
            CheckBox(this).apply {
                text = skill
                isChecked = profile.skills.contains(skill)
            }.also { container.addView(it) }
        }

        val sharesField = EditText(this).apply {
            hint = "e.g. generator, water jugs, truck, spare room"
            setText(profile.shares.joinToString(", "))
        }
        container.addView(label("Things you can share (comma separated)"))
        container.addView(sharesField)

        val certsField = EditText(this).apply {
            hint = "e.g. RN license, CPR certified, licensed electrician"
            setText(profile.certs.joinToString(", "))
        }
        container.addView(label(getString(R.string.toast_certs_note)))
        container.addView(certsField)

        val scroll = ScrollView(this).apply { addView(container) }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (onboarding) "Welcome to MeshHood — set up your profile" else "Edit profile")
            .setView(scroll)
            .setPositiveButton("Save", null)
        if (onboarding) {
            builder.setNegativeButton("Skip for now", null)
            builder.setCancelable(false)
        } else {
            builder.setNegativeButton("Cancel", null)
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name or handle", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
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
        val labels = names.map { name ->
            val p = service.profileOf(name)
            val skillCount = p?.skills?.size ?: 0
            val self = if (name == service.myName) " (you)" else ""
            val cap = service.capacityOf(name)
            val capGlyph = when {
                service.isExempt(name) -> " 💛"
                cap == MeshService.CAP_LIMITED || cap == MeshService.CAP_HOMEBOUND -> " 🟡"
                else -> ""
            }
            "$name$self$capGlyph — ${if (skillCount > 0) "$skillCount skill(s)" else "no skills listed"}"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("👥 ${getString(R.string.title_directory)}")
            .setItems(labels) { _, which -> showProfileDetail(names[which]) }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showProfileDetail(name: String) {
        val service = meshService ?: return
        val p = service.profileOf(name)
        val r = service.reciprocityOf(name)
        val cap = service.capacityOf(name)
        val capLine = when {
            service.isExempt(name) -> "💛 ${if (cap == MeshService.CAP_HOMEBOUND) "Homebound" else "Limited"} (vouched — exempt)"
            service.isUnverifiedClaim(name) -> "🟡 ${if (cap == MeshService.CAP_HOMEBOUND) "Homebound" else "Limited"} (needs vouches)"
            else -> "🟢 Full capacity"
        }
        val sb = StringBuilder()
        sb.append("${r.glyph} ${r.label} · helped ${r.given}, received ${r.received}\n")
        sb.append("Capacity: $capLine\n\n")
        if (p != null && p.skills.isNotEmpty()) sb.append("Skills: ${p.skills.joinToString(", ")}\n")
        if (p != null && p.shares.isNotEmpty()) sb.append("Can share: ${p.shares.joinToString(", ")}\n")
        if (p != null && p.certs.isNotEmpty()) {
            val verified = service.myGroups().flatMap { g ->
                service.verifiedCertsIn(g.id, name).map { g.name to it }
            }
            val certLines = p.certs.map { cert ->
                val badge = verified.find { it.second == cert }?.let { " ✓ ${it.first}" } ?: ""
                "$cert$badge"
            }
            sb.append("Certifications: ${certLines.joinToString(", ")}\n")
        }
        val memberships = service.groupsFor(name)
        if (memberships.isNotEmpty()) {
            sb.append("Groups: ${memberships.joinToString(", ") { it.name }}\n")
        }
        if (p == null || (p.skills.isEmpty() && p.shares.isEmpty() && p.certs.isEmpty())) {
            if (memberships.isEmpty()) sb.append("No profile details shared yet.")
        }
        val ice = service.iceOf(name)
        if (ice != null && !ice.isBlank()) {
            sb.append("\n\n🆘 Emergency Card\n")
            sb.append(service.iceSummary(ice))
            if (ice.notes.isNotBlank()) sb.append("\nNotes: ${ice.notes}")
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(sb.toString().trim())
            .setPositiveButton("Close", null)
        if (name != service.myName) {
            builder.setNeutralButton("🙏 Thank") { _, _ -> service.thank(name) }
        }
        builder.show()
    }

    // ---- Groups ----

    private fun onCreateGroup() {
        val service = meshService ?: return
        val input = EditText(this).apply {
            hint = "e.g. Oak St Block, Building 4, Westside Mutual Aid"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("➕ Create a group")
            .setMessage("You become the founder and first admin. Admins can verify credentials, pin announcements, and coordinate — but never censor speech.")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter a group name", Toast.LENGTH_SHORT).show()
                } else {
                    service.createGroup(name)
                    Toast.makeText(this, "Created \"$name\" — you're the admin", Toast.LENGTH_SHORT).show()
                    refreshUi()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onMyGroups() {
        val service = meshService ?: return
        val mine = service.myGroups()
        val discover = service.allKnownGroups().filter { g -> g.id !in mine.map { it.id } }
        if (mine.isEmpty() && discover.isEmpty()) {
            Toast.makeText(this, "No groups yet — create one from the menu", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf<String>()
        val ids = mutableListOf<String?>()
        if (mine.isNotEmpty()) {
            labels.add("— Your groups —")
            ids.add(null)
            for (g in mine) {
                val role = if (service.isGroupAdmin(g.id)) "admin" else "member"
                labels.add("🏘️ ${g.name} ($role, ${g.members.size} members)")
                ids.add(g.id)
            }
        }
        if (discover.isNotEmpty()) {
            labels.add("— Join a group —")
            ids.add(null)
            for (g in discover) {
                labels.add("➕ ${g.name} (${g.members.size} members)")
                ids.add(g.id)
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🏘️ My groups")
            .setItems(labels.toTypedArray()) { _, which ->
                val gid = ids[which] ?: return@setItems
                if (mine.any { it.id == gid }) showGroupDetail(gid)
                else {
                    service.joinGroup(gid)
                    service.setFeedScope(gid)
                    syncRecipientToFeedScope()
                    Toast.makeText(this, "Joined ${service.groupOf(gid)?.name}", Toast.LENGTH_SHORT).show()
                    refreshUi()
                }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showGroupDetail(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val sb = StringBuilder()
        sb.append("Founder: ${g.founder}\n")
        sb.append("Members (${g.members.size}): ")
        sb.append(g.members.joinToString(", ") { m ->
            val tags = mutableListOf<String>()
            if (m in g.admins) tags.add("admin")
            if (m == service.myName) tags.add("you")
            if (tags.isEmpty()) m else "$m (${tags.joinToString(", ")})"
        })
        sb.append("\n\n")
        val pins = service.pinsForGroup(gid)
        if (pins.isNotEmpty()) {
            sb.append("📌 Pinned\n")
            for (p in pins.take(5)) sb.append("• ${p.text} — ${p.admin}\n")
            sb.append("\n")
        }
        val verified = service.verifiedSummaryForGroup(gid)
        if (verified.isNotEmpty()) {
            sb.append("✓ Verified credentials\n")
            verified.forEach { (subject, cert) -> sb.append("• $subject — $cert\n") }
        }
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🏘️ ${g.name}")
            .setMessage(sb.toString().trim())
            .setPositiveButton("Close", null)
        if (service.isGroupAdmin(gid)) {
            builder.setNeutralButton("Admin") { _, _ -> showGroupAdminMenu(gid) }
        }
        builder.show()
    }

    private fun showGroupAdminMenu(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val options = arrayOf(
            "📌 Pin announcement",
            "✓ Verify a credential",
            "👑 Promote to admin"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Admin — ${g.name}")
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
            hint = "e.g. Sandbag pickup Saturday 9am at the clubhouse"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📌 Pin announcement")
            .setView(input)
            .setPositiveButton("Pin") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    service.pinGroupAnnouncement(gid, text)
                    Toast.makeText(this, "Pinned to group", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onVerifyCert(gid: String) {
        val service = meshService ?: return
        val members = service.groupOf(gid)?.members?.filter { it != service.myName } ?: emptyList()
        if (members.isEmpty()) {
            Toast.makeText(this, "No other members to verify yet", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pick a member")
            .setItems(members.toTypedArray()) { _, which -> pickCertToVerify(gid, members[which]) }
            .show()
    }

    private fun pickCertToVerify(gid: String, subject: String) {
        val service = meshService ?: return
        val profile = service.profileOf(subject)
        val certs = profile?.certs?.filter { it.isNotBlank() } ?: emptyList()
        if (certs.isEmpty()) {
            Toast.makeText(this, "$subject has no certs on their profile", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Verify which credential?")
            .setItems(certs.toTypedArray()) { _, which ->
                service.verifyCertInGroup(gid, subject, certs[which])
                Toast.makeText(this, "Verified ✓", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun onPromoteAdmin(gid: String) {
        val service = meshService ?: return
        val g = service.groupOf(gid) ?: return
        val candidates = g.members.filter { it != service.myName && it !in g.admins }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No members to promote", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Promote to admin")
            .setItems(candidates.toTypedArray()) { _, which ->
                service.promoteGroupAdmin(gid, candidates[which])
                Toast.makeText(this, "${candidates[which]} is now admin", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private val bloodTypes = listOf("Unknown", "O−", "O+", "A−", "A+", "B−", "B+", "AB−", "AB+")

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
            text = "🆘 Shared only when YOU send an emergency. Stored on your phone otherwise."
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        })

        container.addView(label("Blood type"))
        val bloodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bloodTypes)
            setSelection(bloodTypes.indexOf(ice.bloodType).coerceAtLeast(0))
        }
        container.addView(bloodSpinner)

        container.addView(label("Allergies"))
        val allergies = field("e.g. penicillin, peanuts", ice.allergies).also { container.addView(it) }
        container.addView(label("Medications"))
        val meds = field("e.g. insulin, blood thinners", ice.medications).also { container.addView(it) }
        container.addView(label("Medical conditions"))
        val conditions = field("e.g. diabetic, pacemaker, asthma", ice.conditions).also { container.addView(it) }
        container.addView(label("Emergency contact name"))
        val contactName = field("e.g. Jane (wife)", ice.contactName).also { container.addView(it) }
        container.addView(label("Emergency contact phone"))
        val contactPhone = field("e.g. 555-123-4567", ice.contactPhone).also { container.addView(it) }
        container.addView(label("Other notes (mobility, language, etc.)"))
        val notes = field("e.g. uses a wheelchair, speaks Spanish", ice.notes).also { container.addView(it) }

        val scroll = ScrollView(this).apply { addView(container) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Emergency Card")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
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
                Toast.makeText(this, "Emergency Card saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onEmergencyClicked() {
        meshService?.sendEmergency()
        Toast.makeText(this, "Emergency broadcast sent", Toast.LENGTH_SHORT).show()
    }

    private fun onCoordinatorClicked() {
        val service = meshService ?: return
        val engine = if (service.llmActive()) "on-device AI (Gemma)" else "fast matcher"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🧭 Coordinator · $engine")
            .setMessage(service.coordinatorSummary())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun onClearFeedRequested() {
        val service = meshService ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear feed?")
            .setMessage("This removes the saved messages on this phone. Peers are kept.")
            .setPositiveButton("Clear") { _, _ -> service.clearFeed() }
            .setNegativeButton("Cancel", null)
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

    private fun refreshUi() {
        val service = meshService ?: return
        if (service.hasProfile()) {
            userNameText.text = service.myName
            userNameText.visibility = View.VISIBLE
        } else {
            userNameText.visibility = View.GONE
        }
        statusText.text = service.status
        val scope = service.feedScope
        val inDm = service.isDmScope(scope)
        feedBackButton.visibility = if (inDm) View.VISIBLE else View.GONE
        areaPickerButton.visibility = if (inDm) View.GONE else View.VISIBLE
        feedTitleText.visibility = if (inDm) View.VISIBLE else View.GONE
        if (!inDm) {
            areaPickerButton.text = getString(R.string.area_picker_button_label, service.feedScopeLabel(scope))
        }
        val dmCount = service.dmConversations().size
        chatsButton.visibility = if (inDm) View.GONE else View.VISIBLE
        chatsButton.text = if (dmCount > 0) {
            getString(R.string.chats_button_count, dmCount)
        } else {
            getString(R.string.chats_button)
        }
        if (inDm) {
            feedTitleText.text = getString(
                R.string.feed_title_dm,
                service.peerFromDmScope(scope)
            )
        }
        val log = service.getFeedText(scope)
        messageText.text = when {
            log.isBlank() && (scope == MeshService.SCOPE_EVERYONE || MeshZone.isZoneScope(scope)) ->
                getString(R.string.feed_empty)
            log.isBlank() && service.isDmScope(scope) ->
                getString(R.string.feed_empty_dm)
            log.isBlank() ->
                getString(R.string.feed_empty_group)
            else -> log
        }
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onStart() {
        super.onStart()
        meshService?.setCallback(this)
        meshService?.refreshLiveGeoAsync()
        if (bound) refreshUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        meshService?.setCallback(null)
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
