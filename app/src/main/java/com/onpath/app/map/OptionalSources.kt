package com.onpath.app.map

import android.util.Log
import com.onpath.app.debug.Problems
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Removes style sources whose local data file is missing, together with every layer that uses them.
 *
 * Only `base` and `terrain` are essential; everything else (contours, pois, trail names, …) is optional
 * data that a region may not have. MapLibre would otherwise log errors or show nothing for those layers,
 * and new optional files can be added to the style without any app change.
 *
 * A layer with `"metadata": {"onpath:fallback-for": "<source>"}` is the opposite: it is drawn only when that
 * source is missing (e.g. the base map's paths when an older map package has no paths.geojson).
 */
object OptionalSources {

    /** Sources that are never stripped (the map cannot work without them anyway). */
    private val ESSENTIAL = setOf("base", "terrain")

    /** Format-1 vector tile archive replacing heavy GeoJSON overlays when present. */
    const val OVERLAYS_F1 = "overlays_f1.pmtiles"

    /** Vector tile archive replacing pois.geojson when present. */
    const val POIS_PMTILES = "pois.pmtiles"

    /** Optional geometry GeoJSONs above this size are stripped (base fallback draws paths/tracks) to avoid OOM. */
    const val MAX_OPTIONAL_GEOJSON_BYTES = 8L * 1024 * 1024

    /** Remaining GeoJSONs above this size use coarser geojson-vt tiling to bound native heap usage. */
    const val LARGE_GEOJSON_BYTES = 3L * 1024 * 1024

    private val F1_VECTOR_OVERLAYS = setOf("routes", "trails", "ways", "lowways", "paths", "trailnames")
    private val STRIP_IF_OVERSIZED = setOf("paths", "lowways", "ways")

    /** Layer metadata key: the layer stands in for the named source and is drawn only when that source is missing. */
    const val FALLBACK_KEY = "onpath:fallback-for"

    /**
     * Style metadata key: source id → a stand-in source used when the source's own file is missing and the stand-in's
     * exists (format 2: `pois` vector tiles → `pois.geojson`, for a data folder or store without POI tiles).
     */
    const val FALLBACK_SOURCES_KEY = "onpath:fallback-sources"

    /** The local file a source points to ("file://…" or "pmtiles://file://…"), or null if remote/inline. */
    fun localFile(source: JSONObject): String? {
        val url = source.optString("data", "").ifEmpty { source.optString("url", "") }
        val path = url.removePrefix("pmtiles://")
        return if (path.startsWith("file://")) path.removePrefix("file://") else null
    }

    /** Logging must never break style loading (and is not available in JVM tests). */
    private fun log(msg: String) { try { Log.i("OnPath", msg) } catch (_: Throwable) { /* expected: JVM tests have no android.util.Log */ } }

    private fun vectorPmtilesFor(id: String, geoJsonFile: String): Pair<String, String>? {
        val sourceLayer = when {
            id in F1_VECTOR_OVERLAYS -> id
            id == "pois" -> "pois"
            else -> return null
        }
        val archive = if (id == "pois") POIS_PMTILES else OVERLAYS_F1
        val slash = geoJsonFile.lastIndexOf('/')
        val path = if (slash >= 0) "${geoJsonFile.substring(0, slash)}/$archive" else archive
        return path to sourceLayer
    }

    private fun throttleLargeGeoJson(src: JSONObject, bytes: Long): Boolean {
        if (bytes <= LARGE_GEOJSON_BYTES) return false
        if (src.optInt("maxzoom", 16) > 11) src.put("maxzoom", 11)
        if (src.optDouble("tolerance", 0.25) < 1.2) src.put("tolerance", 1.2)
        if (src.optInt("buffer", 64) > 4) src.put("buffer", 4)
        return true
    }

    /**
     * [standIn]: essential sources that may be stripped like optional ones when their file is missing (the app passes
     * true for all: fresh install without map files – only the overview from the APK draws, MapLibre never tries to
     * open a missing file). Default: essential sources are always kept.
     */
    fun strip(
        styleJson: String,
        standIn: (String) -> Boolean = { false },
        sizeOf: (String) -> Long = { File(it).let { f -> if (f.isFile) f.length() else -1L } },
        exists: (String) -> Boolean = { File(it).isFile },
    ): String = try {
        val root = JSONObject(styleJson)
        val sources = root.optJSONObject("sources") ?: return styleJson
        val missing = mutableSetOf<String>()
        val upgraded = mutableMapOf<String, String>()
        var throttled = false
        sources.keys().asSequence().toList().forEach { id ->
            val src = sources.optJSONObject(id) ?: return@forEach
            val file = localFile(src) ?: return@forEach
            val isGeoJson = src.optString("type") == "geojson" && file.endsWith(".geojson")
            if (isGeoJson) {
                val candidate = vectorPmtilesFor(id, file)
                if (candidate != null) {
                    val (pmtiles, sourceLayer) = candidate
                    if (sizeOf(pmtiles) > 0L || (!exists(file) && exists(pmtiles))) {
                        sources.put(id, JSONObject().put("type", "vector").put("url", "pmtiles://file://$pmtiles"))
                        upgraded[id] = sourceLayer
                        return@forEach
                    }
                }
            }
            if (!exists(file)) {
                if (id !in ESSENTIAL || standIn(id)) missing += id
                return@forEach
            }
            if (isGeoJson) {
                val bytes = sizeOf(file)
                if (id in STRIP_IF_OVERSIZED && bytes > MAX_OPTIONAL_GEOJSON_BYTES) {
                    missing += id
                    log("Oversized GeoJSON source ($id, ${bytes / 1_048_576} MB) skipped; base fallback layers active")
                } else if (throttleLargeGeoJson(src, bytes)) {
                    throttled = true
                    log("Large GeoJSON source ($id, ${bytes / 1_048_576} MB) capped to maxzoom 11")
                }
            }
        }
        val layers = root.optJSONArray("layers") ?: JSONArray()
        if (upgraded.isNotEmpty()) {
            for (i in 0 until layers.length()) {
                val l = layers.optJSONObject(i) ?: continue
                val sl = upgraded[l.optString("source")] ?: continue
                l.put("source-layer", sl)
            }
            log("Format-1 GeoJSON sources upgraded to vector PMTiles: ${upgraded.keys}")
        }
        // a missing source with a stand-in source whose file exists (POI tiles → the GeoJSON): swapped in, its layers
        // lose "source-layer" (a GeoJSON source has one unnamed layer)
        val swapped = mutableSetOf<String>()
        val standIns = root.optJSONObject("metadata")?.optJSONObject(FALLBACK_SOURCES_KEY)
        for (id in missing.toList()) {
            val alt = standIns?.optJSONObject(id) ?: continue
            val file = localFile(alt)
            if (file != null && !exists(file)) continue
            val copy = JSONObject(alt.toString())
            if (file != null && copy.optString("type") == "geojson") throttleLargeGeoJson(copy, sizeOf(file))
            sources.put(id, copy)
            missing -= id; swapped += id
        }
        if (swapped.isNotEmpty()) {
            for (i in 0 until layers.length()) layers.optJSONObject(i)?.takeIf { it.optString("source") in swapped }?.remove("source-layer")
            log("Map data stand-in sources used: $swapped")
        }
        fun fallbackFor(l: JSONObject) = l.optJSONObject("metadata")?.optString(FALLBACK_KEY)?.takeIf { it.isNotEmpty() }
        val hasFallbacks = (0 until layers.length()).any { i -> layers.optJSONObject(i)?.let { fallbackFor(it) } != null }
        if (missing.isEmpty() && !hasFallbacks && swapped.isEmpty() && upgraded.isEmpty() && !throttled) styleJson else {
            missing.forEach { sources.remove(it) }
            val kept = JSONArray()
            for (i in 0 until layers.length()) {
                val l = layers.optJSONObject(i) ?: continue
                val fb = fallbackFor(l)
                // a stand-in layer needs its own source too (the base map may be gone as well – overview only)
                val keep = (fb == null || fb in missing) && l.optString("source") !in missing
                if (keep) kept.put(l)
            }
            root.put("layers", kept)
            if (missing.isNotEmpty()) log("Optional map data missing, layers hidden: $missing")
            root.toString()
        }
    } catch (e: Exception) {
        Problems.warn("map", "Optional map data not checked, style used unchanged", e)
        styleJson
    }
}
