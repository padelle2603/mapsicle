package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun distanceMatchesKnownDegreeLengthAtEquator() {
        val meters = distanceMeters(RoutePoint(0.0, 0.0), RoutePoint(0.001, 0.0))

        assertEquals(111.32, meters, 1.0)
    }

    @Test
    fun distanceShrinksWithLatitude() {
        val equator = distanceMeters(RoutePoint(0.0, 0.0), RoutePoint(0.001, 0.0))
        val north = distanceMeters(RoutePoint(0.0, 60.0), RoutePoint(0.001, 60.0))

        assertEquals(equator / 2.0, north, 1.0)
    }

    @Test
    fun bearingPointsToCardinalDirections() {
        assertEquals(0f, bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.5f)
        assertEquals(90f, bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.5f)
        assertEquals(180f, bearingDegrees(1.0, 0.0, 0.0, 0.0), 0.5f)
        assertEquals(270f, bearingDegrees(0.0, 0.0, 0.0, -1.0), 0.5f)
    }

    @Test
    fun aPlaceBecomesTheRoutePointOfTheSameSpot() {
        // SearchPlace takes (latitude, longitude) and RoutePoint takes (longitude,
        // latitude). Written by hand at a call site the swap compiles, does not crash and
        // puts Milan at 3 degrees south of the Gulf of Guinea, so the check is a distance
        // and not the two numbers: a swapped pair is about 5,700 km, not 0.
        val milan = SearchPlace("Duomo di Milano", 45.4642, 9.1914)

        assertEquals(0.0, distanceMeters(milan.toRoutePoint(), RoutePoint(9.1914, 45.4642)), 0.001)
    }
}
