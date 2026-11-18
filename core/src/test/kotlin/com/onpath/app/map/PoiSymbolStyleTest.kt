package com.onpath.app.map

import com.onpath.app.poi.POI_LAYER_PREFIX
import com.onpath.app.poi.PoiFilter
import com.onpath.app.poi.PoiKinds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.floor
import kotlin.math.pow

/**
 * A POI's name is never drawn without its symbol (owner 2026-11-15, Hilden: a bus stop showed only its name
 * „Dorothea-Erxleben-Straße" – it read like a street name, and a hold on it opened the place menu). A machine check over
 * every bundled style (format 1 + 2, every theme file) and the sprite: for every POI kind × zoom 10–18 × named / unnamed
 * (and the bus stop variants), each POI layer that would draw the name also draws the symbol in the SAME symbol (one
 * layer, icon required, text optional – MapLibre then places the name only when the icon is placed; a name in a layer
 * of its own cannot know whether its icon was placed), symbols first (no name takes the room of an icon), the symbol's
 * image exists in the sprite (1× and 2×), every POI kind has a symbol at some zoom, and a kind hidden in Settings → POIs
 * ([PoiFilter] on every poi- layer, as [LayerSettings]) hides its name too. Owner 2026-11-17: no POI names below z17,
 * from z17 each symbol with its name (tools/make_style.py pois()).
 */
class PoiSymbolStyleTest {
    private val styles: Map<String, JSONObject> = buildMap {
        val dir = File("../app/src/main/assets/style")
        for (f in (dir.listFiles().orEmpty().toList() + File(dir, "f2").listFiles().orEmpty().toList())
            .filter { it.isFile && it.name.startsWith("onpath") && it.name.endsWith(".json") }.sortedBy { it.path })
            put(f.relativeTo(dir).path, JSONObject(f.readText()))
    }
    private val sprites = listOf("onpath.json", "onpath@2x.json")
        .map { name -> JSONObject(File("../app/src/main/assets/sprites/$name").readText()).keys().asSequence().toSet() }

    private fun layers(s: JSONObject) = s.getJSONArray("layers").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun poiLayers(s: JSONObject) = layers(s).filter { it.optString("source") == "pois" }

    /** The feature shapes the POI data has: every kind named and unnamed; bus stops also merged pairs and detail stops. */
    private fun features(kind: String): List<Map<String, Any>> {
        val base = listOf(
            mapOf("kind" to kind, "id" to "n42", "pr" to 7, "name" to "Dorothea-Erxleben-Straße"),
            mapOf("kind" to kind, "id" to "n43", "pr" to 3),
        )
        return if (kind != "bus") base
        else base + base.map { it + ("merged" to 1) } + base.map { it + ("detail" to 1) }
    }

    private val kinds = PoiKinds.all.map { it.key }
    private val zooms = (20..36).map { it / 2.0 } // 10, 10.5 … 18 (the app's max zoom)

    /** POI names from here (owner 2026-11-17, tools/make_style.py POI_NAME_ZOOM). */
    private val NAME_ZOOM = 17.0

    private fun active(l: JSONObject, z: Double) = z >= l.optDouble("minzoom", 0.0) && z < l.optDouble("maxzoom", 24.0)

    private class Drawn(val icon: String?, val text: String?)

    /** What [l] draws for feature [f] at zoom [z] (layout evaluated at the tile's integer zoom, as MapLibre does). */
    private fun drawn(l: JSONObject, f: Map<String, Any>, z: Double): Drawn? {
        if (!active(l, z)) return null
        if (l.has("filter") && !truthy(Expr(f, z).eval(l.get("filter")))) return null
        val lo = l.optJSONObject("layout") ?: JSONObject()
        val paint = l.optJSONObject("paint") ?: JSONObject()
        val tile = Expr(f, floor(z))
        val icon = lo.opt("icon-image")?.let { tile.eval(it) as? String }?.takeIf { it.isNotEmpty() }
            ?.takeIf { num(Expr(f, z).eval(paint.opt("icon-opacity") ?: 1)) > 0.0 }
        val text = lo.opt("text-field")?.let { tile.eval(it)?.toString() }?.takeIf { it.isNotBlank() }
            ?.takeIf { num(Expr(f, z).eval(paint.opt("text-opacity") ?: 1)) > 0.0 }
            ?.takeIf { num(tile.eval(lo.opt("text-size") ?: 16)) > 2.0 }
        return Drawn(icon, text)
    }

    /** The Hilden case as a rule: a layer that draws a POI name draws its symbol in the same symbol, icon required. */
    @Test fun aPoiNameIsNeverDrawnWithoutItsSymbol() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) for (l in poiLayers(s)) {
            val lo = l.optJSONObject("layout") ?: JSONObject()
            for (kind in kinds) for (f in features(kind)) for (z in zooms) {
                val d = drawn(l, f, z) ?: continue
                if (d.text == null) continue
                val where = "$file ${l.getString("id")} $kind z$z ${if (f.containsKey("name")) "named" else "unnamed"}"
                if (d.icon == null) bad += "$where: name „${d.text}\" drawn without a symbol"
                else {
                    if (lo.optBoolean("icon-optional", false)) bad += "$where: icon optional – the name could stand alone"
                    if (!lo.optBoolean("text-optional", false)) bad += "$where: text not optional – a long name would hide the symbol"
                }
            }
        }
        assertTrue("POI names drawn without their symbol (${bad.size}):\n" + bad.distinct().take(40).joinToString("\n"), bad.isEmpty())
    }

    /**
     * Symbols first (owner 2026-11-16 / 17, after the before / after pictures of Hilden, Soho, Meran, Algund): a POI
     * name never pushes out a symbol. MapLibre places symbols from the top layer down, so at every zoom each POI layer
     * that draws a name taking room lies below (= is placed after) every POI layer whose icons take room.
     * The owner's accepted exception (2026-11-17, „ok, we keep the new solution“): from z17 ([NAME_ZOOM]) each POI's
     * name is in its icon's own symbol, so where it is crowded a name may take the room of a less important icon placed
     * after it (render check: Soho z17 −8 of 52 icons, Meran z17 −3 of 32, nothing lost below z17). Only names in an
     * icon's own symbol get that exception; a name layer of its own is never placed before or with the icons.
     */
    @Test fun symbolsFirstNoNameTakesTheRoomOfAnIcon() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) {
            val all = layers(s)
            for (z in zooms) {
                val icons = mutableListOf<Int>(); val names = mutableListOf<Int>(); val withIcon = mutableSetOf<Int>()
                all.forEachIndexed { i, l ->
                    if (l.optString("source") != "pois") return@forEachIndexed
                    val lo = l.optJSONObject("layout") ?: JSONObject()
                    var icon = false; var text = false
                    for (kind in kinds) for (f in features(kind)) {
                        val d = drawn(l, f, z) ?: continue
                        if (d.icon != null && !lo.optBoolean("icon-ignore-placement", false)) icon = true
                        if (d.text != null && !lo.optBoolean("text-ignore-placement", false)) text = true
                        if (d.text != null && d.icon != null) withIcon += i
                    }
                    if (icon) icons += i
                    if (text) names += i
                }
                for (n in names) for (i in icons) if (n >= i) {
                    if (z >= NAME_ZOOM && n in withIcon) continue // the owner's exception: a name in its icon's symbol
                    bad += "$file z$z: ${all[n].getString("id")} places names that take room before or with the icons of ${all[i].getString("id")}"
                }
            }
        }
        assertTrue("POI names placed before / with icons (${bad.size}):\n" + bad.distinct().take(30).joinToString("\n"), bad.isEmpty())
    }

    /** Owner 2026-11-17: POI names normally hidden – below z17 no POI layer draws a name, only icons. */
    @Test fun noPoiNamesBelowZoom17() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) for (l in poiLayers(s)) for (kind in kinds) for (f in features(kind))
            for (z in zooms.filter { it < NAME_ZOOM }) {
                val text = drawn(l, f, z)?.text ?: continue
                bad += "$file ${l.getString("id")} $kind z$z: „$text\""
            }
        assertTrue("POI names below z$NAME_ZOOM (${bad.size}):\n" + bad.distinct().take(30).joinToString("\n"), bad.isEmpty())
    }

    /** … and from z17 every drawn symbol of a named POI carries its name (written out, owner 2026-11-17). */
    @Test fun fromZoom17ANamedPoisSymbolCarriesItsName() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) for (l in poiLayers(s)) for (kind in kinds) for (f in features(kind).filter { it.containsKey("name") })
            for (z in zooms.filter { it >= NAME_ZOOM }) {
                val d = drawn(l, f, z) ?: continue
                if (d.icon != null && d.text != f["name"]) bad += "$file ${l.getString("id")} $kind z$z: symbol without its name"
            }
        assertTrue(bad.distinct().take(30).joinToString("\n"), bad.isEmpty())
    }

    @Test fun everySymbolIsInTheSpriteAndEveryKindHasOne() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) {
            val drawnKinds = mutableSetOf<String>()
            for (l in poiLayers(s)) for (kind in kinds) for (f in features(kind)) for (z in zooms) {
                val icon = drawn(l, f, z)?.icon ?: continue
                drawnKinds += kind
                sprites.forEachIndexed { i, names ->
                    if (icon !in names) bad += "$file ${l.getString("id")} $kind z$z: \"$icon\" missing in sprite ${listOf("1×", "2×")[i]}"
                }
            }
            for (k in kinds - drawnKinds) bad += "$file: kind $k (Settings → POIs) has no symbol at any zoom"
        }
        assertTrue(bad.distinct().take(40).joinToString("\n"), bad.isEmpty())
    }

    /** Icons collide (no pile-ups), and nothing that collides ignores placement. */
    @Test fun poiSymbolsCollide() {
        for ((file, s) in styles) for (l in poiLayers(s)) {
            val lo = l.optJSONObject("layout") ?: continue
            if (!lo.has("icon-image")) continue
            assertTrue("$file ${l.getString("id")}: icons collide", !lo.optBoolean("icon-allow-overlap", false))
            assertTrue("$file ${l.getString("id")}: icons take room", !lo.optBoolean("icon-ignore-placement", false))
        }
    }

    /** Settings → POIs: the filter the app puts on every poi- layer hides a kind's symbol and its name alike. */
    @Test fun aKindHiddenInSettingsHidesItsNameToo() {
        val bad = mutableListOf<String>()
        for ((file, s) in styles) {
            for (l in poiLayers(s)) if (!l.getString("id").startsWith(POI_LAYER_PREFIX))
                bad += "$file ${l.getString("id")}: draws POIs but is not a poi- layer – the Settings filter would miss it"
            for (kind in kinds) for (l in poiLayers(s)) {
                val filtered = PoiFilter.layerFilter(l.opt("filter")?.toString(), kinds, setOf(kind))
                val hidden = JSONObject(l.toString()).also { c -> if (filtered != null) c.put("filter", JSONArray(filtered)) }
                for (f in features(kind)) for (z in zooms) {
                    val d = drawn(hidden, f, z) ?: continue
                    if (d.icon != null || d.text != null) bad += "$file ${l.getString("id")} $kind z$z: still drawn when hidden"
                }
            }
        }
        assertTrue(bad.distinct().take(40).joinToString("\n"), bad.isEmpty())
    }

    // --- a small evaluator for the expressions the POI layers use; anything else fails loudly ---

    private fun truthy(v: Any?) = v == true
    private fun num(v: Any?): Double = when (v) { is Number -> v.toDouble(); is String -> v.toDoubleOrNull() ?: 0.0; else -> 0.0 }

    private inner class Expr(val f: Map<String, Any>, val zoom: Double) {
        fun eval(e: Any?): Any? {
            if (e !is JSONArray) return if (e == JSONObject.NULL) null else e
            if (e.length() == 0) return e
            val op = e.get(0) as? String ?: error("not an expression: $e")
            fun a(i: Int) = eval(e.get(i))
            return when (op) {
                "literal" -> e.get(1)
                "zoom" -> zoom
                "get" -> f[e.getString(1)]
                "has" -> f.containsKey(e.getString(1))
                "!" -> !truthy(a(1))
                "all" -> (1 until e.length()).all { truthy(a(it)) }
                "any" -> (1 until e.length()).any { truthy(a(it)) }
                "==" -> eq(a(1), a(2))
                "!=" -> !eq(a(1), a(2))
                ">=" -> num(a(1)) >= num(a(2))
                ">" -> num(a(1)) > num(a(2))
                "<=" -> num(a(1)) <= num(a(2))
                "<" -> num(a(1)) < num(a(2))
                "in" -> { val x = a(1); val list = a(2) as JSONArray; (0 until list.length()).any { eq(list.get(it), x) } }
                "coalesce" -> (1 until e.length()).firstNotNullOfOrNull { a(it) }
                "concat" -> (1 until e.length()).joinToString("") { str(a(it)) }
                "to-string" -> str(a(1))
                "to-number" -> (1 until e.length()).firstNotNullOfOrNull { i -> a(i)?.let { v -> num(v).takeIf { v is Number || (v as? String)?.toDoubleOrNull() != null } } } ?: 0.0
                "+" -> (1 until e.length()).sumOf { num(a(it)) }
                "*" -> (1 until e.length()).fold(1.0) { p, i -> p * num(a(i)) }
                "-" -> if (e.length() == 2) -num(a(1)) else num(a(1)) - num(a(2))
                "max" -> (1 until e.length()).maxOf { num(a(it)) }
                "min" -> (1 until e.length()).minOf { num(a(it)) }
                "case" -> { var i = 1; while (i + 1 < e.length()) { if (truthy(a(i))) return a(i + 1); i += 2 }; a(e.length() - 1) }
                "match" -> {
                    val x = a(1); var i = 2
                    while (i + 1 < e.length()) {
                        val label = e.get(i)
                        val hit = if (label is JSONArray) (0 until label.length()).any { eq(label.get(it), x) } else eq(label, x)
                        if (hit) return a(i + 1); i += 2
                    }
                    a(e.length() - 1)
                }
                "step" -> { val x = num(a(1)); var out = a(2); var i = 3; while (i + 1 < e.length() && x >= num(e.get(i))) { out = a(i + 1); i += 2 }; out }
                "interpolate" -> {
                    val type = e.getJSONArray(1); val x = num(a(2))
                    val stops = (3 until e.length() step 2).map { num(e.get(it)) to num(a(it + 1)) }
                    if (x <= stops.first().first) return stops.first().second
                    if (x >= stops.last().first) return stops.last().second
                    val k = stops.indexOfLast { it.first <= x }
                    val (x0, y0) = stops[k]; val (x1, y1) = stops[k + 1]
                    val base = if (type.getString(0) == "exponential") type.getDouble(1) else 1.0
                    val t = if (base == 1.0) (x - x0) / (x1 - x0) else (base.pow(x - x0) - 1) / (base.pow(x1 - x0) - 1)
                    y0 + (y1 - y0) * t
                }
                else -> error("expression \"$op\" not known to the POI style check – teach it: $e")
            }
        }

        private fun str(v: Any?) = when (v) { null -> ""; is Double -> if (v == floor(v)) v.toLong().toString() else v.toString(); else -> v.toString() }
        private fun eq(x: Any?, y: Any?) = if (x is Number && y is Number) x.toDouble() == y.toDouble() else x == y
    }
}
