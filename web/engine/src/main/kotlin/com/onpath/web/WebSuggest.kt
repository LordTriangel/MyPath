package com.onpath.web

import com.onpath.app.debug.Problems
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.Rd5Parts
import com.onpath.app.suggest.Candidate
import com.onpath.app.suggest.Character
import com.onpath.app.suggest.Land
import com.onpath.app.suggest.NetReader
import com.onpath.app.suggest.ReaderNetSource
import com.onpath.app.suggest.RouterProfile
import com.onpath.app.suggest.SuggestFile
import com.onpath.app.suggest.SuggestFiles
import com.onpath.app.suggest.SuggestSettings
import com.onpath.app.suggest.TourSuggester
import com.onpath.app.suggest.Wish
import com.onpath.strings.*
import org.jetbrains.compose.resources.StringResource
import java.io.File

/**
 * Round-trip suggestions in the browser planner (docs/design/tour-suggestions.md §7 "Stage 3 – the ready brief"): the
 * app's [TourSuggester], unchanged, on the routing data and the map cells' suggest files the page hands over. One entry
 * ([call], registered in WebApi as `suggest`) with two steps, so the page fetches only what a wish needs:
 *  - `need`: the wish → `{"parts":[["E6_N51.rd5",7],…],"cells":[[x,y],…],"settings":true|false}` – the rd5 parts within
 *    reach of the start and the zoom-10 cells whose `suggest.bin` the method reads ([cellsDir]/10-x-y/suggest.bin, a
 *    missing file = the map is being updated there);
 *  - `run`: the wish → the cards (`{"cards":[…],"notes":[…],"mapUpdating":…,"hours":[…],"ascent":[…],"ms":…}`) or
 *    `{"error":"…"}`.
 * Wish JSON: `{"lat","lon","activity":"HIKE"|…,"sac":2,"hours":[2,3],"ascent":[0,400]|null,"ziel":["gipfel",…]}`.
 * The card's pills follow sketch 7 (owner 2026-11-17, [pills]); the words are the shared strings (strings_suggest.xml).
 */
object WebSuggest {
    /** Where the page puts the method's numbers (tools/suggest-poc/settings.json, one file for POC, app and planner). */
    const val SETTINGS = "assets/suggest/settings.json"
    /** Where the page puts the cells' suggest files: suggest/10-x-y/suggest.bin. */
    private const val CELLS = "suggest"

    private fun root(): File = android.content.res.AssetManager.root?.parentFile ?: File("/onpath")

    private var settings: SuggestSettings? = null
    private var settingsText: String? = null

    /** The parsed suggest files (null = not there), kept while their bytes stay the same file. */
    private val files = HashMap<String, Pair<Long, SuggestFile?>>()

    private val cellFiles = SuggestFiles { x, y ->
        val f = File(root(), "$CELLS/10-$x-$y/${SuggestFile.NAME}")
        val key = "$x-$y"
        val len = if (f.isFile) f.length() else -1L
        val have = files[key]
        if (have != null && have.first == len) have.second else {
            val read = if (len > 0) try { SuggestFile.read(f.readBytes()) } catch (e: Exception) {
                Problems.warn("suggest", "suggest file of cell $key not readable – treated as missing", e); null
            } else null
            files[key] = len to read
            read
        }
    }

    fun call(op: String, json: String): String = try {
        when (op) {
            "need" -> need(json)
            "run" -> run(json)
            else -> err("unknown op $op")
        }
    } catch (e: Exception) {
        Problems.error("suggest", "tour suggestions failed ($op)", e)
        err(e.message ?: e.toString())
    }

    private fun err(text: String) = "{\"error\":" + Engine.str(text) + "}"

    private fun loadSettings(): SuggestSettings? {
        val f = File(root(), SETTINGS)
        if (!f.isFile) return null
        val text = f.readText()
        if (text != settingsText) { settings = SuggestSettings.parse(text); settingsText = text }
        return settings
    }

    /** The planner's activity → the method's (settings.json activities): hike, bike, gravel, mtb. */
    internal fun methodActivity(a: Activity): String = when (a) {
        Activity.HIKE -> "hike"
        Activity.BIKE_TOURING, Activity.EBIKE -> "bike"
        Activity.BIKE_TREKKING -> "gravel"
        Activity.MTB, Activity.EMTB -> "mtb"
    }

    internal class In(val wish: Wish, val activity: Activity, val sac: Int)

    internal fun parseWish(S: SuggestSettings, json: String): In {
        val j = SuggestSettings.parse(json).root
        val act = Activity.entries.firstOrNull { it.name == (j["activity"].v as? String) } ?: Activity.HIKE
        val sac = if (j["sac"].isNull) 2 else j["sac"].i.coerceIn(1, 5)
        val hours = j["hours"].list.map { it.d }.toDoubleArray().takeIf { it.isNotEmpty() } ?: doubleArrayOf(2.0, 3.0)
        val asc = if (j["ascent"].isNull) null else j["ascent"].list.map { it.v as? Double }.toTypedArray()
        val ziel = j["ziel"].list.mapNotNull { it.v as? String }
        val w = Wish.of(S, j["lon"].d, j["lat"].d, methodActivity(act), sac, hours, ascent = asc, ziel = ziel)
        return In(w, act, sac)
    }

    private fun need(json: String): String {
        val S = loadSettings() ?: return "{\"parts\":[],\"cells\":[],\"settings\":false}"
        val w = parseWish(S, json).wish
        val radius = Character(w, S).radius(S)
        // the network reader reaches the radius; a little more for ways leaving it at the rim
        val parts = Rd5Parts.forPoint(GeoPoint(w.lat, w.lon), radius + 2_000.0)
        val cells = Land.cellsFor(w.lon, w.lat, radius + 500.0)
        return buildString {
            append("{\"parts\":").append(parts.joinToString(",", "[", "]") { "[" + Engine.str(it.tile) + "," + it.index + "]" })
            append(",\"cells\":").append(cells.joinToString(",", "[", "]") { "[" + it[0] + "," + it[1] + "]" })
            append(",\"settings\":true}")
        }
    }

    private fun run(json: String): String {
        val S = loadSettings() ?: return err("no settings")
        val inp = parseWish(S, json)
        Engine.writeParts() // parts the page fetched for this wish into the local rd5 files
        val t0 = System.currentTimeMillis()
        val reader = NetReader(File(root(), "segments"))
        val profiles = File(root(), "assets/brouter")
        val source = ReaderNetSource(reader) { _ -> RouterProfile.of(profiles, inp.activity, inp.sac) }
        val r = try { TourSuggester.suggest(inp.wish, source, cellFiles, S) } catch (e: NetReader.ReadException) {
            Problems.warn("suggest", "no network around the start: ${e.message}", e)
            return buildString {
                append("{\"cards\":[],\"notes\":[").append(Engine.str(Txt.s(Res.string.suggest_note_none))).append("]")
                append(",\"mapUpdating\":false,\"noData\":true,\"ms\":").append(System.currentTimeMillis() - t0).append('}')
            }
        }
        val ms = System.currentTimeMillis() - t0
        val ch = r.ch
        val shown = r.shown
        Problems.trace("suggest", "${shown.size} cards of ${r.candidates.size} loops, $ms ms, ${r.times}, network ${reader.stats}")
        return buildString {
            append("{\"cards\":[")
            shown.forEachIndexed { i, c ->
                if (i > 0) append(',')
                card(this, c, r.net, pills(c, shown, ch))
            }
            append("],\"notes\":").append(r.notes.joinToString(",", "[", "]") { Engine.str(it) })
            append(",\"mapUpdating\":").append(r.mapUpdating)
            append(",\"hours\":[").append(Engine.num(ch.hours[0])).append(',').append(Engine.num(ch.hours[1])).append(']')
            append(",\"ascent\":[").append(ch.up[0].toInt()).append(',').append(ch.up[1].toInt()).append(']')
            append(",\"loops\":").append(r.candidates.size)
            append(",\"ms\":").append(ms)
            append(",\"times\":{").append(r.times.entries.joinToString(",") { Engine.str(it.key) + ":" + Engine.num(it.value, 3) }).append('}')
            append('}')
        }
    }

    private fun card(b: StringBuilder, c: Candidate, net: com.onpath.app.suggest.WayNet, pills: List<Pill>) {
        val n = c.numbers
        val off = c.official
        b.append("{\"id\":").append(c.id)
        b.append(",\"name\":").append(Engine.str(c.name))
        b.append(",\"badge\":").append(if (off != null) Engine.str(off.badge) else "null")
        b.append(",\"loopKm\":").append(if (off != null) Engine.num(off.loopKm, 1) else "null")
        b.append(",\"km\":").append(Engine.num(n.km, 1))
        b.append(",\"hours\":").append(Engine.num(n.hours, 3))
        b.append(",\"ascent\":").append(n.ascent)
        b.append(",\"line\":").append(Engine.str(c.line))
        b.append(",\"pills\":").append(pills.joinToString(",", "[", "]") { "{\"t\":" + Engine.str(it.text) + ",\"c\":\"" + it.cls + "\"}" })
        val xy = c.coords(net)
        b.append(",\"coords\":[")
        var k = 0
        while (k < xy.size) {
            if (k > 0) b.append(',')
            b.append('[').append(Engine.num(xy[k + 1], 5)).append(',').append(Engine.num(xy[k], 5)).append(']')
            k += 2
        }
        b.append("]}")
    }

    /** One pill: its words and its look – g1…g3 = a strength (light … strong), y1…y3 = a weakness or a hint. */
    internal class Pill(val text: String, val cls: String)

    private val GOOD = arrayOf(Res.string.suggest_pill_natur_good, Res.string.suggest_pill_ruhe_good, Res.string.suggest_pill_weg_good,
        Res.string.suggest_pill_besonderes_good)
    private val BAD = arrayOf(Res.string.suggest_pill_natur_weak, Res.string.suggest_pill_ruhe_weak, null, Res.string.suggest_pill_besonderes_weak)

    private class Rated(val d: Double, val lvl: Int, val text: String)

    /**
     * The pills of sketch 7 (owner 2026-11-17, the POC page's `pills`): per axis Natur · Ruhe · Weg · Besonderes (no Form
     * pill), only where the shown list differs (spread ≥ 0.08): a strength when the card is in the top quarter of the
     * list and above its mean, a weakness in the bottom quarter and below the mean; intensity by the distance to the
     * mean (< 0.08 light, < 0.15 clear, else strong). The card's hints are yellow pills too, first, by weight (a missed
     * wish – ascent, time – then road, through a town, crossing / doubled). At most 2 green + 2 yellow.
     */
    internal fun pills(c: Candidate, cs: List<Candidate>, ch: Character): List<Pill> {
        val green = ArrayList<Rated>()
        val yellow = ArrayList<Rated>()
        for (a in 0 until 4) {
            var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE; var sum = 0.0
            for (x in cs) { lo = minOf(lo, x.axes[a]); hi = maxOf(hi, x.axes[a]); sum += x.axes[a] }
            if (hi - lo < 0.08) continue
            val v = c.axes[a]
            val q = (v - lo) / (hi - lo); val mean = sum / cs.size; val d = Math.abs(v - mean)
            val lvl = if (d < 0.08) 1 else if (d < 0.15) 2 else 3
            if (q >= 0.75 && v > mean) {
                val res = if (a == 2 && ch.bike) Res.string.suggest_pill_weg_good_wheels else GOOD[a]
                green.add(Rated(d, lvl, Txt.s(res)))
            } else if (q <= 0.25 && v < mean) {
                yellow.add(Rated(d, lvl, if (a == 2) wegCause(c, cs, ch) else Txt.s(BAD[a]!!)))
            }
        }
        green.sortByDescending { it.d }
        yellow.sortByDescending { it.d }
        val y = hintPills(c, ch) + yellow
        return green.take(2).map { Pill(it.text, "g" + it.lvl) } + y.take(2).map { Pill(it.text, "y" + it.lvl) }
    }

    /** A weak Weg names its cause: the one where the card lies most above the list's mean (on foot paved / tracks / rough). */
    private fun wegCause(c: Candidate, cs: List<Candidate>, ch: Character): String {
        fun g(x: Candidate, i: Int) = x.numbers.groundPct.getOrElse(i) { 0 }.toDouble()
        val causes: List<Pair<StringResource, (Candidate) -> Double>> = if (!ch.bike) listOf(
            Res.string.suggest_pill_weg_paved to { x -> g(x, 0) },
            Res.string.suggest_pill_weg_tracks to { x -> x.numbers.trackPct.toDouble() },
            Res.string.suggest_pill_weg_bumpy to { x -> g(x, 3) },
        ) else listOf<Pair<StringResource, (Candidate) -> Double>>(Res.string.suggest_pill_weg_bumpy_wheels to { x -> g(x, 2) + g(x, 3) }) +
            (if (ch.wish.activity == "bike") listOf<Pair<StringResource, (Candidate) -> Double>>(Res.string.suggest_pill_weg_gravel to { x -> g(x, 1) }) else emptyList())
        var best = causes[0].first; var top = -Double.MAX_VALUE
        for ((res, f) in causes) {
            val above = f(c) - cs.sumOf { f(it) } / cs.size
            if (above > top) { top = above; best = res }
        }
        return Txt.s(best)
    }

    /** The card's hints as yellow pills, by weight (the conditions of SuggestText.hints, the POC page's `hintPills`). */
    private fun hintPills(c: Candidate, ch: Character): List<Rated> {
        val out = ArrayList<Pair<Int, Rated>>()
        val n = c.numbers
        val up = n.ascent; val h = n.hours
        val (lo, hi) = ch.hours[0] to ch.hours[1]
        val upMin = ch.up[0]; val upMax = ch.up[1]
        fun lvl3(x: Double, a: Double, b: Double) = if (x < a) 1 else if (x < b) 2 else 3
        if (up > upMax) out.add(0 to Rated(0.0, lvl3(up - upMax, 50.0, 150.0), Txt.s(Res.string.suggest_pill_ascent_over, up.toString())))
        if (up < upMin) out.add(0 to Rated(0.0, lvl3(upMin - up, 50.0, 150.0), Txt.s(Res.string.suggest_pill_ascent_under, up.toString())))
        if (h > hi + 1.0 / 120) { val m = Math.round((h - hi) * 60).toInt(); out.add(1 to Rated(0.0, if (m <= 10) 1 else if (m <= 30) 2 else 3, Txt.s(Res.string.suggest_pill_longer, m.toString()))) }
        if (h < lo - 1.0 / 120) { val m = Math.round((lo - h) * 60).toInt(); out.add(1 to Rated(0.0, if (m <= 10) 1 else if (m <= 30) 2 else 3, Txt.s(Res.string.suggest_pill_shorter, m.toString()))) }
        if (n.roadLongestM >= 200) out.add(2 to Rated(0.0, lvl3(n.roadLongestM.toDouble(), 300.0, 700.0), Txt.s(Res.string.suggest_pill_road, n.roadLongestM.toString())))
        if (n.townPct >= 30) out.add(3 to Rated(0.0, lvl3(n.townPct.toDouble(), 15.0, 30.0), Txt.s(Res.string.suggest_pill_town, n.townPct.toString())))
        if (n.selfCross != 0) out.add(4 to Rated(0.0, 1, Txt.s(Res.string.suggest_pill_crosses)))
        if (n.doubledPct >= 8) out.add(4 to Rated(0.0, 1, Txt.s(Res.string.suggest_pill_doubled)))
        return out.sortedWith(compareBy<Pair<Int, Rated>> { it.first }.thenByDescending { it.second.lvl }).map { it.second }
    }
}
