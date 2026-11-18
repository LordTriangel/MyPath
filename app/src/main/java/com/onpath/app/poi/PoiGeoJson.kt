package com.onpath.app.poi

import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point

/** Maps a rendered/queried MapLibre feature (from source "pois") to a [Poi]; null if not a POI point. */
fun Poi.Companion.fromFeature(feature: Feature): Poi? {
    val point = feature.geometry() as? Point ?: return null
    val json = feature.properties() ?: return null
    val props = HashMap<String, Any?>()
    for ((k, v) in json.entrySet()) {
        props[k] = when {
            v == null || v.isJsonNull -> null
            v.isJsonPrimitive && v.asJsonPrimitive.isNumber -> v.asNumber
            v.isJsonPrimitive -> v.asString
            else -> v.toString()
        }
    }
    return fromProperties(props, point.latitude(), point.longitude())
}
