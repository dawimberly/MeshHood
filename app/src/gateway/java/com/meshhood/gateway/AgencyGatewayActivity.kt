package com.meshhood.gateway

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meshhood.AgencySigner
import com.meshhood.BuildConfig
import com.meshhood.MainActivity
import com.meshhood.MeshService
import com.meshhood.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject

class AgencyGatewayActivity : AppCompatActivity(), MeshService.MeshCallback {

    private var meshService: MeshService? = null
    private var bound = false

    private lateinit var meshStatusText: TextView
    private lateinit var keyHint: TextView
    private lateinit var agencySpinner: Spinner
    private lateinit var alertText: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var gatewaySwitch: SwitchMaterial
    private lateinit var headlessSwitch: SwitchMaterial
    private lateinit var cellularRelayUrl: TextInputEditText
    private lateinit var cellularRelayToken: TextInputEditText
    private lateinit var grantPermissionsButton: MaterialButton

    private var agencies: List<AgencyOption> = emptyList()

    private data class AgencyOption(val id: String, val label: String) {
        override fun toString(): String = label
    }

    private val optionalPermissions = setOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val critical = requiredPermissions().filter { it !in optionalPermissions }
        val locationOk = hasLocationPermission()
        val othersOk = critical
            .filter {
                it != Manifest.permission.ACCESS_FINE_LOCATION &&
                    it != Manifest.permission.ACCESS_COARSE_LOCATION
            }
            .all { results[it] == true || ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (othersOk && locationOk) {
            grantPermissionsButton.visibility = View.GONE
            startAndBindService()
        } else {
            showPermissionsBlockedUi()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            meshService = (binder as MeshService.LocalBinder).service
            meshService?.setCallback(this@AgencyGatewayActivity)
            bound = true
            syncUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShowUiIntent(intent)
        if (GatewayMode.enterHeadlessIfNeeded(this)) {
            return
        }
        setContentView(R.layout.activity_agency_gateway)

        meshStatusText = findViewById(R.id.gatewayMeshStatus)
        keyHint = findViewById(R.id.gatewayKeyHint)
        agencySpinner = findViewById(R.id.agencySpinner)
        alertText = findViewById(R.id.alertText)
        sendButton = findViewById(R.id.sendAlertButton)
        gatewaySwitch = findViewById(R.id.gatewayModeSwitch)
        headlessSwitch = findViewById(R.id.gatewayHeadlessSwitch)
        cellularRelayUrl = findViewById(R.id.cellularRelayUrl)
        cellularRelayToken = findViewById(R.id.cellularRelayToken)
        grantPermissionsButton = findViewById(R.id.grantPermissionsButton)

        agencies = loadAgencies()
        agencySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            agencies,
        )

        val hasKey = BuildConfig.AGENCY_SIGNING_KEY.isNotBlank()
        keyHint.visibility = if (hasKey) View.GONE else View.VISIBLE
        keyHint.text = getString(R.string.agency_gateway_no_key)
        sendButton.isEnabled = hasKey && agencies.isNotEmpty()

        gatewaySwitch.setOnCheckedChangeListener { _, checked ->
            val service = meshService ?: return@setOnCheckedChangeListener
            if (service.isGatewayMode() != checked) {
                service.setGatewayMode(checked)
                Toast.makeText(
                    this,
                    if (checked) R.string.gateway_mode_on_toast else R.string.gateway_mode_off_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        headlessSwitch.isChecked = GatewayMode.isHeadless(this)
        headlessSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (GatewayMode.isHeadless(this)) return@setOnCheckedChangeListener
                headlessSwitch.isChecked = false
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.gateway_headless_confirm_title)
                    .setMessage(R.string.gateway_headless_confirm_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        GatewayMode.activateHeadless(this)
                        headlessSwitch.isChecked = true
                        Toast.makeText(this, R.string.gateway_headless_on_toast, Toast.LENGTH_LONG).show()
                        if (criticalPermissionsGranted()) {
                            GatewayMode.ensureHeadlessMeshRunning(this)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                GatewayMode.deactivateHeadless(this)
                Toast.makeText(this, R.string.gateway_headless_off_toast, Toast.LENGTH_SHORT).show()
            }
        }

        sendButton.setOnClickListener { sendAlert() }
        findViewById<MaterialButton>(R.id.openFullAppButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        grantPermissionsButton.setOnClickListener { onGrantPermissionsClicked() }

        val saveRelayConfig = {
            meshService?.setCellularRelayConfig(
                cellularRelayUrl.text?.toString().orEmpty(),
                cellularRelayToken.text?.toString().orEmpty(),
            )
        }
        cellularRelayUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveRelayConfig()
        }
        cellularRelayToken.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveRelayConfig()
        }

        meshStatusText.text = getString(R.string.agency_gateway_mesh_starting)
        ensureMeshReady()
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

    private fun shouldOpenAppSettingsForPermissions(): Boolean {
        val critical = requiredPermissions().filter { it !in optionalPermissions }
        val locationMissing = !hasLocationPermission()
        if (locationMissing) {
            val locPerms = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            val anyLocDenied = locPerms.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (anyLocDenied && locPerms.none { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                return true
            }
        }
        return critical.any { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
        }
    }

    private fun ensureMeshReady() {
        if (criticalPermissionsGranted()) {
            grantPermissionsButton.visibility = View.GONE
            if (!bound) {
                meshStatusText.text = getString(R.string.agency_gateway_mesh_starting)
                startAndBindService()
            } else {
                syncUi()
            }
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun showPermissionsBlockedUi() {
        meshStatusText.text = getString(R.string.agency_gateway_permissions_denied)
        grantPermissionsButton.visibility = View.VISIBLE
        grantPermissionsButton.text = getString(
            if (shouldOpenAppSettingsForPermissions()) {
                R.string.agency_gateway_open_settings
            } else {
                R.string.agency_gateway_grant_permissions
            },
        )
        Toast.makeText(this, R.string.agency_gateway_permissions_required, Toast.LENGTH_LONG).show()
    }

    private fun onGrantPermissionsClicked() {
        if (shouldOpenAppSettingsForPermissions()) {
            openAppSettings()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun startAndBindService() {
        val intent = Intent(this, MeshService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun loadAgencies(): List<AgencyOption> {
        return try {
            val root = JSONObject(assets.open("agency_gateway.json").bufferedReader().readText())
            val arr = root.optJSONArray("agencies") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").trim()
                    if (id.isEmpty()) continue
                    add(AgencyOption(id, o.optString("label", id).trim()))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sendAlert() {
        val service = meshService ?: return
        val idx = agencySpinner.selectedItemPosition
        if (idx !in agencies.indices) return
        val text = alertText.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        val envelope = AgencySigner.signAlert(
            agencies[idx].id,
            text,
            BuildConfig.AGENCY_SIGNING_KEY,
        )
        if (envelope == null) {
            Toast.makeText(this, R.string.agency_gateway_failed, Toast.LENGTH_LONG).show()
            return
        }
        if (!service.publishAgencyAlert(envelope.toString())) {
            Toast.makeText(this, R.string.agency_gateway_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.agency_gateway_sent, Toast.LENGTH_SHORT).show()
        alertText.text?.clear()
    }

    private fun syncUi() {
        val service = meshService ?: return
        gatewaySwitch.isChecked = service.isGatewayMode()
        if (!cellularRelayUrl.hasFocus()) {
            cellularRelayUrl.setText(service.cellularRelayUrl())
        }
        if (!cellularRelayToken.hasFocus()) {
            cellularRelayToken.setText(service.cellularRelayToken())
        }
        meshStatusText.text = formatGatewayStatus(service.status)
    }

    private fun formatGatewayStatus(raw: String): String {
        if ((raw.contains("waiting to connect", ignoreCase = true) || raw.contains("no device linked", ignoreCase = true))) {
            return getString(R.string.agency_gateway_status_ble_waiting, raw)
        }
        if (raw.contains("searching", ignoreCase = true) && raw.contains("WiFi LAN", ignoreCase = true)) {
            return getString(R.string.agency_gateway_status_lan_searching, raw)
        }
        return raw
    }

    override fun onUpdate() {
        runOnUiThread { syncUi() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShowUiIntent(intent)
    }

    private fun handleShowUiIntent(intent: Intent?) {
        if (intent?.action != GatewayMode.ACTION_SHOW_UI) return
        // Explicit SHOW_UI — keep headless pref; user requested Official alerts.
    }

    override fun onResume() {
        super.onResume()
        if (::headlessSwitch.isInitialized && GatewayMode.isHeadless(this) && !isFinishing) {
            headlessSwitch.isChecked = true
        }
        meshService?.setCallback(this)
        if (criticalPermissionsGranted()) {
            if (!bound) {
                ensureMeshReady()
            } else {
                syncUi()
            }
        } else {
            showPermissionsBlockedUi()
        }
    }

    override fun onPause() {
        if (::cellularRelayUrl.isInitialized && ::cellularRelayToken.isInitialized) {
            meshService?.setCellularRelayConfig(
                cellularRelayUrl.text?.toString().orEmpty(),
                cellularRelayToken.text?.toString().orEmpty(),
            )
        }
        meshService?.setCallback(null)
        super.onPause()
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}


