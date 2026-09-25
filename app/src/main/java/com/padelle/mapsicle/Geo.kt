package com.padelle.mapsicle

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal const val METERS_PER_DEGREE_LAT = 111_320.0

internal data class ProjectedPoint(
    val x: Double,
    val y: Double,
)

internal fun projectPoint(point: RoutePoint, referenceLatitude: Double): ProjectedPoint {
    val longitudeScale = METERS_PER_DEGREE_LAT * cos(Math.toRadians(referenceLatitude))
    return ProjectedPoint(
        x = point.longitude * longitudeScale,
        y = point.latitude * METERS_PER_DEGREE_LAT,
    )
}

internal fun distanceMeters(first: RoutePoint, second: RoutePoint): Double {
    val longitudeScale = METERS_PER_DEGREE_LAT *
        cos(Math.toRadians((first.latitude + second.latitude) / 2.0))
    return hypot(
        (second.longitude - first.longitude) * longitudeScale,
        (second.latitude - first.latitude) * METERS_PER_DEGREE_LAT,
    )
}

internal fun bearingDegrees(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Float {
    val startLatitude = Math.toRadians(fromLatitude)
    val endLatitude = Math.toRadians(toLatitude)
    val deltaLongitude = Math.toRadians(toLongitude - fromLongitude)
    val y = sin(deltaLongitude) * cos(endLatitude)
    val x = cos(startLatitude) * sin(endLatitude) -
        sin(startLatitude) * cos(endLatitude) * cos(deltaLongitude)
    return ((Math.toDegrees(atan2(y, x)).toFloat() + 360f) % 360f)
}
