// In MapLibre 13 the whole annotation API (Marker, MarkerOptions, Icon, IconFactory,
// addMarker) is deprecated in favour of symbol layers. We use it anyway: the three
// markers are static annotations, not data, and replacing them with symbol layers would
// cost more code for an advantage that is not visible here. Suppressed for the whole
// file to avoid 11 identical warnings on every build.
@file:Suppress("DEPRECATION")

package com.padelle.mapsicle

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.method.ScrollingMovementMethod
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.padelle.mapsicle.databinding.ActivityMainBinding
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
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
import org.maplibre.geojson.Point
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

    // 3 threads: style download, routing and search do not block each other.
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val updateCheck = UpdateCheck(
        activity = this,
        handler = mainHandler,
        executor = executor,
        currentVersion = BuildConfig.VERSION_NAME,
        fetchJson = ::httpGet,
    )

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
    private var startAtUserPosition = false
    private var pendingPlace: SearchPlace? = null
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

    // Last fix received: needed to recreate the marker when the style is reloaded and to
    // decide where to put the camera on the first start.
    private var lastUserFix: Location? = null
    private var userLocated = false

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
        suggestions.onSearching = {
            // Only if there is nothing to show yet: otherwise the "searching" text
            // blinks on every key pressed.
            if (latestSuggestions.isEmpty()) {
                setSuggestionStatus(getString(R.string.searching_suggestions))
            }
        }
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
        // touching the map breaks the automatic camera follow
        mapView.setOnTouchListener { _, _ ->
            cameraFollow = false
            false
        }
        mapView.getMapAsync { loadedMap ->
            map = loadedMap
            loadedMap.uiSettings.isAttributionEnabled = true
            loadedMap.addOnMapClickListener { latLng -> onMapClick(latLng) }
            loadMapStyle(loadedMap)
        }
        updateCheck.check()
        configurePanel()
        configureSearchField(binding.startInput)
        configureSearchField(binding.destinationInput)
        binding.routeButton.setOnClickListener { calculateRoute() }
        binding.acceptRouteButton.setOnClickListener { acceptRoute() }
        binding.stopNavigationButton.setOnClickListener {
            // "End route" closes and clears, "Close route" (after the arrival) leaves
            // the itinerary as it was
            if (guidanceActive) clearItinerary() else closeGuidance()
        }
        binding.clearRouteButton.setOnClickListener { clearItinerary() }
        binding.mapCredit.setOnClickListener { showLicenses() }
        binding.startGpsButton.setOnClickListener { requestLocation() }
        binding.locationButton.setOnClickListener {
            // the bottom right button only centres: it must not overwrite the departure
            // field, or it would move what you had typed.
            if (!hasFineLocation() && !hasCoarseLocation()) {
                pendingPermissionAction = PermissionAction.CENTER_ON_USER
                requestLocationPermissions()
            } else {
                recenterOnUser()
            }
        }
        setPanelCollapsed(panelCollapsed)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // the position marker follows whenever the app is in the foreground
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
        if (field === binding.startInput) {
            setStartPlace(place, fromUserPosition = false)
        } else {
            setDestinationPlace(place)
        }
        clearSuggestions()
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
        // it also invalidates the request in flight, otherwise a route calculated for
        // places the user has already changed can land on the map
        routeRequestId += 1
        routeInFlight = false
        renderRoute(EMPTY_ROUTE_GEOJSON)
        // the position marker stays: it is not part of the route
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
                if (isGone() || requestId != routeRequestId) {
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
     * The search panel is anchored at the top: without an asymmetric padding the camera
     * centres the route on the screen and the upper half ends up under the panel.
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
        // the updates stay on: the position marker keeps following
        binding.routeContent.visibility = View.VISIBLE
        binding.navigationContent.visibility = View.GONE
        binding.acceptRouteButton.visibility = View.VISIBLE
        binding.stopNavigationButton.visibility = View.GONE
        updateRouteButton()
        updateHeaderSummary()
    }

    /**
     * Empties departure and destination. The TextWatchers do the rest: they clear the
     * places, remove the markers, invalidate the route and the suggestions of the
     * focused field.
     */
    private fun clearItinerary() {
        closeGuidance()
        binding.startInput.setText("")
        binding.destinationInput.setText("")
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
            // GPS/fused only: the network provider can be hundreds of metres off and
            // make the marker jump back and forth.
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
        // One provider only: fused and GPS together both arrive on the same listener
        // (up to 2 fix/s), so all the per-fix work was being done twice.
        val fused = LocationManager.FUSED_PROVIDER
        val gps = LocationManager.GPS_PROVIDER
        return when {
            fused in enabled -> listOf(fused)
            gps in enabled -> listOf(gps)
            else -> enabled
        }
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
     * Moves the position marker by reusing the same Marker: MapLibre has setPosition(),
     * so there is no need (and it is not advisable) to remove and recreate the annotation
     * on every fix.
     */
    private fun updatePositionMarker(location: Location) {
        lastUserFix = location
        userLocated = true
        // Every fix arrives here (startup cache, one-shot, live), so this is the only
        // place where the search learns where we are: Photon uses it only to sort the
        // results, not to filter, so a search far away keeps working.
        suggestions.center = SearchCenter(location.latitude, location.longitude)
        val position = LatLng(location.latitude, location.longitude)
        val marker = currentLocationMarker
        if (marker == null) {
            currentLocationMarker = addMarker(
                location.latitude,
                location.longitude,
                R.string.my_location,
                R.drawable.ic_user_position,
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
        val distanceText = if (progress.nextStep == null) {
            getString(R.string.navigation_arrival_distance, formatDistance(progress.remainingMeters, displayLocale))
        } else {
            getString(
                R.string.navigation_next_step,
                formatDistance(progress.distanceToNextManeuverMeters, displayLocale),
            )
        }
        val progressText = getString(R.string.navigation_progress, progress.percent)
        // setText re-lays out and re-measures even when the string has not changed: 3 of
        // those checks on every fix, once or twice a second, cost more than the progress
        // computation.
        if (binding.navigationInstruction.text != nextInstruction) {
            binding.navigationInstruction.text = nextInstruction
        }
        if (binding.navigationDistance.text != distanceText) {
            binding.navigationDistance.text = distanceText
        }
        if (binding.navigationProgress.text != progressText) {
            binding.navigationProgress.text = progressText
        }

        val target = progress.nextStep?.let { step ->
            route.coordinates.getOrNull(step.geometryIndex)
        } ?: route.coordinates.last()
        val bearing = bearingDegrees(
            location.latitude,
            location.longitude,
            target.latitude,
            target.longitude,
        )
        // Do not force the zoom on every fix: if the user has picked a level, it stays.
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
    /**
     * IconFactory.fromResource() in MapLibre 13 only accepts BitmapDrawable and throws
     * IllegalArgumentException on a vector drawable, so I rasterise it by hand.
     * 96px for the 24dp circle: plenty even on xxxhdpi screens.
     */
    private fun bitmapIcon(iconRes: Int): Icon? {
        val drawable = resources.getDrawable(iconRes, theme) ?: return null
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return IconFactory.getInstance(this).fromBitmap(bitmap)
    }

    private fun addMarker(
        latitude: Double,
        longitude: Double,
        titleRes: Int,
        iconRes: Int = 0,
    ): Marker? {
        val loadedMap = map ?: return null
        val options = MarkerOptions()
            .position(LatLng(latitude, longitude))
            .title(getString(titleRes))
        if (iconRes != 0) {
            bitmapIcon(iconRes)?.let { options.setIcon(it) }
        }
        return loadedMap.addMarker(options)
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

    private fun setStartPlace(place: SearchPlace, fromUserPosition: Boolean) {
        invalidateRoute()
        startPlace = place
        startAtUserPosition = fromUserPosition
        suppressTextChange = true
        binding.startInput.setText(place.displayName)
        suppressTextChange = false
        updateRouteButton()
        updateEndpointMarkers()
        updateHeaderSummary()
    }

    private fun setDestinationPlace(place: SearchPlace) {
        invalidateRoute()
        destinationPlace = place
        suppressTextChange = true
        binding.destinationInput.setText(place.displayName)
        suppressTextChange = false
        updateRouteButton()
        updateEndpointMarkers()
    }

    /**
     * Touching a POI opens the card with the name, the category and the distance.
     * The query is a 16dp rectangle around the touch instead of the exact point: the
     * tile icons are 11px and hitting one with a finger is impossible, so the target is
     * widened here rather than with an invisible layer.
     */
    private fun onMapClick(latLng: LatLng): Boolean {
        val loadedMap = map ?: return false
        val screen = loadedMap.getProjection().toScreenLocation(latLng)
        val reach = dp(POI_TOUCH_DP).toFloat()
        val tapped = RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach)
        val place = loadedMap
            .queryRenderedFeatures(tapped, POI_ICON_LAYER, POI_LABEL_LAYER)
            .mapNotNull { feature -> (feature.geometry() as? Point)?.let { feature to it } }
            .minByOrNull { (_, point) ->
                val east = point.longitude() - latLng.longitude
                val north = point.latitude() - latLng.latitude
                (east * east + north * north).toFloat()
            }
            ?: return false
        val properties = place.first.properties() ?: return false
        val name = properties.optLocalName(appLanguage) ?: return false
        showPlaceSheet(
            SearchPlace(name, place.second.latitude(), place.second.longitude()),
            poiCategoryLabelRes(properties.get("class")?.asString.orEmpty()),
        )
        return true
    }

    private fun showPlaceSheet(place: SearchPlace, categoryRes: Int) {
        val fix = lastUserFix
        val category = getString(categoryRes)
        val message = if (fix == null) {
            category
        } else {
            // I reuse the "%1$s · %2$s" format of the route summary: it is the same fact
            getString(
                R.string.route_summary,
                category,
                formatDistance(
                    distanceMeters(
                        place.toRoutePoint(),
                        RoutePoint(fix.longitude, fix.latitude),
                    ),
                    displayLocale,
                ),
            )
        }
        AlertDialog.Builder(this)
            .setTitle(place.displayName)
            .setMessage(message)
            .setPositiveButton(R.string.place_directions) { _, _ -> routeToPlace(place) }
            .setNeutralButton(R.string.place_open_in_google_maps) { _, _ -> openPlaceInGoogleMaps(place) }
            .setNegativeButton(R.string.about_close, null)
            .show()
    }

    /**
     * A chain name alone is a category, so Google answers with every branch instead of the one
     * tapped: the address is what pins it down, and only Photon has one. The name of the POI
     * biased on its own coordinates already gives the address, so this is the same request the
     * search field makes. It fails to the name-only link, which is what the button did before,
     * so a slow or absent network costs the fix for a chain and nothing else.
     */
    private fun openPlaceInGoogleMaps(place: SearchPlace) {
        Toast.makeText(this, R.string.place_address_searching, Toast.LENGTH_SHORT).show()
        val url = buildSuggestionUrl(
            place.displayName,
            appLanguage,
            SearchCenter(place.latitude, place.longitude),
        )
        executor.execute {
            val addresses = runCatching { parsePlaceAddresses(httpGet(url)) }.getOrNull().orEmpty()
            mainHandler.post {
                if (isGone()) {
                    return@post
                }
                openExternalUrl(buildPlaceUrl(resolvePlaceQuery(place, addresses)))
            }
        }
    }

    /**
     * "Directions" on a POI: it starts from my position if the departure field is empty or
     * already holds my position, otherwise it respects the typed departure. If there is no
     * fix yet, we come back here when it arrives, or after the permission.
     */
    private fun routeToPlace(place: SearchPlace) {
        if (startPlace == null || startAtUserPosition) {
            if (!hasFineLocation()) {
                pendingPlace = place
                pendingPermissionAction = PermissionAction.ROUTE_TO_PLACE
                setRouteStatus(getString(R.string.location_searching))
                requestLocationPermissions()
                return
            }
            val fix = lastUserFix ?: singleLocation.lastKnown()
            if (fix == null) {
                pendingPlace = place
                setRouteStatus(getString(R.string.location_searching))
                singleLocation.request { fresh -> fresh?.let { routeToPlace(place) } }
                return
            }
            updatePositionMarker(fix)
            setStartPlace(
                SearchPlace(getString(R.string.my_location), fix.latitude, fix.longitude),
                fromUserPosition = true,
            )
        }
        pendingPlace = null
        setDestinationPlace(place)
        setPanelCollapsed(false)
        calculateRoute()
    }

    private fun loadMapStyle(loadedMap: MapLibreMap) {
        val styleUrl = STYLE_URL
        executor.execute {
            val localizedStyle = runCatching {
                Style.Builder()
                    .fromJson(buildMapStyleJson(httpGet(styleUrl), appLanguage))
            }.getOrNull()
            mainHandler.post {
                if (isGone()) {
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
        // Reloading the style must not drop the camera back on Italy if the user has
        // already positioned themselves: the default applies only on the first load.
        if (userLocated) {
            lastUserFix?.let { centerOnUser(it, animate = false) }
        } else {
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(DEFAULT_TARGET_LAT, DEFAULT_TARGET_LON))
                .zoom(DEFAULT_ZOOM)
                .build()
        }
        // The route source and layer live only in the runtime: if the style is recreated
        // (rotation, MapView restore) they are lost, so I add them back here.
        currentRoute?.let { renderRoute(it.toGeoJson()) }
        updateEndpointMarkers()
        // the annotations of the position marker disappear with the style reload:
        // without this reset currentLocationMarker would keep pointing at a dead Marker
        // and the fix below would recreate nothing.
        currentLocationMarker = null
        lastUserFix?.let { updatePositionMarker(it) }
        if (!userLocated) {
            locateUserOnStartup()
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

    /**
     * Opens the app already centred on where you are, without asking anything: it acts
     * only if the permission has already been granted. First it shows at once the last
     * position known to the system (the cached one) and then it refines with the live fix
     * as soon as it arrives, so the camera stops right away on the right area instead of
     * waiting for the GPS fix.
     */
    private fun locateUserOnStartup() {
        if (!hasFineLocation() && !hasCoarseLocation()) {
            return
        }
        singleLocation.lastKnown()?.let { cached ->
            centerOnUser(cached, animate = false)
        }
        singleLocation.request { fresh ->
            if (isGone() || fresh == null) {
                return@request
            }
            centerOnUser(fresh, animate = true)
        }
    }

    private fun recenterOnUser() {
        setRouteStatus(getString(R.string.location_searching))
        cameraFollow = true
        val cached = singleLocation.lastKnown()
        cached?.let { centerOnUser(it, animate = false) }
        singleLocation.request { fresh ->
            if (isGone()) {
                return@request
            }
            if (fresh == null) {
                if (cached == null) {
                    setRouteStatus(getString(R.string.location_unavailable))
                }
                return@request
            }
            centerOnUser(fresh, animate = true)
            setRouteStatus(getString(R.string.location_found))
        }
    }

    private fun centerOnUser(location: Location, animate: Boolean) {
        updatePositionMarker(location)
        val target = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(STARTUP_ZOOM)
            .build()
        if (animate) {
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(target), CAMERA_ANIMATION_MS)
        } else {
            map?.cameraPosition = target
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
            if (isGone()) {
                return@request
            }
            if (location == null) {
                setRouteStatus(getString(R.string.location_unavailable))
                return@request
            }
            setStartPlace(
                SearchPlace(getString(R.string.my_location), location.latitude, location.longitude),
                fromUserPosition = true,
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
        if (hasFineLocation() || hasCoarseLocation()) {
            // onResume has already passed before the dialog: without this the updates do
            // not start until the app comes back to the foreground.
            startLocationUpdates()
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

            PermissionAction.CENTER_ON_USER -> {
                pendingPermissionAction = PermissionAction.NONE
                if (hasFineLocation() || hasCoarseLocation()) {
                    recenterOnUser()
                } else {
                    setRouteStatus(getString(R.string.location_permission_denied))
                }
            }

            PermissionAction.ROUTE_TO_PLACE -> {
                pendingPermissionAction = PermissionAction.NONE
                // The precise position is needed: with only the approximate one the route
                // would start up to 1 km off, so no return to routeToPlace().
                if (hasFineLocation()) {
                    pendingPlace?.let(::routeToPlace)
                } else {
                    pendingPlace = null
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

    // Tappable OSM credit: this is where the obligation to deliver the licences ends
    // (Apache-2.0 sections 4a/4d). A scrollable TextView inside the dialog: 15 KB of
    // text, no WebView and no new dependency. No textIsSelectable: it clashes with the
    // drag for scrolling.
    private fun showLicenses() {
        val text = TextView(this).apply {
            movementMethod = ScrollingMovementMethod()
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        text.text = assets.open("licenses.txt").bufferedReader().use { it.readText() }
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setView(text)
            .setPositiveButton(R.string.about_close, null)
            .show()
    }

    private enum class PermissionAction {
        NONE,
        ACCEPT_ROUTE,
        SET_START_LOCATION,
        CENTER_ON_USER,
        ROUTE_TO_PLACE,
    }

    private companion object {
        // OpenFreeMap: free and with no API key. Liberty is the classic OSM style in
        // colour (green parks, blue water); 111 layers / 43 KB against the 160 layers /
        // 167 KB of MapTiler Streets.
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        val USER_AGENT = "Mapsicle/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"
        const val LOCATION_REQUEST_CODE = 1001
        const val MIN_QUERY_LENGTH = 3
        const val LOCATION_UPDATE_INTERVAL_MS = 1_000L
        const val LOCATION_UPDATE_MIN_DISTANCE_METERS = 2f
        const val CAMERA_ANIMATION_MS = 600
        const val GUIDANCE_ZOOM = 16.0
        const val MIN_FOLLOW_ZOOM = 12.0
        const val MAX_FOLLOW_ZOOM = 18.0
        // Zoom for "centre me here": close enough to get your bearings, not so close as
        // to lose the context.
        const val STARTUP_ZOOM = 15.0
        const val DEFAULT_TARGET_LAT = 42.5
        const val DEFAULT_TARGET_LON = 12.5
        const val DEFAULT_ZOOM = 5.0
        const val NETWORK_TIMEOUT_SECONDS = 10L
        const val ROUTE_SOURCE_ID = "route"
        const val ROUTE_LAYER_ID = "route-line"
        const val POI_TOUCH_DP = 16
        const val EMPTY_ROUTE_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
        // Style, tiles, sprite, fonts and searches share this cache: 20 MB ran out at once.
        const val HTTP_CACHE_BYTES = 128L * 1024 * 1024
        const val STATE_PANEL_COLLAPSED = "panel_collapsed"
    }
}
