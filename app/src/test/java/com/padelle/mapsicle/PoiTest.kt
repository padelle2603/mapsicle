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
        // Senza api=1 Google ignora tutti gli altri parametri: la documentazione lo dice
        // esplicitamente, quindi questo assert esiste perche' si vede subito se qualcuno
        // "semplifica" la URL togliendolo.
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
        // "Bar&origin=my location" deve finire tutto dentro query, non diventare un
        // parametro: il link non puo' dire a Google da dove parte l'utente. Anche il "+"
        // del nome diventa %2B, cosi' non viene riletto come uno spazio.
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Bar%26origin%3Dmy%2Blocation",
            buildPlaceUrl("Bar&origin=my+location"),
        )
    }
}
