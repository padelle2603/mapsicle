package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun buildsEncodedPhotonUrl() {
        val italianUrl = buildSuggestionUrl("Piazza del Duomo Milano", ITALIAN_LANGUAGE)
        val englishUrl = buildSuggestionUrl("Piazza del Duomo Milano", ENGLISH_LANGUAGE)

        assertTrue(italianUrl.startsWith("https://photon.komoot.io/api/?"))
        assertTrue(italianUrl.contains("limit=5"))
        assertTrue(italianUrl.contains("q=Piazza+del+Duomo+Milano"))
        assertFalse(italianUrl.contains("lang="))
        assertTrue(englishUrl.endsWith("&lang=en"))
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
}
