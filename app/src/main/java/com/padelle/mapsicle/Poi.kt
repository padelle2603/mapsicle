package com.padelle.mapsicle

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens the POI in Google Maps. The name is enough: of the 8478 curated POIs between Milan
 * and Rome only 9 have a generic name ("Bar", "Minimarket"), and `api=1` is the parameter
 * Google's documentation calls mandatory, because without it all the others are ignored.
 * No API key. If Google Maps is not installed, the browser opens instead.
 *
 * Only the name of the POI, never the position of the user: the link must not be able to
 * tell Google where the person who touches it stands.
 */
internal fun buildPlaceUrl(name: String): String {
    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
    return "https://www.google.com/maps/search/?api=1&query=$encoded"
}
