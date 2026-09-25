package com.padelle.mapsicle

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val BROUTER_ENDPOINT = "https://brouter.de/brouter"
private const val ARRIVAL_THRESHOLD_METERS = 25.0

enum class TravelMode(val profile: String) {
    WALK("shortest"),
    BICYCLE("trekking"),
    CAR("car-fast"),
}

data class RoutePoint(
    val longitude: Double,
    val latitude: Double,
)

data class RouteStep(
    val geometryIndex: Int,
    val command: Int,
    val distanceToManeuverMeters: Double,
    val angleDegrees: Int,
)

data class RouteResult(
    val coordinates: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep> = emptyList(),
)

data class RouteProgress(
    val nextStep: RouteStep?,
    val distanceToNextManeuverMeters: Double,
    val remainingMeters: Double,
    val traveledMeters: Double,
    val percent: Int,
    val arrived: Boolean,
)

internal fun buildRouteUrl(
    start: SearchPlace,
    destination: SearchPlace,
    mode: TravelMode,
): String {
    val points = "${start.longitude},${start.latitude}|${destination.longitude},${destination.latitude}"
    val encodedPoints = URLEncoder.encode(points, StandardCharsets.UTF_8.name())
    return "$BROUTER_ENDPOINT?lonlats=$encodedPoints&profile=${mode.profile}&format=geojson&alternativeidx=0&timode=3"
}

internal fun parseRouteResponse(json: String): RouteResult {
    val feature = JSONObject(json)
        .optJSONArray("features")
        ?.optJSONObject(0)
        ?: error("Route response missing")
    val coordinatesJson = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
        ?: error("Route geometry missing")
    val coordinates = ArrayList<RoutePoint>(coordinatesJson.length())

    for (index in 0 until coordinatesJson.length()) {
        val coordinate = coordinatesJson.optJSONArray(index) ?: continue
        val longitude = coordinate.optDouble(0, Double.NaN)
        val latitude = coordinate.optDouble(1, Double.NaN)
        if (longitude.isFinite() && latitude.isFinite()) {
            coordinates += RoutePoint(longitude, latitude)
        }
    }
    if (coordinates.size < 2) {
        error("Invalid route geometry")
    }

    val properties = feature.optJSONObject("properties") ?: JSONObject()
    val voiceHints = properties.optJSONArray("voicehints")
    val steps = ArrayList<RouteStep>()
    if (voiceHints != null) {
        for (index in 0 until voiceHints.length()) {
            val hint = voiceHints.optJSONArray(index) ?: continue
            val geometryIndex = hint.optInt(0, -1)
            if (geometryIndex in coordinates.indices) {
                steps += RouteStep(
                    geometryIndex = geometryIndex,
                    command = hint.optInt(1, 1),
                    distanceToManeuverMeters = hint.optDouble(3, 0.0),
                    angleDegrees = hint.optInt(4, 0),
                )
            }
        }
    }

    return RouteResult(
        coordinates = coordinates,
        distanceMeters = properties.optString("track-length").toDoubleOrNull() ?: 0.0,
        durationSeconds = properties.optString("total-time").toDoubleOrNull() ?: 0.0,
        steps = steps.sortedBy(RouteStep::geometryIndex),
    )
}

internal fun RouteResult.toGeoJson(): String {
    val coordinates = coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordinates]}}]}"
}

fun RouteStep.instructionRes(): Int {
    return when (command) {
        1 -> R.string.nav_continue
        2 -> R.string.nav_turn_left
        3 -> R.string.nav_turn_slight_left
        4 -> R.string.nav_turn_sharp_left
        5 -> R.string.nav_turn_right
        6 -> R.string.nav_turn_slight_right
        7 -> R.string.nav_turn_sharp_right
        8 -> R.string.nav_keep_left
        9 -> R.string.nav_keep_right
        10 -> R.string.nav_uturn_left
        11 -> R.string.nav_uturn_right
        12 -> R.string.nav_off_route
        13 -> R.string.nav_roundabout
        14 -> R.string.nav_roundabout_left
        15 -> R.string.nav_uturn
        16 -> R.string.nav_continue_2
        17 -> R.string.nav_exit_left
        18 -> R.string.nav_exit_right
        else -> R.string.nav_continue_path
    }
}

internal fun calculateRouteProgress(
    route: RouteResult,
    latitude: Double,
    longitude: Double,
): RouteProgress {
    if (route.coordinates.size < 2) {
        return RouteProgress(null, 0.0, 0.0, 0.0, 0, true)
    }

    val referenceLatitude = latitude
    val current = projectPoint(RoutePoint(longitude, latitude), referenceLatitude)
    val cumulative = DoubleArray(route.coordinates.size)
    for (index in 1 until route.coordinates.size) {
        cumulative[index] = cumulative[index - 1] + distanceMeters(
            route.coordinates[index - 1],
            route.coordinates[index],
        )
    }

    var bestSegment = 0
    var bestRatio = 0.0
    var bestDistance = Double.MAX_VALUE
    for (index in 0 until route.coordinates.lastIndex) {
        val start = projectPoint(route.coordinates[index], referenceLatitude)
        val end = projectPoint(route.coordinates[index + 1], referenceLatitude)
        val deltaX = end.x - start.x
        val deltaY = end.y - start.y
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        val ratio = if (lengthSquared == 0.0) {
            0.0
        } else {
            (((current.x - start.x) * deltaX + (current.y - start.y) * deltaY) / lengthSquared)
                .coerceIn(0.0, 1.0)
        }
        val projectedX = start.x + deltaX * ratio
        val projectedY = start.y + deltaY * ratio
        val distance = hypot(current.x - projectedX, current.y - projectedY)
        if (distance < bestDistance) {
            bestDistance = distance
            bestSegment = index
            bestRatio = ratio
        }
    }

    val segmentLength = distanceMeters(
        route.coordinates[bestSegment],
        route.coordinates[bestSegment + 1],
    )
    val geometryDistance = cumulative.last()
    val traveled = (cumulative[bestSegment] + segmentLength * bestRatio).coerceAtLeast(0.0)
    val totalDistance = route.distanceMeters.coerceAtLeast(geometryDistance)
    val destinationDistance = distanceMeters(
        RoutePoint(longitude, latitude),
        route.coordinates.last(),
    )
    val arrived = destinationDistance <= ARRIVAL_THRESHOLD_METERS ||
        (bestDistance <= 50.0 && totalDistance - traveled <= ARRIVAL_THRESHOLD_METERS)
    val nextStep = route.steps.firstOrNull { step ->
        val stepDistance = cumulative.getOrElse(step.geometryIndex) { geometryDistance }
        stepDistance > traveled + 5.0
    }
    val remaining = (totalDistance - traveled).coerceAtLeast(0.0)
    val distanceToNext = nextStep?.let { step ->
        (cumulative.getOrElse(step.geometryIndex) { geometryDistance } - traveled).coerceAtLeast(0.0)
    } ?: remaining
    val percent = if (totalDistance > 0.0) {
        (traveled / totalDistance * 100.0).roundToInt().coerceIn(0, 100)
    } else {
        0
    }

    return RouteProgress(
        nextStep = nextStep,
        distanceToNextManeuverMeters = distanceToNext,
        remainingMeters = remaining,
        traveledMeters = traveled,
        percent = percent,
        arrived = arrived,
    )
}

fun formatDistance(distanceMeters: Double, locale: Locale): String {
    return if (distanceMeters >= 1_000) {
        String.format(locale, "%.1f km", distanceMeters / 1_000)
    } else {
        String.format(locale, "%d m", distanceMeters.toInt())
    }
}

fun formatDuration(durationSeconds: Double, locale: Locale): String {
    val totalMinutes = (durationSeconds / 60).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        String.format(locale, "%d h %02d min", hours, minutes)
    } else {
        String.format(locale, "%d min", totalMinutes.coerceAtLeast(1))
    }
}
