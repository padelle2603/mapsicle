package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SearchTest {
    @Test
    fun buildsEncodedPhotonUrl() {
        val italianUrl = buildSuggestionUrl("Piazza del Duomo Milano", ITALIAN_LANGUAGE)
        val englishUrl = buildSuggestionUrl("Piazza del Duomo Milano", ENGLISH_LANGUAGE)

        assertTrue(italianUrl.startsWith("https://photon.komoot.io/api/?"))
        assertTrue(italianUrl.contains("limit=10"))
        assertTrue(italianUrl.contains("q=Piazza+del+Duomo+Milano"))
        assertFalse(italianUrl.contains("lang="))
        assertTrue(englishUrl.endsWith("&lang=en"))
    }

    @Test
    fun omitsLocationBiasWhenPositionIsUnknown() {
        // Without the location permission there is no center: the URL must be identical
        // to before, with no bias parameters, or the search would change behaviour.
        val url = buildSuggestionUrl("Pizzeria", ITALIAN_LANGUAGE)

        assertFalse(url.contains("lat="))
        assertFalse(url.contains("lon="))
        assertFalse(url.contains("location_bias_scale"))
        assertFalse(url.contains("zoom="))
    }

    @Test
    fun addsMeasuredLocationBiasWhenPositionIsKnown() {
        // 0.4 / z12: 16km radius. Photon's defaults (0.2 / z16 = 1km) make a namesake
        // bakery win when searching for "Roma" from Milan.
        val url = buildSuggestionUrl("Pizzeria", ITALIAN_LANGUAGE, SearchCenter(45.4642, 9.19))

        assertTrue(url.contains("&lat=45.464"))
        assertTrue(url.contains("&lon=9.190"))
        assertTrue(url.contains("&location_bias_scale=0.4"))
        assertTrue(url.contains("&zoom=12"))
    }

    @Test
    fun roundsCoordinatesToKeepTheHttpCacheUsable() {
        // OkHttp keys the disk cache on the URL: at full precision every fix would create
        // a new entry. Let us check that ~111m of difference end up in the same string.
        val first = buildSuggestionUrl("Pizzeria", ITALIAN_LANGUAGE, SearchCenter(45.46421, 9.18998))
        val second = buildSuggestionUrl("Pizzeria", ITALIAN_LANGUAGE, SearchCenter(45.46429, 9.19003))

        assertEquals(first, second)
    }

    @Test
    fun formatsCoordinatesWithADotEvenInACommaLocale() {
        // On an Italian phone String.format would use the comma and "45,464" would not
        // be the right coordinate for Photon.
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            val url = buildSuggestionUrl("Pizzeria", ITALIAN_LANGUAGE, SearchCenter(45.4642, 9.19))
            assertTrue(url.contains("&lat=45.464"))
            assertTrue(url.contains("&lon=9.190"))
            assertFalse(url.contains("45,464"))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun parsesPhotonSuggestions() {
        val results = parseSuggestions(
            """
            {"features":[{"type":"Feature","properties":{"name":"Duomo","city":"Milano","country":"Italy"},"geometry":{"type":"Point","coordinates":[9.19,45.46]}}]}
            """.trimIndent(),
        )

        assertEquals(1, results.size)
        assertEquals("Duomo, Milano, Italy", results.single().displayName)
        assertEquals(45.46, results.single().latitude, 0.0001)
    }

    @Test
    fun dropsDuplicatesAndKeepsFillingTheList() {
        // With the bias nearby namesakes repeat: two stations 30m apart with the same
        // name and address. We ask for 10 and keep the first 5 unique, so the list does
        // not get shorter.
        val feature = { id: String, lon: String, lat: String ->
            """{"type":"Feature","properties":{"name":"$id","street":"Via Roma","city":"Milano"},"geometry":{"type":"Point","coordinates":[$lon,$lat]}}"""
        }
        val json = buildString {
            append("""{"features":[""")
            append(feature("Duomo A", "9.1890", "45.4640"))
            append(",")
            append(feature("Duomo A", "9.1891", "45.4641"))   // duplicato
            append(",")
            append(feature("Duomo B", "9.1900", "45.4650"))
            append(",")
            append(feature("Duomo B", "9.1901", "45.4651"))   // duplicato
            append(",")
            repeat(8) { i ->
                if (i > 0) append(",")
                append(feature("Piazzale $i", "9.2$i", "45.47$i"))
            }
            append("]}")
        }

        val results = parseSuggestions(json)

        assertEquals(5, results.size)
        assertEquals("Duomo A, Via Roma, Milano", results[0].displayName)
        assertEquals("Duomo B, Via Roma, Milano", results[1].displayName)
        assertEquals(5, results.map { it.displayName }.distinct().size)
    }
}
