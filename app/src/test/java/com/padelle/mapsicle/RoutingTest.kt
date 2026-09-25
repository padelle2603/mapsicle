package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RoutingTest {
    private val start = SearchPlace("A", 45.46, 9.19)
    private val destination = SearchPlace("B", 45.47, 9.20)

    @Test
    fun buildsRouteForSelectedMode() {
        val url = buildRouteUrl(start, destination, TravelMode.BICYCLE)

        assertTrue(url.startsWith("https://brouter.de/brouter?"))
        assertTrue(url.contains("profile=trekking"))
        assertTrue(url.contains("format=geojson"))
        assertTrue(url.contains("timode=3"))
        assertTrue(url.contains("9.19%2C45.46%7C9.2%2C45.47"))
    }

    @Test
    fun parsesRouteGeometryAndMetrics() {
        val route = parseRouteResponse(
            """
            {"features":[{"type":"Feature","properties":{"track-length":"1250","total-time":"900","voicehints":[[0,1,0,0,0],[2,2,0,100,90]]},"geometry":{"type":"LineString","coordinates":[[9.19,45.46],[9.195,45.465],[9.2,45.47]]}}]}
            """.trimIndent(),
        )

        assertEquals(3, route.coordinates.size)
        assertEquals(1250.0, route.distanceMeters, 0.1)
        assertEquals(900.0, route.durationSeconds, 0.1)
        assertEquals(2, route.steps.size)
        assertEquals(2, route.steps[1].command)
        assertEquals(R.string.nav_turn_left, route.steps[1].instructionRes())
        assertTrue(route.toGeoJson().contains("\"coordinates\":[[9.19,45.46],[9.195,45.465],[9.2,45.47]]"))
    }

    @Test
    fun dropsElevationFromRouteGeoJson() {
        // BRouter con timode=3 risponde con coordinate 3D [lon, lat, quota]
        val route = parseRouteResponse(
            """
            {"features":[{"type":"Feature","properties":{"track-length":"10","total-time":"5"},"geometry":{"type":"LineString","coordinates":[[12.500605,42.500165,86.0],[12.599686,42.600017,240.25]]}}]}
            """.trimIndent(),
        )

        val geoJson = route.toGeoJson()

        assertTrue(geoJson.contains("\"coordinates\":[[12.500605,42.500165],[12.599686,42.600017]]"))
        assertTrue("non deve contenere la quota", !geoJson.contains("86.0"))
        assertTrue("non deve contenere la quota", !geoJson.contains("240.25"))
    }

    @Test
    fun calculatesProgressAndNextManeuver() {
        val route = RouteResult(
            coordinates = List(5) { RoutePoint(longitude = it * 0.001, latitude = 0.0) },
            distanceMeters = 1_000.0,
            durationSeconds = 600.0,
            steps = listOf(RouteStep(geometryIndex = 2, command = 2, distanceToManeuverMeters = 100.0, angleDegrees = 90)),
        )

        val progress = calculateRouteProgress(route, latitude = 0.0, longitude = 0.0015)

        assertEquals(2, progress.nextStep?.geometryIndex)
        assertTrue(progress.distanceToNextManeuverMeters in 50.0..60.0)
        assertTrue(progress.traveledMeters in 160.0..170.0)
        assertTrue(progress.percent in 16..18)
        assertTrue(!progress.arrived)
    }

    @Test
    fun marksDestinationAsArrived() {
        val route = RouteResult(
            coordinates = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.001, 0.0)),
            distanceMeters = 111.32,
            durationSeconds = 60.0,
        )

        val progress = calculateRouteProgress(route, latitude = 0.0, longitude = 0.001)

        assertTrue(progress.arrived)
        assertEquals(100, progress.percent)
    }

    @Test
    fun doesNotMarkOffRouteLocationAsArrived() {
        val route = RouteResult(
            coordinates = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.001, 0.0)),
            distanceMeters = 111.32,
            durationSeconds = 60.0,
        )

        val progress = calculateRouteProgress(route, latitude = 0.01, longitude = 0.001)

        assertTrue(!progress.arrived)
    }

    @Test
    fun cumulativeDistancesAreStableAcrossRepeatedCalls() {
        // cumulativeMeters e' memoizzato su una rotta immutabile: se lo ricalcolassi o lo
        // corrompessi, il progresso cambierebbe tra una chiamata e l'altra.
        val route = RouteResult(
            coordinates = listOf(
                RoutePoint(0.0, 0.0),
                RoutePoint(0.001, 0.0),
                RoutePoint(0.002, 0.0),
                RoutePoint(0.002, 0.001),
            ),
            distanceMeters = 335.0,
            durationSeconds = 120.0,
        )

        assertEquals(route.coordinates.size, route.cumulativeMeters.size)
        assertEquals(0.0, route.cumulativeMeters.first(), 0.0)

        val first = calculateRouteProgress(route, latitude = 0.0, longitude = 0.001)
        val second = calculateRouteProgress(route, latitude = 0.0, longitude = 0.001)
        assertEquals(first.traveledMeters, second.traveledMeters, 0.0)
        assertEquals(first.percent, second.percent)
        assertTrue(first.traveledMeters > 0.0)
    }

    @Test
    fun formatsRouteMetrics() {
        assertEquals("1,3 km", formatDistance(1250.0, Locale.ITALY))
        assertEquals("1.3 km", formatDistance(1250.0, Locale.US))
        assertEquals("15 min", formatDuration(900.0, Locale.ITALY))
        assertEquals("15 min", formatDuration(900.0, Locale.US))
    }
}
