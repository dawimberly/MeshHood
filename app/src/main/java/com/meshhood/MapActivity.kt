package com.meshhood

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

class MapActivity : AppCompatActivity(), MeshService.MeshCallback, OnMapReadyCallback {

    private var meshService: MeshService? = null
    private var bound = false
    private var googleMap: GoogleMap? = null

    private lateinit var shareSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var searchNearbyButton: MaterialButton
    private lateinit var openSystemMapsButton: MaterialButton
    private lateinit var junctionBoxesChip: MaterialButton
    private lateinit var nearestJunctionButton: MaterialButton
    private lateinit var mapSetupHint: TextView
    private lateinit var offlineMapsCard: MaterialCardView
    private val peerMarkers = mutableMapOf<String, Marker>()
    private val feedMarkers = mutableMapOf<String, Marker>()
    private val emergencyMarkers = mutableMapOf<String, Marker>()
    private val junctionBoxMarkers = mutableMapOf<String, Marker>()
    private var emergencyFacilityIcon: BitmapDescriptor? = null
    private val junctionBoxIcons = mutableMapOf<JunctionBoxStore.Tier, BitmapDescriptor>()
    private var highlightedJunctionId: String? = null
    private var emergencyFetchInFlight = false
    private var lastEmergencyAnchor: Pair<Double, Double>? = null

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
        junctionBoxesChip = findViewById(R.id.junctionBoxesChip)
        nearestJunctionButton = findViewById(R.id.nearestJunctionButton)
        mapSetupHint = findViewById(R.id.mapSetupHint)
        offlineMapsCard = findViewById(R.id.offlineMapsCard)

        JunctionBoxStore.load(this)
        updateJunctionChip()

        findViewById<ImageButton>(R.id.mapBackButton).setOnClickListener { finish() }

        bindOfflineMapsCard()

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
        junctionBoxesChip.setOnClickListener { showJunctionLegend() }
        nearestJunctionButton.setOnClickListener { showNearestJunction() }

        bindService(Intent(this, MeshService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun bindOfflineMapsCard() {
        val prefs = getSharedPreferences(PREFS_MAP, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_OFFLINE_CARD_DISMISSED, false)) {
            offlineMapsCard.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.offlineMapsCardDismiss).setOnClickListener {
            offlineMapsCard.visibility = View.GONE
            prefs.edit().putBoolean(KEY_OFFLINE_CARD_DISMISSED, true).apply()
        }
        findViewById<MaterialButton>(R.id.offlineMapsCardAction).setOnClickListener {
            val anchor = userLocationAnchor()
            MapsHelper.openOfflineMapsGuide(this, anchor?.first, anchor?.second)
        }
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
            when (val tag = marker.tag) {
                is JunctionBoxTag -> showJunctionBoxActions(tag.box)
                is EmergencyFacilityTag -> showEmergencyFacilityActions(tag.facility)
                is FeedMarkerTag -> {
                    val label = marker.title ?: getString(R.string.map_title)
                    showPinActions(
                        label = label,
                        lat = marker.position.latitude,
                        lon = marker.position.longitude,
                        bodySnippet = marker.snippet,
                    )
                }
                else -> {
                    val label = marker.title ?: getString(R.string.map_title)
                    showPinActions(
                        label = label,
                        lat = marker.position.latitude,
                        lon = marker.position.longitude,
                    )
                }
            }
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

        refreshPeerMarkers(map, service)
        refreshFeedMarkers(map, service)
        refreshEmergencyFacilityMarkers(map)
        refreshJunctionBoxMarkers(map)

        val self = service.myLocationSnapshot()
        val peers = service.peersWithLocation()
        val center = when {
            self != null && self.hasCoords() -> LatLng(self.lat, self.lon)
            peers.isNotEmpty() -> {
                val first = peers.values.first()
                LatLng(first.lat, first.lon)
            }
            feedMarkers.isNotEmpty() -> feedMarkers.values.first().position
            else -> null
        }
        if (center != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 14f))
        }
    }

    private fun refreshPeerMarkers(map: GoogleMap, service: MeshService) {
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
    }

    private fun refreshFeedMarkers(map: GoogleMap, service: MeshService) {
        val scope = service.feedScope
        val pins = service.feedLines(scope).filter { it.hasMapCoords() }
        val activeKeys = mutableSetOf<String>()

        for (line in pins) {
            val lat = line.mapLat!!
            val lon = line.mapLon!!
            val key = feedMarkerKey(line, lat, lon)
            activeKeys.add(key)
            val pinKind = mapPinKind(line)
            val position = LatLng(lat, lon)
            val title = line.sender.ifBlank { getString(R.string.feed_location_actions_title) }
            val snippet = feedSnippet(line, pinKind)
            val hue = markerHue(pinKind)
            val existing = feedMarkers[key]
            if (existing != null) {
                existing.position = position
                existing.title = title
                existing.snippet = snippet
                existing.tag = FeedMarkerTag(pinKind, line.text)
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(title)
                        .snippet(snippet)
                        .icon(BitmapDescriptorFactory.defaultMarker(hue)),
                ) ?: continue
                marker.tag = FeedMarkerTag(pinKind, line.text)
                feedMarkers[key] = marker
            }
        }

        feedMarkers.keys.filter { it !in activeKeys }.forEach { key ->
            feedMarkers.remove(key)?.remove()
        }
    }

    private fun feedMarkerKey(line: FeedLine, lat: Double, lon: Double): String =
        "${line.kind.name}|${line.sender}|${line.time}|$lat|$lon"

    private fun mapPinKind(line: FeedLine): MapPinKind = when {
        line.kind == FeedKind.AGENCY -> MapPinKind.AGENCY
        line.kind == FeedKind.EMERGENCY -> MapPinKind.EMERGENCY
        isShelterRelated(line.text) -> MapPinKind.SHELTER
        else -> MapPinKind.NEIGHBOR
    }

    private fun isShelterRelated(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("shelter") ||
            t.contains("evacuation") ||
            t.contains("warming center") ||
            t.contains("cooling center") ||
            t.contains("respite")
    }

    private fun feedSnippet(line: FeedLine, kind: MapPinKind): String {
        val typeLabel = when (kind) {
            MapPinKind.AGENCY -> getString(R.string.map_marker_agency_snippet)
            MapPinKind.SHELTER -> getString(R.string.map_marker_shelter_snippet)
            MapPinKind.EMERGENCY -> getString(R.string.map_marker_emergency_snippet)
            MapPinKind.NEIGHBOR -> getString(R.string.map_marker_neighbor_snippet)
        }
        val preview = line.text.trim().take(80)
        return if (preview.isEmpty()) typeLabel else "$typeLabel · $preview"
    }

    private fun markerHue(kind: MapPinKind): Float = when (kind) {
        MapPinKind.AGENCY -> BitmapDescriptorFactory.HUE_RED
        MapPinKind.SHELTER -> BitmapDescriptorFactory.HUE_ORANGE
        MapPinKind.EMERGENCY -> BitmapDescriptorFactory.HUE_ROSE
        MapPinKind.NEIGHBOR -> BitmapDescriptorFactory.HUE_GREEN
    }

    private fun userLocationAnchor(): Pair<Double, Double>? {
        val service = meshService ?: return null
        service.myLocationSnapshot()?.takeIf { it.hasCoords() }?.let { return it.lat to it.lon }
        return null
    }

    private fun updateJunctionChip() {
        junctionBoxesChip.text = getString(R.string.map_junction_chip, JunctionBoxStore.count())
    }

    private fun refreshJunctionBoxMarkers(map: GoogleMap) {
        val boxes = JunctionBoxStore.all()
        val activeIds = mutableSetOf<String>()
        val anchor = userLocationAnchor()
        for (box in boxes) {
            activeIds.add(box.id)
            val position = LatLng(box.lat, box.lon)
            val snippet = junctionBoxSnippet(box, anchor)
            val zIndex = if (box.id == highlightedJunctionId) 3f else 2f
            val existing = junctionBoxMarkers[box.id]
            if (existing != null) {
                existing.position = position
                existing.title = box.name
                existing.snippet = snippet
                existing.tag = JunctionBoxTag(box)
                existing.zIndex = zIndex
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(box.name)
                        .snippet(snippet)
                        .icon(junctionBoxMarkerIcon(box.tier))
                        .zIndex(zIndex),
                ) ?: continue
                marker.tag = JunctionBoxTag(box)
                junctionBoxMarkers[box.id] = marker
            }
        }
        junctionBoxMarkers.keys.filter { it !in activeIds }.forEach { id ->
            junctionBoxMarkers.remove(id)?.remove()
        }
    }

    private fun junctionBoxSnippet(
        box: JunctionBoxStore.JunctionBox,
        anchor: Pair<Double, Double>?,
    ): String {
        val tierLabel = tierLabel(box.tier)
        val statusLabel = when (box.status) {
            JunctionBoxStore.Status.ACTIVE -> getString(R.string.map_junction_status_active)
            JunctionBoxStore.Status.PLANNED -> getString(R.string.map_junction_status_planned)
        }
        val distanceLine = anchor?.let { (lat, lon) ->
            JunctionBoxStore.distanceMeters(box, lat, lon)?.let { meters ->
                getString(R.string.map_junction_distance, meters / 1000.0)
            }
        } ?: getString(R.string.map_junction_distance_unknown)
        return "$tierLabel · $statusLabel · $distanceLine"
    }

    private fun tierLabel(tier: JunctionBoxStore.Tier): String = when (tier) {
        JunctionBoxStore.Tier.URBAN -> getString(R.string.map_junction_tier_urban)
        JunctionBoxStore.Tier.RIDGE -> getString(R.string.map_junction_tier_ridge)
        JunctionBoxStore.Tier.TRAILHEAD -> getString(R.string.map_junction_tier_trailhead)
    }

    private fun junctionBoxMarkerIcon(tier: JunctionBoxStore.Tier): BitmapDescriptor {
        junctionBoxIcons[tier]?.let { return it }
        val color = when (tier) {
            JunctionBoxStore.Tier.URBAN -> ContextCompat.getColor(this, R.color.purple_500)
            JunctionBoxStore.Tier.RIDGE -> ContextCompat.getColor(this, R.color.mesh_amber)
            JunctionBoxStore.Tier.TRAILHEAD -> ContextCompat.getColor(this, R.color.mesh_teal)
        }
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_map_junction_box)?.mutate()
            ?: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE).also {
                junctionBoxIcons[tier] = it
            }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
        DrawableCompat.setTint(drawable, color)
        val size = (resources.displayMetrics.density * 36).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { junctionBoxIcons[tier] = it }
    }

    private fun showJunctionBoxActions(box: JunctionBoxStore.JunctionBox) {
        val anchor = userLocationAnchor()
        val message = buildString {
            append(tierLabel(box.tier))
            append(" · ")
            append(
                when (box.status) {
                    JunctionBoxStore.Status.ACTIVE -> getString(R.string.map_junction_status_active)
                    JunctionBoxStore.Status.PLANNED -> getString(R.string.map_junction_status_planned)
                },
            )
            append('\n')
            anchor?.let { (lat, lon) ->
                JunctionBoxStore.distanceMeters(box, lat, lon)?.let { meters ->
                    append(getString(R.string.map_junction_distance, meters / 1000.0))
                    append('\n')
                }
            } ?: run {
                append(getString(R.string.map_junction_distance_unknown))
                append('\n')
            }
            if (box.notes.isNotBlank()) {
                append(box.notes)
            }
        }.trim()
        AlertDialog.Builder(this)
            .setTitle(box.name)
            .setMessage(message)
            .setPositiveButton(R.string.map_navigate_here) { _, _ ->
                MapsHelper.navigateTo(this, box.lat, box.lon)
            }
            .setNeutralButton(R.string.map_open_system) { _, _ ->
                MapsHelper.openInGoogleMaps(this, box.lat, box.lon, box.name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showJunctionLegend() {
        AlertDialog.Builder(this)
            .setTitle(R.string.map_junction_legend_title)
            .setMessage(R.string.map_junction_legend_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showNearestJunction() {
        val anchor = userLocationAnchor()
        if (anchor == null) {
            Toast.makeText(this, R.string.map_no_location, Toast.LENGTH_SHORT).show()
            return
        }
        val nearest = JunctionBoxStore.findNearest(anchor.first, anchor.second) ?: return
        highlightedJunctionId = nearest.id
        googleMap?.let { map ->
            refreshJunctionBoxMarkers(map)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(nearest.lat, nearest.lon), 13f))
        }
        Toast.makeText(this, getString(R.string.map_junction_nearest_toast, nearest.name), Toast.LENGTH_SHORT).show()
        showJunctionBoxActions(nearest)
    }

    private fun refreshEmergencyFacilityMarkers(map: GoogleMap) {
        val anchor = userLocationAnchor() ?: run {
            clearEmergencyMarkers()
            lastEmergencyAnchor = null
            return
        }
        if (emergencyFetchInFlight && anchor == lastEmergencyAnchor) return
        if (anchor != lastEmergencyAnchor) {
            clearEmergencyMarkers()
            lastEmergencyAnchor = anchor
        }
        if (emergencyFetchInFlight) return
        emergencyFetchInFlight = true
        EmergencyPlacesCache.fetchNearby(
            context = this,
            lat = anchor.first,
            lon = anchor.second,
        ) { result ->
            runOnUiThread {
                emergencyFetchInFlight = false
                when (result) {
                    is EmergencyPlacesCache.Result.Success,
                    is EmergencyPlacesCache.Result.Cached,
                    -> {
                        val facilities = when (result) {
                            is EmergencyPlacesCache.Result.Success -> result.facilities
                            is EmergencyPlacesCache.Result.Cached -> result.facilities
                            else -> emptyList()
                        }
                        applyEmergencyFacilityMarkers(map, facilities)
                    }
                    is EmergencyPlacesCache.Result.PlacesDisabled -> {
                        mapSetupHint.visibility = View.VISIBLE
                        mapSetupHint.text = result.message
                    }
                    is EmergencyPlacesCache.Result.ApiKeyMissing -> {
                        mapSetupHint.visibility = View.VISIBLE
                        mapSetupHint.text = result.message
                    }
                    is EmergencyPlacesCache.Result.Error -> {
                        // Silent on transient errors; mesh pins still show.
                    }
                }
            }
        }
    }

    private fun applyEmergencyFacilityMarkers(
        map: GoogleMap,
        facilities: List<EmergencyPlacesCache.Facility>,
    ) {
        val icon = emergencyFacilityMarkerIcon()
        val activeIds = mutableSetOf<String>()
        for (facility in facilities) {
            activeIds.add(facility.id)
            val position = LatLng(facility.lat, facility.lon)
            val snippet = facilitySnippet(facility)
            val existing = emergencyMarkers[facility.id]
            if (existing != null) {
                existing.position = position
                existing.title = facility.name
                existing.snippet = snippet
                existing.tag = EmergencyFacilityTag(facility)
            } else {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(facility.name)
                        .snippet(snippet)
                        .icon(icon)
                        .zIndex(1f),
                ) ?: continue
                marker.tag = EmergencyFacilityTag(facility)
                emergencyMarkers[facility.id] = marker
            }
        }
        emergencyMarkers.keys.filter { it !in activeIds }.forEach { id ->
            emergencyMarkers.remove(id)?.remove()
        }
    }

    private fun clearEmergencyMarkers() {
        emergencyMarkers.values.forEach { it.remove() }
        emergencyMarkers.clear()
    }

    private fun facilitySnippet(facility: EmergencyPlacesCache.Facility): String {
        val typeLabel = when (facility.category) {
            EmergencyPlacesCache.Category.HOSPITAL -> getString(R.string.map_marker_hospital_snippet)
            EmergencyPlacesCache.Category.PHARMACY -> getString(R.string.map_marker_pharmacy_snippet)
            EmergencyPlacesCache.Category.FIRE_STATION -> getString(R.string.map_marker_fire_snippet)
            EmergencyPlacesCache.Category.SHELTER -> getString(R.string.map_marker_shelter_facility_snippet)
        }
        val address = facility.address.trim()
        return if (address.isEmpty()) typeLabel else "$typeLabel · $address"
    }

    private fun emergencyFacilityMarkerIcon(): BitmapDescriptor {
        emergencyFacilityIcon?.let { return it }
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_map_emergency_facility)
            ?: return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE).also {
                emergencyFacilityIcon = it
            }
        val size = (resources.displayMetrics.density * 36).toInt().coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { emergencyFacilityIcon = it }
    }

    private fun showEmergencyFacilityActions(facility: EmergencyPlacesCache.Facility) {
        val message = facilitySnippet(facility)
        AlertDialog.Builder(this)
            .setTitle(facility.name)
            .setMessage(message)
            .setPositiveButton(R.string.map_navigate_here) { _, _ ->
                MapsHelper.navigateTo(this, facility.lat, facility.lon)
            }
            .setNeutralButton(R.string.map_open_system) { _, _ ->
                MapsHelper.openInGoogleMaps(this, facility.lat, facility.lon, facility.name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSearchNearbyMenu() {
        val options = arrayOf(
            getString(R.string.map_search_hospital),
            getString(R.string.map_search_shelter),
            getString(R.string.map_search_pharmacy),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.map_search_nearby)
            .setItems(options) { _, which ->
                val query = options[which]
                val coords = userLocationAnchor()
                MapsHelper.searchNearby(this, query, coords?.first, coords?.second)
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

    private fun showPinActions(
        label: String,
        lat: Double,
        lon: Double,
        bodySnippet: String? = null,
    ) {
        val options = arrayOf(
            getString(R.string.map_open_system),
            getString(R.string.map_navigate_here),
        )
        val message = bodySnippet?.takeIf { it.isNotBlank() }
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.map_pin_actions_title, label))
        if (message != null) {
            builder.setMessage(message)
        }
        builder
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

    private enum class MapPinKind { AGENCY, SHELTER, NEIGHBOR, EMERGENCY }

    private data class FeedMarkerTag(val kind: MapPinKind, val body: String)

    private data class EmergencyFacilityTag(val facility: EmergencyPlacesCache.Facility)

    private data class JunctionBoxTag(val box: JunctionBoxStore.JunctionBox)

    companion object {
        private const val PREFS_MAP = "map_prefs"
        private const val KEY_OFFLINE_CARD_DISMISSED = "offline_maps_card_dismissed"

        fun openOfflineMapsGuide(context: Context, lat: Double?, lon: Double?) {
            MapsHelper.openOfflineMapsGuide(context, lat, lon)
        }
    }
}
