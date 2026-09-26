package com.padelle.mapsicle

import java.util.Locale

internal const val ITALIAN_LANGUAGE = "it"
internal const val ENGLISH_LANGUAGE = "en"

internal fun resolveAppLanguage(locale: Locale): String {
    return if (locale.language.equals(ITALIAN_LANGUAGE, ignoreCase = true)) {
        ITALIAN_LANGUAGE
    } else {
        ENGLISH_LANGUAGE
    }
}

internal fun appLocale(language: String): Locale {
    return if (isItalian(language)) {
        Locale.ITALY
    } else {
        Locale.ENGLISH
    }
}

/**
 * The one place that decides whether a language is Italian. It used to be spelled in four
 * places: three times as `substringBefore('-') == "it"` (appLocale, primaryNameKey and the
 * `name:latin` step of localizedNameExpression) and once, in MainActivity, as its inverse
 * `startsWith("en")`. The two agreed only because resolveAppLanguage() can return nothing
 * but "it" or "en": a third language would have drawn the English name on the map and the
 * Italian one on the card of the same place. The test that matters is not the one on this
 * function but the one that checks the two readers still agree.
 */
internal fun isItalian(language: String): Boolean {
    return language.substringBefore('-').equals(ITALIAN_LANGUAGE, ignoreCase = true)
}
