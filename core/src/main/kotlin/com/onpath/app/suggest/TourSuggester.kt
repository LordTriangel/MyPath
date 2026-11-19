package com.onpath.app.suggest


/** Where the core gets the way network around the start for a character (profile, parameters) and radius. */
fun interface NetSource {
    fun read(ch: Character, radiusM: Double): WayNet
}

/** The routing data as the app has it: [NetReader] with the character's profile (see [RouterProfile]) and coarse filter. */
class ReaderNetSource(private val reader: NetReader, private val profileOf: (Character) -> NetReader.Profile) : NetSource {
    override fun read(ch: Character, radiusM: Double): WayNet = reader.read(profileOf(ch), ch.wish.lon, ch.wish.lat, radiusM, coarseOf(ch))

    companion object {
        /** settings.json family.coarse → the reader's filter (wheels: minor ways left out unless a signed route runs there). */
        fun coarseOf(ch: Character): NetReader.Coarse {
            val c = ch.family["coarse"]
            if (c.isNull) return NetReader.Coarse.NONE
            return NetReader.Coarse(c["maxcf"].d.toFloat(), Regex(c["skip"].s), Regex(c["keep"].s))
        }
    }
}

/** What one wish gave: the shown suggestions first (in list order), then the rest of the pool by points. */
class SuggestResult(
    val ch: Character,
    val net: WayNet,
    val place: Place,
    val notes: List<String>,
    val candidates: List<Candidate>,
    /** seconds per phase: network, facts, place, loops, judging */
    val times: LinkedHashMap<String, Double>,
    /** a map cell in reach has no suggest file or an older one (content < [SuggestFile.CONTENT]): "Karte wird aktualisiert" */
    val mapUpdating: Boolean = false,
) {
    val shown: List<Candidate> get() = candidates.filter { it.chosen }
}

/**
 * Tour suggestions – a wish in, a few loops out, each with its reasons (docs/design/tour-suggestions.md §3; the POC's
 * `suggest.py`, frozen 2026-11-16, the batch of 2026-11-17 ported: official loops from the start, the Ziel and aims,
 * quality-trail measures). Pure Kotlin, no Android, shared with the web planner; nothing calls it yet.
 *
 * Inputs: the [Wish], the way network ([NetSource]), the map cells' suggest files ([SuggestFiles]: land grid, heights,
 * names, aims, signed routes and loops) and the method's numbers ([SuggestSettings], one file). Its words come from the
 * shared strings ([SuggestText]). Blocking (a 3 h bike wish: a few seconds on a phone).
 *
 * [check] runs between the phases (network, facts, place, loops, judging): the app passes its coroutine's
 * `ensureActive`, so a newer wish stops this one at the next phase instead of letting two runs hold memory at once.
 */
object TourSuggester {
    fun suggest(wish: Wish, netSource: NetSource, files: SuggestFiles, S: SuggestSettings, check: () -> Unit = {}): SuggestResult {
        val times = LinkedHashMap<String, Double>()
        var t = System.nanoTime()
        fun lap(key: String) { val now = System.nanoTime(); times[key] = (now - t) / 1e9; t = now; check() }
        val notes = ArrayList<String>()
        val ch = Character(wish, S)
        val radius = ch.radius(S)
        // a cell in reach without its suggest file, or with one older than the method needs (no aims before content 2):
        // the map is being updated there (the file comes with the cell's next map version)
        val updating = Land.cellsFor(wish.lon, wish.lat, radius).any { files.cell(it[0], it[1]).let { f -> f == null || f.content < SuggestFile.CONTENT } }
        if (updating) notes.add(SuggestText.noteMapUpdating())
        check()
        val net = netSource.read(ch, radius)
        val things = Named.load(files, wish.lon, wish.lat, radius + 500.0, ch.bike, S["facts"]["rank"])
        lap("network")
        val land = Land(files)
        val F = Facts(net, Named.Index(things), S["facts"], ch.bike, land)
        val (start, moved) = NetSearch.seatStart(net, net.start, F.ok)
        if (moved > 0) notes.add(SuggestText.noteStartMoved(moved))
        lap("facts")
        val search = NetSearch(net, F.ln)
        var P = Place(net, F, ch, S, start, land, search)
        // the way out through town still counts nothing in the rating (Place.access), but it is no note any more
        // (owner 2026-11-19: „Sowas brauchen wir nicht anzeigen. Das verwirrt den Nutzer nur und nimmt Platz weg“)
        lap("place")
        var cands = Generate(P, S).pool()
        if (cands.isEmpty() && P.steep.isNotEmpty() && ch.up[1] < ch.usualUp) {  // nothing below the wished ascent: flatter ways
            val steep = ArrayList(P.steep)
            P = Place(net, F, ch, S, start, land, search, climb = true)
            cands = Generate(P, S).pool()
            P.steep.addAll(steep)
        }
        val others = if (wish.official) Official.near(P, S, files) else emptyList()
        lap("loops")
        val sights = things.filter { it.kind == "sight" }.map { Score.Sight(it.name, it.what, it.dense[0], it.dense[1]) }
        val all = cands + others
        Score.judge(P, S, all, sights)
        for ((i, c) in all.withIndex()) c.id = i + 1
        val form = S["form"]
        fun inTime(c: Candidate) = (1 - form["short_zero"].d) * ch.hours[0] <= c.hours && c.hours <= (1 + form["long_zero"].d) * ch.hours[1]
        var shown = Score.choose(P, S, cands + others.filter { it.show && inTime(it) && it.up <= ch.up[1] })
        var steep: List<Candidate> = emptyList()
        if (shown.isEmpty()) {  // nothing stays below the wished ascent: the flattest few, each with a clear hint
            val over = P.steep + others.filter { it.show && inTime(it) }
            if (over.isNotEmpty()) {
                var least = over[0].up
                for (c in over) least = minOf(least, c.up)
                steep = ArrayList(P.steep)
                Score.judge(P, S, steep, sights)
                for ((i, c) in steep.withIndex()) c.id = all.size + i + 1
                shown = Score.choose(P, S, over.filter { it.up <= form["steep_share"].d * least }).take(form["steep_cards"].i)
                notes.add(SuggestText.noteSteep(ch.up[1].toInt(), SuggestGeo.pyRound(least, -1).toInt()))
            }
        }
        val every = all + steep
        for (c in every) {
            c.chosen = shown.any { it === c }
            if (c.chosen) {
                Score.explain(P, c)
                val off = c.official
                if (off != null) {  // our own line from what it passes, without "km auf <itself>"
                    c.name = off.name
                    c.line = Score.line(c.numbers, c.numbers.known.filter { off.name !in it.first })
                }
            }
        }
        if (shown.isEmpty()) notes.add(SuggestText.noteNone())
        else if (shown.maxOf { it.up } < ch.up[0]) notes.add(SuggestText.noteLessAscent())
        lap("judging")
        search.release()  // the result keeps the network and the place, not the searches' work arrays
        val rest = every.filter { !it.chosen }.sortedBy { -it.score }
        return SuggestResult(ch, net, P, notes, shown + rest, times, updating)
    }
}
