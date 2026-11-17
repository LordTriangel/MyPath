package com.onpath.app.suggest

import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.strings.*
import org.jetbrains.compose.resources.StringResource

/**
 * What app and planner hand to the screen from one wish (docs/design/tour-suggestions.md §7, stage 3 / 4): light cards
 * – the loop line, km, h, ascent, title, text line, badge, the sketch-7 pills – and the run's notes. Built from a
 * [SuggestResult] by [of]; nothing here points back into the result, so the network and the place (tens of MB) can go
 * as soon as the cards are made. Shared with the web planner (`WebSuggest` writes these cards as JSON).
 */
class SuggestCards(
    val cards: List<SuggestCard>,
    /** the run's notes in the text language ("Die Karte wird hier gerade aktualisiert …", start moved, …) */
    val notes: List<String>,
    /** a map cell in reach has no suggest file or an older one ([SuggestFile.CONTENT]): "Karte wird aktualisiert" */
    val mapUpdating: Boolean,
    /** the wish as the method understood it: hours from–to, metres up from–to */
    val hours: DoubleArray,
    val ascent: IntArray,
    /** loops the method built and judged (the pool) */
    val loops: Int,
    /** seconds per phase: network, facts, place, loops, judging */
    val times: Map<String, Double>,
    /** the zoom-10 cells in reach whose suggest file is missing or too old ([x, y]); empty from [of] – the app fills it */
    val cellsToFetch: List<IntArray> = emptyList(),
    /** no routing data / no way near the start: nothing could be read (the cards are empty) */
    val noData: Boolean = false,
    /** rd5 part keys ([com.onpath.app.routing.Rd5Parts.Part.key]) to load first – the app made no run (no cards, no notes) */
    val routingToLoad: List<String> = emptyList(),
) {
    companion object {
        /** The cards of a run's shown suggestions, in list order. */
        fun of(r: SuggestResult, cellsToFetch: List<IntArray> = emptyList()): SuggestCards {
            val shown = r.shown
            val ch = r.ch
            val cards = shown.map { c ->
                val off = c.official
                val n = c.numbers
                SuggestCard(c.id, c.name, off?.badge, off?.loopKm, n.km, n.hours, n.ascent, c.line,
                    SuggestPills.pills(c, shown, ch), c.coords(r.net))
            }
            return SuggestCards(cards, r.notes.toList(), r.mapUpdating, doubleArrayOf(ch.hours[0], ch.hours[1]),
                intArrayOf(ch.up[0].toInt(), ch.up[1].toInt()), r.candidates.size, LinkedHashMap(r.times), cellsToFetch)
        }

        /** Nothing could be read around the start (no routing data, no way there): no cards, the "none" note. */
        fun none(): SuggestCards = SuggestCards(emptyList(), listOf(SuggestText.noteNone()), false, DoubleArray(2), IntArray(2), 0,
            emptyMap(), noData = true)

        /** No run yet: the routing data within reach is not on the phone ([routingToLoad]). */
        fun loadRouting(parts: List<String>): SuggestCards = SuggestCards(emptyList(), emptyList(), false, DoubleArray(2), IntArray(2), 0,
            emptyMap(), routingToLoad = parts)
    }
}

/** One suggestion as the screen shows it. */
class SuggestCard(
    val id: Int,
    /** the title ("Rund um den Elbsee", a signed loop's name) */
    val name: String,
    /** „Ausgeschildert · <family>“ part for a signed loop ("Traumpfad", "Rundweg" …), null = our own loop */
    val badge: String?,
    /** km of the ride on the signed loop (null = our own loop) */
    val loopKm: Double?,
    val km: Double,
    val hours: Double,
    val ascent: Int,
    /** the generated text line on what the loop passes (never an operator's text) */
    val line: String,
    val pills: List<SuggestPill>,
    /** the loop's line, lon / lat pairs, closed (first = last = the start) */
    val coords: DoubleArray,
) {
    /** The line as points (for the map and for a plan from the loop). */
    fun points(): List<GeoPoint> = List(coords.size / 2) { GeoPoint(coords[2 * it + 1], coords[2 * it]) }
}

/** One pill of sketch 7: a strength (green) or a weakness / hint (yellow), level 1 … 3 = light … strong. */
class SuggestPill(val text: String, val strength: Boolean, val level: Int) {
    /** The planner's CSS class: g1…g3, y1…y3. */
    val cls: String get() = (if (strength) "g" else "y") + level
}

/**
 * A wish as app and planner ask it (the wish sheet, sketch 5 B): where, the activity as the app names it, the T level,
 * time from–to, metres up from–to (either end open), the Ziel kinds. [wish] turns it into the method's [Wish].
 */
class SuggestAsk(
    val lat: Double,
    val lon: Double,
    val activity: Activity,
    val sac: Int = 2,
    val hours: DoubleArray = doubleArrayOf(2.0, 3.0),
    val ascent: Array<Double?>? = null,
    val ziel: List<String> = emptyList(),
) {
    fun wish(S: SuggestSettings): Wish = Wish.of(S, lon, lat, methodActivity(activity), sac.coerceIn(1, 5), hours, ascent = ascent, ziel = ziel)

    companion object {
        /** The app's activity → the method's (settings.json activities): hike, bike, gravel, mtb. */
        fun methodActivity(a: Activity): String = when (a) {
            Activity.HIKE -> "hike"
            Activity.BIKE_TOURING, Activity.EBIKE -> "bike"
            Activity.BIKE_TREKKING -> "gravel"
            Activity.MTB, Activity.EMTB -> "mtb"
        }
    }
}

/**
 * The pills of sketch 7 (owner 2026-11-17, the POC page's `pills`; moved here from the planner's WebSuggest so app and
 * planner show the same): per axis Natur · Ruhe · Weg · Besonderes (no Form pill), only where the shown list differs
 * (spread ≥ 0.08): a strength when the card is in the top quarter of the list and above its mean, a weakness in the
 * bottom quarter and below the mean; intensity by the distance to the mean (< 0.08 light, < 0.15 clear, else strong).
 * The card's hints are yellow pills too, first, by weight (a missed wish – ascent, time – then road, through a town,
 * crossing / doubled). At most 2 green + 2 yellow.
 */
object SuggestPills {
    private val GOOD = arrayOf(Res.string.suggest_pill_natur_good, Res.string.suggest_pill_ruhe_good, Res.string.suggest_pill_weg_good,
        Res.string.suggest_pill_besonderes_good)
    private val BAD = arrayOf(Res.string.suggest_pill_natur_weak, Res.string.suggest_pill_ruhe_weak, null, Res.string.suggest_pill_besonderes_weak)

    private class Rated(val d: Double, val lvl: Int, val text: String)

    fun pills(c: Candidate, cs: List<Candidate>, ch: Character): List<SuggestPill> {
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
        return green.take(2).map { SuggestPill(it.text, true, it.lvl) } + y.take(2).map { SuggestPill(it.text, false, it.lvl) }
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
