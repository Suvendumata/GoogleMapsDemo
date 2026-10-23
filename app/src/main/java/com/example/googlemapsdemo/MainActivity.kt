package com.example.googlemapsdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var currentMarker: Marker? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Permission request launcher for Fine and Coarse location
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableMyLocationOverlay()
            moveToCurrentLocation()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration (essential for fetching tiles without API key)
        Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Setup Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Adjust top window inset for toolbar to avoid status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, systemBarInsets.top, 0, 0)
            insets
        }

        // Initialize Fused Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize OpenStreetMap View
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)

        val controller = mapView.controller
        controller.setZoom(13.0)

        // Initial map centered on Bengaluru, India with marker
        val bengaluru = GeoPoint(12.9716, 77.5946)
        controller.setCenter(bengaluru)

        addMarker(
            bengaluru,
            getString(R.string.initial_location_title),
            getString(R.string.initial_location_snippet)
        )

        // Setup Floating Action Control Buttons
        findViewById<FloatingActionButton>(R.id.fabZoomIn).setOnClickListener {
            controller.zoomIn()
        }

        findViewById<FloatingActionButton>(R.id.fabZoomOut).setOnClickListener {
            controller.zoomOut()
        }

        findViewById<FloatingActionButton>(R.id.fabMyLocation).setOnClickListener {
            checkAndRequestLocationPermissions()
        }

        if (hasLocationPermission()) {
            enableMyLocationOverlay()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView

        searchItem?.expandActionView()

        searchView?.apply {
            queryHint = getString(R.string.search_hint)
            isIconified = false
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchLocation(query)
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return false
                }
            })
        }

        return true
    }

    private fun addMarker(point: GeoPoint, title: String, snippet: String) {
        currentMarker?.let {
            mapView.overlays.remove(it)
        }
        val marker = Marker(mapView).apply {
            position = point
            this.title = title
            this.snippet = snippet
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        currentMarker = marker
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun searchLocation(locationName: String?) {
        if (locationName.isNullOrBlank()) {
            Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
            return
        }

        val query = locationName.trim()
        val geocoder = Geocoder(this, Locale.getDefault())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                handleGeocodeResults(addresses, query)
                            }
                        }

                        override fun onError(errorMessage: String?) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.search_error, errorMessage ?: "Unknown error"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    })
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(query, 1)
                    withContext(Dispatchers.Main) {
                        handleGeocodeResults(addresses, query)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.search_error, "Network error or location not found"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.location_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handleGeocodeResults(addresses: List<Address>?, query: String) {
        if (addresses.isNullOrEmpty()) {
            Toast.makeText(this, R.string.location_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val address = addresses[0]
        val geoPoint = GeoPoint(address.latitude, address.longitude)

        addMarker(geoPoint, query, address.getAddressLine(0) ?: query)

        mapView.controller.animateTo(geoPoint)
        mapView.controller.setZoom(15.0)
        Toast.makeText(this, "Found: $query", Toast.LENGTH_SHORT).show()
    }

    private fun checkAndRequestLocationPermissions() {
        if (hasLocationPermission()) {
            enableMyLocationOverlay()
            moveToCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableMyLocationOverlay() {
        if (myLocationOverlay == null) {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            mapView.overlays.add(myLocationOverlay)
        }
        myLocationOverlay?.enableMyLocation()
    }

    @SuppressLint("MissingPermission")
    private fun moveToCurrentLocation() {
        if (!hasLocationPermission()) return

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    mapView.controller.animateTo(geoPoint)
                    mapView.controller.setZoom(15.0)
                } else {
                    Toast.makeText(this, "Unable to fetch current location", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
