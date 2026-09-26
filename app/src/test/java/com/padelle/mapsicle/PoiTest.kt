package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiTest {
    @Test
    fun buildsGoogleSearchUrlForThePlaceName() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Trattoria+Da+Luigi",
            buildPlaceUrl("Trattoria Da Luigi"),
        )
    }

    @Test
    fun keepsTheApiParameterGoogleRequires() {
        // Without api=1 Google ignores all the other parameters: the documentation says so
        // explicitly, so this assertion exists because it shows at once if somebody
        // "simplifies" the URL by removing it.
        assertTrue(buildPlaceUrl("Bar").contains("?api=1&query="))
    }

    @Test
    fun encodesCharactersThatWouldBreakTheUrl() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9+%26+Co",
            buildPlaceUrl("Café & Co"),
        )
    }

    @Test
    fun aNameCannotInjectAnotherParameter() {
        // "Bar&origin=my location" must end up entirely inside query, not become a
        // parameter: the link cannot tell Google where the user starts from. Even the "+"
        // of the name becomes %2B, so it is not read back as a space.
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Bar%26origin%3Dmy%2Blocation",
            buildPlaceUrl("Bar&origin=my+location"),
        )
    }
}
