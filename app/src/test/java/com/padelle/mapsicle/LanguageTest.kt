package com.padelle.mapsicle

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun tellsItalianFromEverythingElse() {
        // The region is dropped: "it-CH" is Italian, "en-GB" is not. German is here on
        // purpose, it is the first language that is neither of the two the app ships and
        // the one that used to be read as Italian by the card of a place.
        assertTrue(isItalian("it"))
        assertTrue(isItalian("it-CH"))
        assertFalse(isItalian("en"))
        assertFalse(isItalian("en-GB"))
        assertFalse(isItalian("de"))
    }

    @Test
    fun bothReadersOfANameAgreeOnEveryLanguage() {
        // This is the test the last two were for. localizedNameExpression() decides what the
        // map writes on a label and optLocalName() what the card of a tapped place shows.
        // They used to ask the question in opposite ways, "is it English" against "is it
        // Italian", and agreed only because resolveAppLanguage() can return nothing but "it"
        // or "en". The day a third language arrives one of the two has to change, and
        // without this the map and the card would quietly stop saying the same name.
        //
        // Every value is the name of its own key, so each reader answers with the key it
        // reached for and the two can be compared without guessing.
        val properties = JsonParser.parseString(
            """{"name":"name","name:latin":"name:latin","name:it":"name:it","name:en":"name:en"}""",
        ).asJsonObject

        for (language in listOf("it", "it-CH", "en", "en-GB", "de", "fr")) {
            assertEquals(
                "the map and the card choose a different name for $language",
                firstNameKeyOfStyleExpression(language),
                properties.optLocalName(language),
            )
        }
    }

    @Test
    fun theItalianChainFallsThroughNameLatinAndName() {
        // Only the card falls through, and only for Italian: the style expression is a
        // MapLibre coalesce, which does the same thing on the map. A feature with only
        // name:latin and name must still be readable in Italian.
        val properties = JsonParser.parseString(
            """{"name":"Duomo","name:latin":"Duomo di Milano"}""",
        ).asJsonObject

        assertEquals("Duomo di Milano", properties.optLocalName(ITALIAN_LANGUAGE))
        assertEquals("Duomo", properties.optLocalName(ENGLISH_LANGUAGE))
    }

    @Test
    fun aNameThatIsBlankOrNotTextIsNotAName() {
        // name is present on the feature but unusable, and optLocalName has to keep looking
        // instead of showing an empty title on the card.
        val blank = JsonParser.parseString("""{"name":"  ","name:it":"Duomo di Milano"}""").asJsonObject
        val notText = JsonParser.parseString("""{"name":{"a":1},"name:it":"Duomo di Milano"}""").asJsonObject
        val nothing = JsonParser.parseString("""{"class":"amenity"}""").asJsonObject

        assertEquals("Duomo di Milano", blank.optLocalName(ITALIAN_LANGUAGE))
        assertEquals("Duomo di Milano", notText.optLocalName(ITALIAN_LANGUAGE))
        assertNull(nothing.optLocalName(ITALIAN_LANGUAGE))
    }
}

/**
 * The first key localizedNameExpression() coalesces, which is the one that decides the
 * language. Read back out of the expression rather than from primaryNameKey(), so the test
 * checks what the map really does and not what the helper meant to do.
 */
private fun firstNameKeyOfStyleExpression(language: String): String {
    return localizedNameExpression(language).getJSONArray(1).getString(1)
}
