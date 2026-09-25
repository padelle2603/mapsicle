package com.padelle.mapsicle

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.padelle.mapsicle.databinding.ActivityMainBinding
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var suggestions: Suggestions
    private lateinit var singleLocation: SingleLocation

    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null

    // 3 thread: scarica dello stile, routing e ricerca non si bloccano a vicenda.
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(File(cacheDir, "http"), HTTP_CACHE_BYTES))
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private var activeInput: EditText? = null
    private var startPlace: SearchPlace? = null
    private var destinationPlace: SearchPlace? = null
    private var startMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var currentLocationMarker: Marker? = null
    private var currentRoute: RouteResult? = null
    private var latestSuggestions: List<SearchPlace> = emptyList()
    private var routeInFlight = false
    private var routeRequestId = 0
    private var suppressTextChange = false
    private var panelCollapsed = true
    private var guidanceActive = false
    private var locationUpdatesActive = false
    private var cameraFollow = false
    private var pendingPermissionAction = PermissionAction.NONE

    private val appLanguage = resolveAppLanguage(Locale.getDefault())
    private val displayLocale = appLocale(appLanguage)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updatePositionMarker(location)
            if (guidanceActive) {
                updateGuidance(location)
            }
        }

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit

        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(httpClient)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        suggestions = Suggestions(mainHandler, executor, appLanguage) { url -> httpGet(url) }
        suggestions.onSearching = { setSuggestionStatus(getString(R.string.searching_suggestions)) }
        suggestions.onResults = { results ->
            latestSuggestions = results
            showSuggestions(results)
            if (results.isNotEmpty()) {
                setSuggestionStatus("")
            }
        }
        suggestions.onError = { setSuggestionStatus(getString(R.string.suggestions_error)) }

        singleLocation = SingleLocation(
            locationManager(),
            mainHandler,
            Executor { command -> runOnUiThread(command) },
        )

        panelCollapsed = savedInstanceState?.getBoolean(STATE_PANEL_COLLAPSED) ?: true

        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        // toccare la mappa stacca il follow automatico della camera
        mapView.setOnTouchListener { _, _ ->
            cameraFollow = false
            false
        }
        mapView.getMapAsync { loadedMap ->
            map = loadedMap
            loadedMap.uiSettings.isAttributionEnabled = true
            loadMapStyle(loadedMap)
        }

        configurePanel()
        configureSearchField(binding.startInput)
        configureSearchField(binding.destinationInput)
        binding.routeButton.setOnClickListener { calculateRoute() }
        binding.acceptRouteButton.setOnClickListener { acceptRoute() }
        binding.stopNavigationButton.setOnClickListener { closeGuidance() }
        binding.startGpsButton.setOnClickListener { requestLocation() }
        binding.locationButton.setOnClickListener {
            cameraFollow = true
            requestLocation()
        }
        setPanelCollapsed(panelCollapsed)
        if (BuildConfig.MAPTILER_API_KEY.isBlank()) {
            setRouteStatus(getString(R.string.map_key_missing))
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // il segnalino di posizione segue sempre che l'app e in primo piano
        if (hasFineLocation()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        stopLocationUpdates()
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        stopLocationUpdates()
        mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        mapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PANEL_COLLAPSED, panelCollapsed)
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        suggestions.cancel()
        singleLocation.cancel()
        stopLocationUpdates()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun configurePanel() {
        binding.routeHeader.setOnClickListener { setPanelCollapsed(!panelCollapsed) }
        binding.collapseButton.setOnClickListener { setPanelCollapsed(!panelCollapsed) }
        binding.modeGroup.setOnCheckedChangeListener { _, _ ->
            if (startPlace != null && destinationPlace != null) {
                invalidateRoute()
            }
        }
    }

    private fun setPanelCollapsed(collapsed: Boolean) {
        panelCollapsed = collapsed
        binding.panelBody.visibility = if (collapsed) View.GONE else View.VISIBLE
        binding.collapseButton.rotation = if (collapsed) 180f else 0f
        binding.collapseButton.contentDescription = getString(
            if (collapsed) R.string.expand_route_panel else R.string.collapse_route_panel,
        )
        updateHeaderSummary()
    }

    private fun updateHeaderSummary() {
        binding.routeHeaderSummary.text = when {
            guidanceActive -> getString(R.string.navigation_active_summary)
            currentRoute != null && binding.routeSummary.text.isNotBlank() ->
                binding.routeSummary.text.toString()
            else -> getString(R.string.itinerary_empty_summary)
        }
    }

    private fun configureSearchField(field: EditText) {
        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeInput = field
                if (field.text.toString().trim().length >= MIN_QUERY_LENGTH) {
                    suggestions.request(field.text.toString(), immediate = true)
                } else {
                    clearSuggestions()
                }
            }
        }
        field.setOnClickListener { activeInput = field }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(text: Editable?) {
                if (suppressTextChange) {
                    return
                }
                if (field === binding.startInput) {
                    startPlace = null
                    startMarker?.remove()
                    startMarker = null
                } else {
                    destinationPlace = null
                    destinationMarker?.remove()
                    destinationMarker = null
                }
                invalidateRoute()
                if (activeInput === field) {
                    val value = text?.toString().orEmpty()
                    if (value.trim().length >= MIN_QUERY_LENGTH) {
                        suggestions.request(value)
                    } else {
                        clearSuggestions()
                    }
                }
            }
        })
        field.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (submitted) {
                if (latestSuggestions.isNotEmpty()) {
                    selectSuggestion(latestSuggestions.first())
                } else {
                    suggestions.request(field.text.toString(), immediate = true)
                }
                true
            } else {
                false
            }
        }
    }

    private fun showSuggestions(results: List<SearchPlace>) {
        binding.resultsContainer.removeAllViews()
        if (results.isEmpty()) {
            binding.resultsScroll.visibility = View.GONE
            setSuggestionStatus(getString(R.string.suggestions_empty))
            return
        }
        binding.resultsScroll.visibility = View.VISIBLE
        results.forEach { place ->
            val row = TextView(this).apply {
                text = place.displayName
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                minHeight = dp(52)
                isClickable = true
                contentDescription = place.displayName
                setOnClickListener { selectSuggestion(place) }
            }
            binding.resultsContainer.addView(row)
        }
    }

    private fun selectSuggestion(place: SearchPlace) {
        val field = activeInput ?: return
        suppressTextChange = true
        if (field === binding.startInput) {
            startPlace = place
            binding.startInput.setText(place.displayName)
        } else {
            destinationPlace = place
            binding.destinationInput.setText(place.displayName)
        }
        suppressTextChange = false
        invalidateRoute()
        clearSuggestions()
        updateRouteButton()
        updateEndpointMarkers()
        field.clearFocus()
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), 13.0),
        )
    }

    private fun clearSuggestions() {
        suggestions.clear()
        binding.resultsContainer.removeAllViews()
        binding.resultsScroll.visibility = View.GONE
        latestSuggestions = emptyList()
        setSuggestionStatus("")
    }

    private fun invalidateRoute() {
        pendingPermissionAction = PermissionAction.NONE
        guidanceActive = false
        currentRoute = null
        // invalida anche la richiesta in volo, altrimenti puo landingare sulla mappa
        // una rotta calcolata per luoghi che l'utente ha gia cambiato
        routeRequestId += 1
        routeInFlight = false
        renderRoute(EMPTY_ROUTE_GEOJSON)
        // il segnalino di posizione resta: non fa parte della rotta
        binding.routeContent.visibility = View.VISIBLE
        binding.navigationContent.visibility = View.GONE
        binding.acceptRouteButton.visibility = View.GONE
        binding.stopNavigationButton.visibility = View.GONE
        binding.routeSummary.visibility = View.GONE
        setRouteStatus("")
        updateRouteButton()
        updateHeaderSummary()
    }

    private fun calculateRoute() {
        val start = startPlace
        val destination = destinationPlace
        if (start == null || destination == null) {
            setRouteStatus(getString(R.string.route_select_both))
            return
        }
        if (start.latitude == destination.latitude && start.longitude == destination.longitude) {
            setRouteStatus(getString(R.string.route_error))
            return
        }

        currentRoute = null
        renderRoute(EMPTY_ROUTE_GEOJSON)
        val mode = selectedTravelMode()
        val requestId = ++routeRequestId
        binding.routeSummary.visibility = View.GONE
        binding.acceptRouteButton.visibility = View.GONE
        routeInFlight = true
        updateRouteButton()
        setRouteStatus(getString(R.string.route_calculating))
        executor.execute {
            val result = runCatching {
                parseRouteResponse(httpGet(buildRouteUrl(start, destination, mode)))
            }
            mainHandler.post {
                if (isFinishing || isDestroyed || requestId != routeRequestId) {
                    return@post
                }
                routeInFlight = false
                updateRouteButton()
                result.onSuccess { route ->
                    currentRoute = route
                    guidanceActive = false
                    setRouteStatus("")
                    binding.routeSummary.text = getString(
                        R.string.route_summary,
                        formatDistance(route.distanceMeters, displayLocale),
                        formatDuration(route.durationSeconds, displayLocale),
                    )
                    binding.routeSummary.visibility = View.VISIBLE
                    binding.acceptRouteButton.visibility = View.VISIBLE
                    binding.acceptRouteButton.isEnabled = true
                    renderRoute(route.toGeoJson())
                    focusRoute(route)
                    updateEndpointMarkers()
                    updateHeaderSummary()
                }.onFailure {
                    setRouteStatus(getString(R.string.route_error))
                }
            }
        }
    }

    private fun selectedTravelMode(): TravelMode {
        return when (binding.modeGroup.checkedRadioButtonId) {
            R.id.bikeMode -> TravelMode.BICYCLE
            R.id.carMode -> TravelMode.CAR
            else -> TravelMode.WALK
        }
    }

    private fun renderRoute(geoJson: String) {
        map?.getStyle { style ->
            val source = style.getSource(ROUTE_SOURCE_ID)
            if (source == null) {
                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, geoJson))
            } else if (source is GeoJsonSource) {
                source.setGeoJson(geoJson)
            }
            if (style.getLayer(ROUTE_LAYER_ID) == null) {
                style.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        PropertyFactory.lineColor(getColor(R.color.route_line)),
                        PropertyFactory.lineWidth(8f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        PropertyFactory.lineOpacity(1f),
                    ),
                )
            }
        }
    }

    private fun focusRoute(route: RouteResult) {
        val currentMap = map ?: return
        val bounds = LatLngBounds.Builder()
        route.coordinates.forEach { point ->
            bounds.include(LatLng(point.latitude, point.longitude))
        }
        val padding = routeCameraPadding()
        currentMap.animateCamera(
            if (padding == null) {
                CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(48))
            } else {
                CameraUpdateFactory.newLatLngBounds(
                    bounds.build(),
                    padding[0],
                    padding[1],
                    padding[2],
                    padding[3],
                )
            },
        )
    }

    /**
     * Il pannello di ricerca e ancorato in alto: senza un padding asimmetrico la camera
     * centra la rotta sullo schermo e la meta superiore finisce sotto il pannello.
     */
    private fun routeCameraPadding(): IntArray? {
        val mapHeight = binding.mapView.height
        if (mapHeight <= 0) {
            return null
        }
        val panelHeight = binding.searchPanel.height
        if (panelHeight <= 0) {
            return null
        }
        val side = dp(24)
        val bottom = dp(72)
        val top = (panelHeight + dp(12)).coerceAtMost((mapHeight - bottom - dp(48)).coerceAtLeast(side))
        return intArrayOf(side, top, side, bottom)
    }

    private fun acceptRoute() {
        val route = currentRoute ?: return
        if (!hasFineLocation()) {
            pendingPermissionAction = PermissionAction.ACCEPT_ROUTE
            requestLocationPermissions()
            return
        }
        startGuidance(route)
    }

    private fun startGuidance(route: RouteResult) {
        currentRoute = route
        guidanceActive = true
        cameraFollow = true
        pendingPermissionAction = PermissionAction.NONE
        binding.routeContent.visibility = View.GONE
        binding.navigationContent.visibility = View.VISIBLE
        binding.acceptRouteButton.visibility = View.GONE
        binding.stopNavigationButton.visibility = View.VISIBLE
        binding.stopNavigationButton.isEnabled = true
        binding.stopNavigationButton.text = getString(R.string.stop_navigation)
        binding.navigationInstruction.text = getString(R.string.navigation_waiting)
        binding.navigationDistance.text = ""
        binding.navigationProgress.text = ""
        setPanelCollapsed(false)
        updateHeaderSummary()
        startLocationUpdates()
        singleLocation.lastKnown()?.let { location ->
            updatePositionMarker(location)
            updateGuidance(location)
        }
    }

    private fun closeGuidance() {
        if (guidanceActive) {
            guidanceActive = false
            cameraFollow = false
            setRouteStatus(getString(R.string.navigation_stopped))
        }
        // gli update restano attivi: il segnalino di posizione continua a seguire
        binding.routeContent.visibility = View.VISIBLE
        binding.navigationContent.visibility = View.GONE
        binding.acceptRouteButton.visibility = View.VISIBLE
        binding.stopNavigationButton.visibility = View.GONE
        updateRouteButton()
        updateHeaderSummary()
    }

    private fun finishGuidance() {
        guidanceActive = false
        cameraFollow = false
        binding.navigationInstruction.text = getString(R.string.navigation_arrived)
        binding.navigationDistance.text = ""
        binding.navigationProgress.text = getString(R.string.navigation_progress, 100)
        binding.stopNavigationButton.text = getString(R.string.close_navigation)
        updateHeaderSummary()
    }

    private fun startLocationUpdates() {
        if (locationUpdatesActive) {
            return
        }
        if (!hasFineLocation()) {
            return
        }
        val manager = locationManager()
        var requested = false
        try {
            // Solo GPS/fused: il provider network puo sbagliare di centinaia di
            // metri e far saltare il segnalino indietro e avanti.
            locationProviders(manager).forEach { provider ->
                try {
                    manager.requestLocationUpdates(
                        provider,
                        LOCATION_UPDATE_INTERVAL_MS,
                        LOCATION_UPDATE_MIN_DISTANCE_METERS,
                        locationListener,
                        Looper.getMainLooper(),
                    )
                    requested = true
                } catch (_: SecurityException) {
                }
            }
        } catch (_: SecurityException) {
        }
        locationUpdatesActive = requested
    }

    private fun locationProviders(manager: LocationManager): List<String> {
        val enabled = try {
            manager.getProviders(true).toList()
        } catch (_: SecurityException) {
            return emptyList()
        }
        val preferred = enabled.filter { provider ->
            provider == LocationManager.FUSED_PROVIDER || provider == LocationManager.GPS_PROVIDER
        }
        return preferred.ifEmpty { enabled }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesActive) {
            return
        }
        try {
            locationManager().removeUpdates(locationListener)
        } catch (_: SecurityException) {
        }
        locationUpdatesActive = false
    }

    /**
     * Sposta il segnalino di posizione riusando lo stesso Marker: MapLibre ha
     * setPosition(), non serve (e non conviene) rimuovere e ricreare l'annotazione
     * a ogni fix.
     */
    private fun updatePositionMarker(location: Location) {
        val position = LatLng(location.latitude, location.longitude)
        val marker = currentLocationMarker
        if (marker == null) {
            currentLocationMarker = addMarker(
                location.latitude,
                location.longitude,
                R.string.my_location,
            )
        } else {
            marker.position = position
        }
    }

    private fun updateGuidance(location: Location) {
        if (!guidanceActive) {
            return
        }
        val route = currentRoute ?: return
        val progress = calculateRouteProgress(route, location.latitude, location.longitude)

        if (progress.arrived) {
            finishGuidance()
            return
        }

        val nextInstruction = progress.nextStep?.let { getString(it.instructionRes()) }
            ?: getString(R.string.navigation_continue)
        binding.navigationInstruction.text = nextInstruction
        binding.navigationDistance.text = if (progress.nextStep == null) {
            getString(R.string.navigation_arrival_distance, formatDistance(progress.remainingMeters, displayLocale))
        } else {
            getString(
                R.string.navigation_next_step,
                formatDistance(progress.distanceToNextManeuverMeters, displayLocale),
            )
        }
        binding.navigationProgress.text = getString(R.string.navigation_progress, progress.percent)

        val target = progress.nextStep?.let { step ->
            route.coordinates.getOrNull(step.geometryIndex)
        } ?: route.coordinates.last()
        val bearing = bearingDegrees(
            location.latitude,
            location.longitude,
            target.latitude,
            target.longitude,
        )
        // Non forzare lo zoom a ogni fix: se l'utente ha scelto un livello, resta.
        if (cameraFollow) {
            map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(currentMapZoom())
                        .bearing(bearing.toDouble())
                        .build(),
                ),
                CAMERA_ANIMATION_MS,
            )
        }
    }

    private fun currentMapZoom(): Double {
        val current = map?.cameraPosition?.zoom ?: GUIDANCE_ZOOM
        return current.coerceIn(MIN_FOLLOW_ZOOM, MAX_FOLLOW_ZOOM)
    }

    @Suppress("DEPRECATION")
    private fun addMarker(latitude: Double, longitude: Double, titleRes: Int): Marker? {
        return map?.addMarker(
            MarkerOptions()
                .position(LatLng(latitude, longitude))
                .title(getString(titleRes)),
        )
    }

    private fun updateRouteButton() {
        binding.routeButton.isEnabled =
            !routeInFlight && startPlace != null && destinationPlace != null
    }

    @Suppress("DEPRECATION")
    private fun updateEndpointMarkers() {
        startMarker?.remove()
        destinationMarker?.remove()
        startMarker = startPlace?.let { place ->
            addMarker(place.latitude, place.longitude, R.string.start_label)
        }
        destinationMarker = destinationPlace?.let { place ->
            addMarker(place.latitude, place.longitude, R.string.destination_label)
        }
    }

    private fun setStartPlace(place: SearchPlace) {
        invalidateRoute()
        startPlace = place
        suppressTextChange = true
        binding.startInput.setText(place.displayName)
        suppressTextChange = false
        updateRouteButton()
        updateEndpointMarkers()
        updateHeaderSummary()
    }

    private fun loadMapStyle(loadedMap: MapLibreMap) {
        val styleUrl = mapStyleUrl()
        executor.execute {
            val localizedStyle = runCatching {
                Style.Builder()
                    .fromJson(localizeStyleJson(httpGet(styleUrl), appLanguage))
            }.getOrNull()
            mainHandler.post {
                if (isFinishing || isDestroyed) {
                    return@post
                }
                if (localizedStyle != null) {
                    loadedMap.setStyle(localizedStyle) {
                        onMapStyleLoaded(loadedMap)
                    }
                } else {
                    loadedMap.setStyle(styleUrl) {
                        onMapStyleLoaded(loadedMap)
                    }
                }
            }
        }
    }

    private fun onMapStyleLoaded(loadedMap: MapLibreMap) {
        loadedMap.cameraPosition = CameraPosition.Builder()
            .target(LatLng(42.5, 12.5))
            .zoom(5.0)
            .build()
        // Source e layer della rotta vivono solo nel runtime: se lo stile viene ricreato
        // (rotazione, restore di MapView) vanno persi, quindi li riaggiungo qui.
        currentRoute?.let { renderRoute(it.toGeoJson()) }
        updateEndpointMarkers()
    }

    private fun mapStyleUrl(): String {
        val key = BuildConfig.MAPTILER_API_KEY
        return if (key.isBlank()) {
            FALLBACK_STYLE_URL
        } else {
            "$MAPTILER_STYLE_URL?key=${Uri.encode(key)}"
        }
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", appLanguage)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return response.body?.string() ?: error("Empty response body")
        }
    }

    private fun requestLocation() {
        setPanelCollapsed(false)
        if (!hasFineLocation() && !hasCoarseLocation()) {
            pendingPermissionAction = PermissionAction.SET_START_LOCATION
            setRouteStatus(getString(R.string.location_searching))
            requestLocationPermissions()
            return
        }
        setRouteStatus(getString(R.string.location_searching))
        requestCurrentLocation()
    }

    private fun requestCurrentLocation() {
        singleLocation.request { location ->
            if (isFinishing || isDestroyed) {
                return@request
            }
            if (location == null) {
                setRouteStatus(getString(R.string.location_unavailable))
                return@request
            }
            setStartPlace(
                SearchPlace(getString(R.string.my_location), location.latitude, location.longitude),
            )
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 14.0),
            )
            setRouteStatus(getString(R.string.location_found))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_REQUEST_CODE) {
            return
        }
        when (pendingPermissionAction) {
            PermissionAction.ACCEPT_ROUTE -> {
                pendingPermissionAction = PermissionAction.NONE
                if (hasFineLocation()) {
                    currentRoute?.let(::startGuidance)
                } else {
                    setRouteStatus(getString(R.string.location_permission_denied))
                }
            }

            PermissionAction.SET_START_LOCATION -> {
                pendingPermissionAction = PermissionAction.NONE
                if (hasFineLocation() || hasCoarseLocation()) {
                    setRouteStatus(getString(R.string.location_searching))
                    requestCurrentLocation()
                } else {
                    setRouteStatus(getString(R.string.location_permission_denied))
                }
            }

            PermissionAction.NONE -> Unit
        }
    }

    private fun requestLocationPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            LOCATION_REQUEST_CODE,
        )
    }

    private fun hasFineLocation(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocation(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun locationManager(): LocationManager {
        return getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun setSuggestionStatus(message: String) {
        binding.suggestionStatus.text = message
        binding.suggestionStatus.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun setRouteStatus(message: String) {
        binding.routeStatus.text = message
        binding.routeStatus.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class PermissionAction {
        NONE,
        ACCEPT_ROUTE,
        SET_START_LOCATION,
    }

    private companion object {
        const val MAPTILER_STYLE_URL = "https://api.maptiler.com/maps/streets-v4/style.json"
        const val FALLBACK_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val USER_AGENT = "Mapsicle/0.1 (Android; com.padelle.mapsicle)"
        const val LOCATION_REQUEST_CODE = 1001
        const val MIN_QUERY_LENGTH = 3
        const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
        const val LOCATION_UPDATE_MIN_DISTANCE_METERS = 2f
        const val CAMERA_ANIMATION_MS = 600
        const val GUIDANCE_ZOOM = 16.0
        const val MIN_FOLLOW_ZOOM = 12.0
        const val MAX_FOLLOW_ZOOM = 18.0
        const val NETWORK_TIMEOUT_SECONDS = 10L
        const val ROUTE_SOURCE_ID = "route"
        const val ROUTE_LAYER_ID = "route-line"
        const val EMPTY_ROUTE_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        const val HTTP_CACHE_BYTES = 20L * 1024 * 1024
        const val STATE_PANEL_COLLAPSED = "panel_collapsed"
    }
}
