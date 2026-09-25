package com.padelle.mapsicle

import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val PHOTON_ENDPOINT = "https://photon.komoot.io/api/"
private const val RESULT_LIMIT = 5

// Chiediamo il doppio perche' con il bias gli omonimi vicini si ripetono: se i duplicati
// occupassero gli stessi 5 slot, l'utente ne vedrebbe 3 invece di 5.
private const val PHOTON_FETCH_LIMIT = RESULT_LIMIT * 2

// Il raggio del bias e' 0.25km * 2^(18-zoom), quindi a z12 copre 16km.
// location_bias_scale 0.4 lascia contare l'importanza del luogo al 40%. Sono entrambi
// diversi dai default di Photon (0.2 e z16, che vuol dire 1km): con quelli, cercando
// "Roma" da Milano vince una panetteria omonima e la citta sparisce dai primi risultati.
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
    // Photon accetta solo default/de/en/fr: mandare lang=it risponde HTTP 400. Per
    // l'italiano omettiamo lang e Photon restituisce i nomi nativi, che e' quello che
    // serve. Non "correggere" questo in lang=it senza verifica.
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

// Locale.US non e' un dettaglio: su un telefono italiano String.format userebbe la
// virgola e "45,464" per Photon non sarebbe la stessa coordinata. Tre decimali sono
// ~111m, trascurabile rispetto al raggio di 16km del bias, e serve anche a non
// far esplodere la cache: OkHttp la indicizza sull'URL, quindi a piena precisione ogni
// fix creerebbe una chiave nuova.
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
        // Il bias porta in cima gli omonimi di casa: "Duomo, Piazza del Duomo, Cinque
        // Vie, Milano" arriva due volte (due stazioni a 30m) e "Montenapoleone" tre.
        // Due voci con lo stesso indirizzo sono indistinguibili in elenco, quindi tengo la
        // prima e passo al successivo.
        if (displayName.isEmpty() || !seen.add(displayName)) {
            continue
        }
        results += SearchPlace(displayName, latitude, longitude)
    }

    return results
}
