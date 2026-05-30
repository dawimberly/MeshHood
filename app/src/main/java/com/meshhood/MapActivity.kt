package com.meshhood

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity(), MeshService.MeshCallback {

    private var meshService: MeshService? = null
    private var bound = false

    private lateinit var mapView: MapView
    private lateinit var shareSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var openSystemMapsButton: MaterialButton
    private var myLocationOverlay: MyLocationNewOverlay? = null
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
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        shareSwitch = findViewById(R.id.shareLocationSwitch)
        statusText = findViewById(R.id.mapStatusText)
        openSystemMapsButton = findViewById(R.id.openSystemMapsButton)

        findViewById<ImageButton>(R.id.mapBackButton).setOnClickListener { finish() }

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).also {
            it.enableMyLocation()
            mapView.overlays.add(it)
        }

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

        openSystemMapsButton.setOnClickListener { openSelfInSystemMaps() }

        bindService(Intent(this, MeshService::class.java), connection, Context.BIND_AUTO_CREATE)
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
        val service = meshService ?: return
        service.refreshLiveGeoAsync()
        PeerLocationStore.purgeExpired()

        val peers = service.peersWithLocation()
        val names = peers.keys.toSet()

        peerMarkers.keys.filter { it !in names }.forEach { name ->
            mapView.overlays.remove(peerMarkers.remove(name))
        }

        for ((name, snap) in peers) {
            val marker = peerMarkers.getOrPut(name) {
                Marker(mapView).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { m, _ ->
                        MapsHelper.openInMaps(this@MapActivity, m.position.latitude, m.position.longitude, name)
                        true
                    }
                    mapView.overlays.add(this)
                }
            }
            marker.position = GeoPoint(snap.lat, snap.lon)
            marker.title = name
            marker.snippet = getString(R.string.map_peer_tap)
            marker.icon = peerDrawable()
        }

        val self = service.myLocationSnapshot()
        if (self != null && self.hasCoords()) {
            mapView.controller.setCenter(GeoPoint(self.lat, self.lon))
        } else if (peers.isNotEmpty()) {
            val first = peers.values.first()
            mapView.controller.setCenter(GeoPoint(first.lat, first.lon))
        }

        mapView.invalidate()
    }

    private fun peerDrawable(): Drawable? {
        val d = resources.getDrawable(R.drawable.avatar_circle, theme)
        d.setTint(getColor(R.color.mesh_primary))
        return d
    }

    private fun openSelfInSystemMaps() {
        val service = meshService ?: return
        val snap = service.myLocationSnapshot() ?: run {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        if (!snap.hasCoords()) {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        MapsHelper.openInMaps(this, snap.lat, snap.lon, service.myName)
    }

    override fun onUpdate() {
        runOnUiThread {
            syncUi()
            refreshMarkers()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        meshService?.setCallback(this)
        refreshMarkers()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        meshService?.setCallback(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) unbindService(connection)
    }
}
