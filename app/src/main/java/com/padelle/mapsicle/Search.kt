package com.padelle.mapsicle

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val PHOTON_ENDPOINT = "https://photon.komoot.io/api/"
private const val RESULT_LIMIT = 5

data class SearchPlace(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
)

internal fun buildSuggestionUrl(query: String, language: String): String {
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    // Photon accetta solo default/de/en/fr: mandare lang=it risponde HTTP 400. Per
    // l'italiano omettiamo lang e Photon restituisce i nomi nativi, che e' quello che
    // serve. Non "correggere" questo in lang=it senza verifica.
    val languageParam = if (language.equals(ENGLISH_LANGUAGE, ignoreCase = true)) "&lang=en" else ""
    return "$PHOTON_ENDPOINT?q=$encodedQuery&limit=$RESULT_LIMIT$languageParam"
}

internal fun parseSuggestions(json: String): List<SearchPlace> {
    val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
    val results = ArrayList<SearchPlace>(minOf(features.length(), RESULT_LIMIT))

    for (index in 0 until minOf(features.length(), RESULT_LIMIT)) {
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
        if (displayName.isNotEmpty()) {
            results += SearchPlace(displayName, latitude, longitude)
        }
    }

    return results
}
