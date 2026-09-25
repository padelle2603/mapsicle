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
    return if (language.substringBefore('-').equals(ITALIAN_LANGUAGE, ignoreCase = true)) {
        Locale.ITALY
    } else {
        Locale.ENGLISH
    }
}
