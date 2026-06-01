package com.meshhood

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MapActivity : AppCompatActivity(), MeshService.MeshCallback, OnMapReadyCallback {

    private var meshService: MeshService? = null
    private var bound = false
    private var googleMap: GoogleMap? = null

    private lateinit var shareSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var searchNearbyButton: MaterialButton
    private lateinit var openSystemMapsButton: MaterialButton
    private lateinit var mapSetupHint: TextView
    private val peerMarkers = mutableMapOf<String, Marker>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            meshService = (binder as MeshService.LocalBinder).service
            meshService?.setCallback(this@MapActivity)
            bound = true
            syncUi()
            refreshMarkers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        shareSwitch = findViewById(R.id.shareLocationSwitch)
        statusText = findViewById(R.id.mapStatusText)
        searchNearbyButton = findViewById(R.id.searchNearbyButton)
        openSystemMapsButton = findViewById(R.id.openSystemMapsButton)
        mapSetupHint = findViewById(R.id.mapSetupHint)

        findViewById<ImageButton>(R.id.mapBackButton).setOnClickListener { finish() }

        if (BuildConfig.MAPS_API_KEY.isBlank()) {
            mapSetupHint.visibility = View.VISIBLE
            mapSetupHint.text = getString(R.string.map_api_key_missing)
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        shareSwitch.visibility = View.GONE
        shareSwitch.isClickable = false

        searchNearbyButton.setOnClickListener { showSearchNearbyMenu() }
        openSystemMapsButton.setOnClickListener { showSelfPinActions() }

        bindService(Intent(this, MeshService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = true
        map.mapType = GoogleMap.MAP_TYPE_NORMAL

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }

        map.setOnMarkerClickListener { marker ->
            showPinActions(
                label = marker.title ?: getString(R.string.map_title),
                lat = marker.position.latitude,
                lon = marker.position.longitude,
            )
            true
        }

        map.setOnMapLoadedCallback {
            mapSetupHint.visibility = View.GONE
        }

        refreshMarkers()
    }

    private fun syncUi() {
        val service = meshService ?: return
        val n = service.mutualLocationPeers().size
        statusText.text = if (n > 0) {
            getString(R.string.map_sharing_hint, n)
        } else {
            getString(R.string.map_hidden_hint)
        }
    }

    private fun refreshMarkers() {
        val map = googleMap ?: return
        val service = meshService ?: return
        service.refreshLiveGeoAsync()
        PeerLocationStore.purgeExpired()

        val peers = service.peersWithLocation()
        val names = peers.keys.toSet()

        peerMarkers.keys.filter { it !in names }.forEach { name ->
            peerMarkers.remove(name)?.remove()
        }

        for ((name, snap) in peers) {
            val position = LatLng(snap.lat, snap.lon)
            val existing = peerMarkers[name]
            if (existing != null) {
                existing.position = position
                existing.title = name
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(name)
                        .snippet(getString(R.string.map_peer_tap))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)),
                ) ?: continue
                peerMarkers[name] = marker
            }
        }

        val self = service.myLocationSnapshot()
        val center = when {
            self != null && self.hasCoords() -> LatLng(self.lat, self.lon)
            peers.isNotEmpty() -> {
                val first = peers.values.first()
                LatLng(first.lat, first.lon)
            }
            else -> null
        }
        if (center != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
        }
    }

    private fun searchAnchor(): Pair<Double, Double>? {
        val service = meshService ?: return null
        service.myLocationSnapshot()?.takeIf { it.hasCoords() }?.let { return it.lat to it.lon }
        val peers = service.peersWithLocation()
        if (peers.isNotEmpty()) {
            val first = peers.values.first()
            return first.lat to first.lon
        }
        return null
    }

    private fun showSearchNearbyMenu() {
        val anchor = searchAnchor()
        val options = arrayOf(
            getString(R.string.map_search_hospital),
            getString(R.string.map_search_shelter),
            getString(R.string.map_search_pharmacy),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.map_search_nearby)
            .setItems(options) { _, which ->
                val query = options[which]
                if (anchor != null) {
                    MapsHelper.searchNearby(this, query, anchor.first, anchor.second)
                } else {
                    MapsHelper.searchNearby(this, query)
                }
            }
            .show()
    }

    private fun showSelfPinActions() {
        val service = meshService ?: return
        val snap = service.myLocationSnapshot() ?: run {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        if (!snap.hasCoords()) {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        showPinActions(service.myName, snap.lat, snap.lon)
    }

    private fun showPinActions(label: String, lat: Double, lon: Double) {
        val options = arrayOf(
            getString(R.string.map_open_system),
            getString(R.string.map_navigate_here),
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.map_pin_actions_title, label))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> MapsHelper.openInGoogleMaps(this, lat, lon, label)
                    1 -> MapsHelper.navigateTo(this, lat, lon)
                }
            }
            .show()
    }

    override fun onUpdate() {
        runOnUiThread {
            syncUi()
            refreshMarkers()
        }
    }

    override fun onResume() {
        super.onResume()
        meshService?.setCallback(this)
        refreshMarkers()
    }

    override fun onPause() {
        super.onPause()
        meshService?.setCallback(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) unbindService(connection)
    }
}
