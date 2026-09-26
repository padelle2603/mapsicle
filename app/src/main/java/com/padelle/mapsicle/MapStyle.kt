package com.padelle.mapsicle

import com.google.gson.JsonObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's localized style and POI layers. The POI come from no extra service: the
 * `poi` layer is already inside the tile that draws the basemap (7000 named places per
 * z14 tile over Milan) and the category icons are in the sprite the style points at.
 */
internal fun buildMapStyleJson(styleJson: String, language: String): String {
    return addPoiLayers(localizeStyleJson(styleJson, language), language)
}

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
    return if (isItalian(language)) {
        "name:it"
    } else {
        "name:en"
    }
}

internal fun localizedNameExpression(language: String): JSONArray {
    return JSONArray().put("coalesce").put(
        JSONArray().put("get").put(primaryNameKey(language)),
    ).also {
        if (isItalian(language)) {
            it.put(JSONArray().put("get").put("name:latin"))
        }
        it.put(JSONArray().put("get").put("name"))
    }
}

/**
 * Same priority as localizedNameExpression() above, read from a feature of the tile instead
 * of from a style expression: this is the name the card of a tapped place shows, while that
 * one is what the map writes on the labels. It lived in MainActivity as a private extension,
 * where no test could reach it, and it had drifted: it asked "is it English" where the other
 * asked "is it Italian". They agree only while isItalian() and its inverse are the only two
 * answers resolveAppLanguage() can give, and the test on the two readers is what keeps them
 * from drifting again.
 */
internal fun JsonObject.optLocalName(language: String): String? {
    val keys = if (isItalian(language)) {
        listOf("name:it", "name:latin", "name")
    } else {
        listOf("name:en", "name")
    }
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }
}

// The 3 poi_r* layers of the Liberty style filter on ["get","rank"] and never draw
// anything: in the tiles the 'rank' key is there but has no value on any feature
// (checked on 7063/7063 in Milan and 6247/6247 in Rome). Here they are removed and
// replaced by the 2 layers below, which use data that really exists.
private val DEAD_POI_LAYERS = setOf("poi_r1", "poi_r7", "poi_r20")

// 55 classes with a name and a reason to go there. The excluded ones are the public
// works: waste_basket 879, gate 337, entrance 144, bollard 107, telephone 55,
// drinking_water 45, lift_gate 27, toilets 28, recycling 6, sally_port 3, cycle_barrier 2.
// They are most of the 7063 POIs of a Milan tile and not one of them is a place to go to,
// so drawing them makes no sense. "has name" in the filter: a point without a name cannot
// be described and cannot even be touched.
private val POI_CLASSES = listOf(
    "alcohol_shop", "art_gallery", "attraction", "bakery", "bank", "bar", "beer",
    "bicycle", "bicycle_parking", "bicycle_rental", "bus", "butcher", "cafe", "castle",
    "cinema", "clothing_store", "college", "dentist", "doctors", "dog_park", "fast_food",
    "fuel", "garden", "grocery", "hairdresser", "hospital", "ice_cream", "information",
    "laundry", "library", "lodging", "monument", "motorcycle_parking", "museum", "music",
    "office", "park", "parking", "pharmacy", "pitch", "place_of_worship", "playground",
    "police", "post", "railway", "restaurant", "school", "shelter", "shop",
    "sports_centre", "swimming_pool", "theatre", "town_hall", "veterinary", "yoga",
)

// The 6 classes with no dedicated icon in the sprite: they fall back on the generic dot
// of the same sprite, so there is nothing to draw and no image to register at runtime.
private val POI_CLASSES_WITHOUT_ICON = setOf(
    "office", "bicycle_parking", "motorcycle_parking", "swimming_pool", "sports_centre", "yoga",
)

internal fun addPoiLayers(styleJson: String, language: String): String {
    val style = JSONObject(styleJson)
    val original = style.optJSONArray("layers") ?: return styleJson
    val kept = JSONArray()
    for (index in 0 until original.length()) {
        val layer = original.optJSONObject(index) ?: continue
        if (layer.optString("id") in DEAD_POI_LAYERS) {
            continue
        }
        kept.put(layer)
    }
    kept.put(poiLayer(POI_ICON_LAYER, 16, iconLayout()))
    kept.put(poiLayer(POI_LABEL_LAYER, 17, labelLayout(language)))
    style.put("layers", kept)
    return style.toString()
}

private fun poiLayer(id: String, minzoom: Int, layout: JSONObject) = JSONObject()
    .put("id", id)
    .put("type", "symbol")
    .put("source", POI_SOURCE)
    .put("source-layer", POI_SOURCE_LAYER)
    .put("minzoom", minzoom)
    .put("filter", poiFilter())
    .put("layout", layout)

/**
 * Only from z16: at z15 the viewport covers ~0.5 km2 and in a city centre like Milan
 * there are ~900 of the POIs of a tile, all overlapping. The icons overlap on purpose
 * (icon-allow-overlap) because a point that vanishes when a neighbour passes in front of
 * it is worse than an overlapping point; the labels do not, and MapLibre drops on its own
 * the ones that collide.
 */
private fun iconLayout() = JSONObject()
    .put(
        "icon-image",
        JSONArray()
            .put("match")
            .put(JSONArray().put("get").put("class"))
            .put(JSONArray(POI_CLASSES.filterNot { it in POI_CLASSES_WITHOUT_ICON }))
            .put(JSONArray().put("get").put("class"))
            .put("dot_11"),
    )
    .put("icon-allow-overlap", true)

private fun labelLayout(language: String) = JSONObject()
    .put("text-field", localizedNameExpression(language))
    .put("text-font", JSONArray().put("Noto Sans Italic"))
    .put("text-size", 12)
    .put("text-max-width", 9)
    // "bottom" with no offset: the name sits just above the icon, which is centred on
    // the point
    .put("text-anchor", "bottom")
    .put(
        "paint",
        JSONObject()
            .put("text-color", "#666")
            .put("text-halo-color", "#ffffff")
            .put("text-halo-width", 1)
            .put("text-halo-blur", 0.5),
    )

internal fun poiFilter(): JSONArray = JSONArray()
    .put("all")
    .put(
        JSONArray()
            .put("match")
            .put(JSONArray().put("geometry-type"))
            .put(JSONArray().put("Point").put("MultiPoint").put("Polygon").put("MultiPolygon"))
            .put(true)
            .put(false),
    )
    .put(JSONArray().put("has").put("name"))
    .put(
        JSONArray()
            .put("in")
            .put(JSONArray().put("get").put("class"))
            .put(JSONArray().put("literal").put(JSONArray(POI_CLASSES))),
    )

/**
 * 55 classes -> 14 labels: the card of a POI shows the category, not the OSM key.
 * Italian first because the Manifest wants it read in the language of the user.
 */
internal fun poiCategoryLabelRes(category: String): Int = when (category) {
    "restaurant", "fast_food", "cafe", "bar", "beer", "ice_cream" -> R.string.category_food
    "shop", "clothing_store", "grocery", "bakery", "butcher", "alcohol_shop", "laundry",
    "hairdresser" -> R.string.category_shop
    "lodging" -> R.string.category_lodging
    "bank" -> R.string.category_bank
    "pharmacy" -> R.string.category_pharmacy
    "hospital", "doctors", "dentist", "veterinary" -> R.string.category_health
    "museum", "art_gallery", "attraction", "castle", "monument", "theatre", "cinema",
    "music" -> R.string.category_culture
    "park", "garden", "dog_park", "playground", "pitch" -> R.string.category_green
    "bicycle", "bicycle_rental", "sports_centre", "swimming_pool", "yoga" -> R.string.category_sport
    "information", "post", "police", "town_hall" -> R.string.category_service
    "school", "college" -> R.string.category_school
    "parking", "bicycle_parking", "motorcycle_parking" -> R.string.category_parking
    "bus", "railway", "fuel" -> R.string.category_transport
    else -> R.string.category_other
}

private const val POI_SOURCE = "openmaptiles"
private const val POI_SOURCE_LAYER = "poi"
internal const val POI_ICON_LAYER = "poi-icons"
internal const val POI_LABEL_LAYER = "poi-labels"
