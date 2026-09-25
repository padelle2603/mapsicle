package com.padelle.mapsicle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LanguageTest {
    @Test
    fun selectsItalianForItalianSystemLanguage() {
        assertEquals(ITALIAN_LANGUAGE, resolveAppLanguage(Locale.ITALY))
        assertEquals(ITALIAN_LANGUAGE, resolveAppLanguage(Locale.forLanguageTag("it-CH")))
    }

    @Test
    fun selectsEnglishForOtherSystemLanguages() {
        assertEquals(ENGLISH_LANGUAGE, resolveAppLanguage(Locale.US))
        assertEquals(ENGLISH_LANGUAGE, resolveAppLanguage(Locale.FRANCE))
    }

    @Test
    fun mapsApplicationLanguageToDisplayLocale() {
        assertEquals(Locale.ITALY, appLocale(ITALIAN_LANGUAGE))
        assertEquals(Locale.ENGLISH, appLocale(ENGLISH_LANGUAGE))
    }
}
