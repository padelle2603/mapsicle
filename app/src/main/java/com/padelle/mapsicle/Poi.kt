package com.padelle.mapsicle

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens the POI in Google Maps. `query` is what Google itself recommends to link to one
 * specific shop, `PLACE_NAME, ADDRESS`: a chain name on its own is a category, not a place,
 * so "Starbucks" answers with the list of every branch instead of the one tapped.
 * `api=1` is the parameter Google's documentation calls mandatory, because without it all
 * the others are ignored. No API key. If Google Maps is not installed, the browser opens.
 *
 * Only the place that was tapped, never the position of the user: the link must not be able
 * to tell Google where the person who touches it stands.
 */
internal fun buildPlaceUrl(query: String): String {
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    return "https://www.google.com/maps/search/?api=1&query=$encoded"
}

// Photon's location bias is a hint, not a filter, so the answer is trusted only if it is
// really the shop that was tapped. 250m and not 150m: buildSuggestionUrl() rounds the centre
// of the bias to 3 decimals (111m, there for the OkHttp cache), so the centre itself is
// already up to ~96m off. For scale, searching "Bar" near Siena answers with a bar 15km
// away, in a town of the same name.
internal const val MAX_CANDIDATE_METERS = 250.0

/**
 * The query for a tapped POI, given the addresses Photon found for it. The nearest candidate
 * wins, not the first in the ranking: the bias orders by distance but does not filter, so the
 * first one can be a namesake. Too far away means Photon has no branch here, and the name on
 * its own is the safer link.
 */
internal fun resolvePlaceQuery(place: SearchPlace, addresses: List<SearchPlace>): String {
    val tapped = place.toRoutePoint()
    val nearest = addresses.minByOrNull {
        distanceMeters(tapped, it.toRoutePoint())
    } ?: return place.displayName
    val gap = distanceMeters(tapped, nearest.toRoutePoint())
    return if (gap <= MAX_CANDIDATE_METERS) {
        "${place.displayName}, ${nearest.displayName}"
    } else {
        place.displayName
    }
}

/**
 * The addresses of the places Photon answers with, the same JSON parseSuggestions() reads but
 * keeping only what a postal address is made of: the street with its number, then the postcode
 * and the city, which is how an address is written both in Google and on a letter.
 *
 * Each part is optional because Photon has none of them guaranteed, and a bad answer is
 * dropped rather than sent half-formed. Coordinates are not validated: MAX_CANDIDATE_METERS
 * rejects a NaN because the comparison is false, which is the same fallback as no answer.
 */
internal fun parsePlaceAddresses(json: String): List<SearchPlace> {
    val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
    val addresses = ArrayList<SearchPlace>(features.length())
    for (index in 0 until features.length()) {
        val feature = features.optJSONObject(index) ?: continue
        val address = addressOf(feature.optJSONObject("properties")) ?: continue
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
        addresses += SearchPlace(address, coordinates.optDouble(1), coordinates.optDouble(0))
    }
    return addresses
}

private fun addressOf(properties: JSONObject?): String? {
    if (properties == null) {
        return null
    }
    val street = listOf("street", "housenumber")
        .map { properties.optString(it).trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    // Outside the big cities "city" is null and the place is a town, a village or a
    // municipality, so the name of the city is looked for in that order. "locality" (the
    // quarter, "Cerchia dei Navigli") and "district" ("Municipio 1") are left out on
    // purpose: neither is a city, and one of the two in the query sends Google looking for
    // the wrong place.
    val city = listOf("city", "town", "village", "municipality", "county")
        .firstNotNullOfOrNull { properties.optString(it).trim().ifEmpty { null } }
    val postcode = properties.optString("postcode").trim()
    val cityLine = listOfNotNull(postcode.ifEmpty { null }, city).joinToString(" ")
    return listOf(street, cityLine).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null }
}
