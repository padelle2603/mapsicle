package com.padelle.mapsicle

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val PHOTON_ENDPOINT = "https://photon.komoot.io/api/"
private const val RESULT_LIMIT = 5

// We ask for twice as much because with the bias nearby namesakes repeat: if the
// duplicates took the same 5 slots, the user would see 3 instead of 5.
private const val PHOTON_FETCH_LIMIT = RESULT_LIMIT * 2

// The bias radius is 0.25km * 2^(18-zoom), so at z12 it covers 16km.
// location_bias_scale 0.4 lets the importance of the place count for 40%. Both differ
// from Photon's defaults (0.2 and z16, which means 1km): with those, searching for
// "Roma" from Milan is won by a namesake bakery and the city disappears from the top.
private const val BIAS_SCALE = "0.4"
private const val BIAS_ZOOM = "12"

data class SearchPlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

data class SearchCenter(
    val latitude: Double,
    val longitude: Double,
)

internal fun buildSuggestionUrl(
    query: String,
    language: String,
    center: SearchCenter? = null,
): String {
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    // Photon only accepts default/de/en/fr: sending lang=it answers HTTP 400. For
    // Italian we omit lang and Photon returns the native names, which is what we want.
    // Do not "fix" this into lang=it without checking.
    val languageParam = if (language.equals(ENGLISH_LANGUAGE, ignoreCase = true)) "&lang=en" else ""
    val biasParam = if (center == null) {
        ""
    } else {
        "&lat=${formatCoordinate(center.latitude)}" +
            "&lon=${formatCoordinate(center.longitude)}" +
            "&location_bias_scale=$BIAS_SCALE&zoom=$BIAS_ZOOM"
    }
    return "$PHOTON_ENDPOINT?q=$encodedQuery&limit=$PHOTON_FETCH_LIMIT$languageParam$biasParam"
}

// Locale.US is not a detail: on an Italian phone String.format would use the comma,
// and "45,464" is not the same coordinate for Photon. Three decimals are ~111m,
// negligible against the 16km bias radius, and they also keep the cache from
// exploding: OkHttp keys it on the URL, so at full precision every fix would create a
// new entry.
private fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.3f", value)

internal fun parseSuggestions(json: String): List<SearchPlace> {
    val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
    val results = ArrayList<SearchPlace>(RESULT_LIMIT)
    val seen = HashSet<String>(PHOTON_FETCH_LIMIT)

    for (index in 0 until features.length()) {
        if (results.size >= RESULT_LIMIT) {
            break
        }
        val feature = features.optJSONObject(index) ?: continue
        val geometry = feature.optJSONObject("geometry") ?: continue
        if (geometry.optString("type") != "Point") {
            continue
        }
        val coordinates = geometry.optJSONArray("coordinates") ?: continue
        val longitude = coordinates.optDouble(0, Double.NaN)
        val latitude = coordinates.optDouble(1, Double.NaN)
        if (!longitude.isFinite() || !latitude.isFinite()) {
            continue
        }

        val properties = feature.optJSONObject("properties") ?: JSONObject()
        val displayName = listOf("name", "street", "locality", "city", "state", "country")
            .map { properties.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
        // The bias brings home namesakes to the top: "Duomo, Piazza del Duomo, Cinque
        // Vie, Milano" arrives twice (two stations 30m apart) and "Montenapoleone" three
        // times. Two entries with the same address are indistinguishable in the list, so
        // I keep the first and move to the next.
        if (displayName.isEmpty() || !seen.add(displayName)) {
            continue
        }
        results += SearchPlace(displayName, latitude, longitude)
    }

    return results
}
