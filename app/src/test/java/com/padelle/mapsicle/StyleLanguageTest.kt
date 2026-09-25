package com.padelle.mapsicle

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StyleLanguageTest {
    @Test
    fun prefersItalianNamesInLabelExpressions() {
        val localized = localizeStyleJson(styleJson, ITALIAN_LANGUAGE)
        val textField = textField(localized, "label_city")

        assertEquals("coalesce", textField.getString(0))
        assertEquals("name:it", textField.getJSONArray(1).getString(1))
        assertEquals("name:latin", textField.getJSONArray(2).getString(1))
        assertEquals("name", textField.getJSONArray(3).getString(1))
    }

    @Test
    fun prefersEnglishNamesInLabelExpressions() {
        val localized = localizeStyleJson(styleJson, ENGLISH_LANGUAGE)
        val textField = textField(localized, "label_city")

        assertEquals("coalesce", textField.getString(0))
        assertEquals("name:en", textField.getJSONArray(1).getString(1))
        assertEquals("name", textField.getJSONArray(2).getString(1))
    }

    @Test
    fun keepsRoadShieldReferences() {
        val localized = localizeStyleJson(styleJson, ITALIAN_LANGUAGE)
        val textField = textField(localized, "highway-shield")

        assertEquals("[\"to-string\",[\"get\",\"ref\"]]", textField.toString())
    }

    @Test
    fun handlesSimpleTextFieldTemplates() {
        val source = """{"layers":[{"id":"place","layout":{"text-field":"{name}"}}]}"""
        val localized = JSONObject(localizeStyleJson(source, ENGLISH_LANGUAGE))

        assertEquals(
            "coalesce",
            localized.getJSONArray("layers").getJSONObject(0)
                .getJSONObject("layout").getJSONArray("text-field").getString(0),
        )
    }

    private fun textField(styleJson: String, layerId: String): JSONArray {
        val layers = JSONObject(styleJson).getJSONArray("layers")
        return (0 until layers.length())
            .map { layers.getJSONObject(it) }
            .first { it.getString("id") == layerId }
            .getJSONObject("layout")
            .getJSONArray("text-field")
    }

    private companion object {
        val styleJson = """
            {
              "layers": [
                {
                  "id": "label_city",
                  "layout": {
                    "text-field": [
                      "case",
                      ["has", "name:nonlatin"],
                      ["concat", ["get", "name:latin"], " ", ["get", "name:nonlatin"]],
                      ["coalesce", ["get", "name_en"], ["get", "name"]]
                    ]
                  }
                },
                {
                  "id": "highway-shield",
                  "layout": {
                    "text-field": ["to-string", ["get", "ref"]]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
