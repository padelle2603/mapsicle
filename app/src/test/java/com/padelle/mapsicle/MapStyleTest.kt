package com.padelle.mapsicle

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
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

    @Test
    fun dropsTheDeadPoiLayersAndAddsTheOnesThatMatchRealData() {
        // poi_r1/r7/r20 filtrano su ["get","rank"], che nei tile non ha valore: resterebbero
        // tre layer che non disegnano niente accanto ai due nuovi.
        val layers = JSONObject(buildMapStyleJson(styleJson, ITALIAN_LANGUAGE))
            .getJSONArray("layers")
        val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }

        assertFalse(ids.contains("poi_r1"))
        assertFalse(ids.contains("poi_r7"))
        assertFalse(ids.contains("poi_r20"))
        assertTrue(ids.contains(POI_ICON_LAYER))
        assertTrue(ids.contains(POI_LABEL_LAYER))
        assertEquals(4, layers.length())
    }

    @Test
    fun showsPlacesFromZoomSixteenAndLabelsFromSeventeen() {
        val layers = JSONObject(buildMapStyleJson(styleJson, ITALIAN_LANGUAGE))
            .getJSONArray("layers")

        assertEquals(16, layer(layers, POI_ICON_LAYER).getInt("minzoom"))
        assertEquals(17, layer(layers, POI_LABEL_LAYER).getInt("minzoom"))
    }

    @Test
    fun keepsPlacesAndDropsInfrastructure() {
        val classes = filteredClasses()
        // su un tile di Milano: 879 waste_basket, 337 gate, 144 entrance, 107 bollard,
        // 55 telephone, 45 drinking_water, 27 lift_gate. Nessuno e' un posto in cui andare.
        val infrastructure = setOf(
            "waste_basket", "gate", "entrance", "bollard", "telephone",
            "drinking_water", "lift_gate", "sally_port", "cycle_barrier",
        )

        assertTrue(classes.containsAll(listOf("restaurant", "shop", "lodging", "pharmacy", "park")))
        assertTrue(classes.none { it in infrastructure })
    }

    @Test
    fun everyDrawnPlaceGetsItsIconOrTheGenericDot() {
        val iconImage = layer(
            JSONObject(buildMapStyleJson(styleJson, ITALIAN_LANGUAGE)).getJSONArray("layers"),
            POI_ICON_LAYER,
        ).getJSONObject("layout").getJSONArray("icon-image")
        val classes = filteredClasses()
        val withIcon = (0 until iconImage.getJSONArray(2).length())
            .map { iconImage.getJSONArray(2).getString(it) }

        // 6 classi non hanno icona nello sprite e prendono dot_11, che e' gia' nello sprite:
        // se una di queste finisse fra le icone, l'icona inesistente la renderebbe invisibile.
        assertEquals("dot_11", iconImage.getString(4))
        assertTrue(withIcon.contains("restaurant"))
        assertFalse(withIcon.contains("office"))
        assertEquals(classes.size, withIcon.size + classes.count { it in withoutSpriteIcon })
    }

    @Test
    fun everyDrawnPlaceHasACategoryLabel() {
        filteredClasses().forEach { category ->
            assertTrue("manca l'etichetta per $category", poiCategoryLabelRes(category) != 0)
        }
    }

    private fun layer(layers: JSONArray, layerId: String) = (0 until layers.length())
        .map { layers.getJSONObject(it) }
        .first { it.getString("id") == layerId }

    private fun filteredClasses(): List<String> {
        val inFilter = poiFilter().getJSONArray(3)
        val list = inFilter.getJSONArray(2).getJSONArray(1)
        return (0 until list.length()).map { list.getString(it) }
    }

    private companion object {
        val withoutSpriteIcon = setOf(
            "office", "bicycle_parking", "motorcycle_parking",
            "swimming_pool", "sports_centre", "yoga",
        )

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
                },
                {
                  "id": "poi_r1",
                  "filter": ["match", ["get", "rank"], ["interpolate", ["linear"], ["zoom"], 6, 0, 12, 100], true, false],
                  "layout": {
                    "text-field": ["coalesce", ["get", "name:latin"], ["get", "name"]]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
