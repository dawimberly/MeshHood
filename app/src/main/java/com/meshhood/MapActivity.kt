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

        shareSwitch.setOnCheckedChangeListener { _, checked ->
            val service = meshService ?: return@setOnCheckedChangeListener
            if (service.isLocationSharing() == checked) return@setOnCheckedChangeListener
            service.setLocationSharing(checked)
            Toast.makeText(
                this,
                if (checked) R.string.location_share_on_toast else R.string.location_share_off_toast,
                Toast.LENGTH_SHORT,
            ).show()
            syncUi()
        }

        openSystemMapsButton.setOnClickListener { openSelfInGoogleMaps() }

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
            MapsHelper.openInGoogleMaps(
                this,
                marker.position.latitude,
                marker.position.longitude,
                marker.title ?: "",
            )
            true
        }

        refreshMarkers()
    }

    private fun syncUi() {
        val service = meshService ?: return
        shareSwitch.isChecked = service.isLocationSharing()
        statusText.text = when {
            service.isLocationSharing() -> getString(R.string.map_sharing_hint)
            else -> getString(R.string.map_hidden_hint)
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

    private fun openSelfInGoogleMaps() {
        val service = meshService ?: return
        val snap = service.myLocationSnapshot() ?: run {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        if (!snap.hasCoords()) {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        MapsHelper.openInGoogleMaps(this, snap.lat, snap.lon, service.myName)
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
