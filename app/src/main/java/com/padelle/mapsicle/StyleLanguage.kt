package com.padelle.mapsicle

import org.json.JSONArray
import org.json.JSONObject

internal fun localizeStyleJson(styleJson: String, language: String): String {
    val style = JSONObject(styleJson)
    val layers = style.optJSONArray("layers") ?: return styleJson
    for (index in 0 until layers.length()) {
        val layer = layers.optJSONObject(index) ?: continue
        val layout = layer.optJSONObject("layout") ?: continue
        val textField = layout.opt("text-field") ?: continue
        if (containsNameReference(textField)) {
            layout.put("text-field", localizeTextField(textField, language))
        }
    }
    return style.toString()
}

private fun localizeTextField(value: Any?, language: String): Any? {
    if (value is JSONArray && value.optString(0) == "case" && containsNameReference(value)) {
        return localizedNameExpression(language)
    }
    return localizeExpression(value, language)
}

private fun localizeExpression(value: Any?, language: String): Any? {
    return when (value) {
        is JSONArray -> {
            val operation = value.optString(0)
            if (operation == "get" && value.length() > 1 && isNameKey(value.optString(1))) {
                localizedNameExpression(language)
            } else if (operation == "has" && value.length() > 1 && isNameKey(value.optString(1))) {
                JSONArray().put("has").put(primaryNameKey(language))
            } else if ((operation == "case" || operation == "coalesce") && containsNameReference(value)) {
                localizedNameExpression(language)
            } else {
                JSONArray().apply {
                    for (index in 0 until value.length()) {
                        put(localizeExpression(value.opt(index), language))
                    }
                }
            }
        }

        is JSONObject -> JSONObject().apply {
            keys().asSequence().forEach { key ->
                put(key, localizeExpression(opt(key), language))
            }
        }

        is String -> if (isNameReference(value)) localizedNameExpression(language) else value
        else -> value
    }
}

private fun containsNameReference(value: Any?): Boolean {
    return when (value) {
        is JSONArray -> (0 until value.length()).any { index ->
            containsNameReference(value.opt(index))
        }

        is JSONObject -> value.keys().asSequence().any { key ->
            containsNameReference(value.opt(key))
        }

        is String -> isNameReference(value)
        else -> false
    }
}

private fun isNameReference(value: String): Boolean {
    return value == "name" || value == "name_en" || value.startsWith("name:") || value.contains("{name")
}

private fun isNameKey(value: String): Boolean {
    return isNameReference(value)
}

private fun primaryNameKey(language: String): String {
    return if (language.substringBefore('-').equals(ITALIAN_LANGUAGE, ignoreCase = true)) {
        "name:it"
    } else {
        "name:en"
    }
}

private fun localizedNameExpression(language: String): JSONArray {
    return JSONArray().put("coalesce").put(
        JSONArray().put("get").put(primaryNameKey(language)),
    ).also {
        if (language.substringBefore('-').equals(ITALIAN_LANGUAGE, ignoreCase = true)) {
            it.put(JSONArray().put("get").put("name:latin"))
        }
        it.put(JSONArray().put("get").put("name"))
    }
}
