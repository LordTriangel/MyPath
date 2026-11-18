package com.onpath.app.suggest


/**
 * Judging loops (the POC's `score.py`, batch of 2026-11-17 ported): five axes – Natur, Ruhe, Weg, Besonderes read on
 * the place's yardstick, Form absolute –, a few hard rules, one selection over our loops and the official ones, and the
 * words that say why (title, reason line, hints, the generated line on what the loop passes – never an operator's text;
 * the words themselves in [SuggestText]). Since the batch: aims really reached count on Besonderes (more when the wish
 * names their Ziel), Natur counts the landscape's variety, the selection shows per chosen Ziel the best loop that leads
 * to it and the finest aims in reach, the order puts loops a few minutes outside the wished time with the ones inside.
 */
object Score {
    val AXES = arrayOf("natur", "ruhe", "weg", "besonderes", "form")
    /** the Form parts' names (Candidate.formParts keys; shown through SuggestText) */
    const val ZEIT = "Zeit"
    const val HOEHE = "Höhenmeter"
    const val DOPPELT = "doppelt"
    const val KREUZT = "kreuzt sich"

    class Sight(val name: String, val what: String, val lon: Double, val lat: Double)

    /** score.py `judge`: axes, points, numbers and the hard-rule reason of every loop – each on its own. */
    fun judge(P: Place, S: SuggestSettings, cands: List<Candidate>, sights: List<Sight>) {
        val F = P.F; val net = P.net; val K = S["form"]
        val sightBonus = K["sight"].d; val ringBonus = K["ring"].d; val ringRoad = K["ring_road_m"].d
        val most = S["aims"]["most"].i
        val V = S["quality"]["variety"]
        val vShare = V["share"].d; val vPerKm = V["per_km"].d
        for (c in cands) {
            var core = c.edges.filter { P.core(it) }.toIntArray()
            if (core.isEmpty()) core = c.edges
            var m = 0.0
            for (e in core) m += F.ln[e]
            for (a in 0 until 4) {
                var s = 0.0
                for (e in core) s += P.value(a, e) * F.ln[e]
                c.raw[a] = s / m
            }
            val byName = LinkedHashMap<String, Sight>()
            for (s in sights) {
                var near = false
                var i = 0
                while (i < c.nodes.size) {
                    val x = c.nodes[i]
                    if (SuggestGeo.dist(net.lon(x), net.lat(x), s.lon, s.lat) < 120.0) { near = true; break }
                    i += 3
                }
                if (near) byName[s.name] = s
            }
            val seen = byName.values.toList()
            // going round a signed loop is what the place's makers laid out – but along a road is always bad
            val ring = (c.origin == "Rundweg" || c.origin == "offiziell") && roadRun(F, c.edges) <= ringRoad
            c.raw[3] += sightBonus * minOf(3, seen.size) + ringBonus * (if (ring) 1 else 0)
            // aims really reached (a summit, a lake gone round or along, a hut in the middle …) count more than
            // passing by; the more when the wish names their Ziel (Aims)
            c.reached = Aims.reached(P, S, c.edges, P.ch.ziel)
            var gain = 0.0
            for (w in c.reached.map { it.gain }.sortedDescending().take(most)) gain += w
            c.raw[3] += gain
            c.variety = variety(P, S, core)
            val ax = DoubleArray(4) { P.judge(it, c.raw[it]) }
            // quality trails: the landscape changes along the way, not one uniform forest
            ax[0] *= 1.0 - vShare + vShare * minOf(1.0, c.variety / vPerKm)
            finish(P, S, c, ax, seen)
        }
    }

    /**
     * score.py `variety`: landscape changes per km along the walk (Deutscher Wanderverband: 'Abwechslung /
     * Landschaftswechsel') – every `step_m` stretch gets its dominant kind (village, water, forest, open land) and
     * each change of it counts.
     */
    fun variety(P: Place, S: SuggestSettings, edges: IntArray): Double {
        val F = P.F
        val step = S["quality"]["variety"]["step_m"].d
        fun kind(e: Int) = if (F.built[e]) 0 else if (F.water[e] > 0) 1 else if (F.green[e] >= 0.5) 2 else 3
        val kinds = ArrayList<Int>()
        var acc = 0.0
        var cur = DoubleArray(4)
        for (e in edges) {
            cur[kind(e)] += F.ln[e]
            acc += F.ln[e]
            if (acc >= step) {
                var best = 0
                for (k in 1 until 4) if (cur[k] > cur[best]) best = k
                kinds.add(best)
                acc = 0.0
                cur = DoubleArray(4)
            }
        }
        var total = 0.0
        for (e in edges) total += F.ln[e]
        var changes = 0
        for (i in 1 until kinds.size) if (kinds[i] != kinds[i - 1]) changes++
        return SuggestGeo.pyRound(changes / maxOf(1.0, total / 1000.0), 2)
    }

    private fun finish(P: Place, S: SuggestSettings, c: Candidate, ax4: DoubleArray, seen: List<Sight>) {
        val ch = P.ch; val K = S["form"]
        fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
        val h = c.hours
        val parts = LinkedHashMap<String, Double>()
        parts[ZEIT] = 1.0 - clamp(maxOf((ch.hours[0] - h) / (K["short_zero"].d * ch.hours[0]), (h - ch.hours[1]) / (K["long_zero"].d * ch.hours[1])))
        parts[HOEHE] = 1.0 - 0.7 * clamp((ch.up[0] - c.up) / maxOf(ch.up[0], 1.0))
        parts[DOPPELT] = 1.0 - clamp(c.doubledM / c.m / K["doubled_zero"].d)
        parts[KREUZT] = Math.pow(K["cross"].d, c.cross.toDouble())
        val ax = DoubleArray(5)
        for (a in 0 until 4) ax[a] = ax4[a]
        ax[4] = parts.getValue(ZEIT) * parts.getValue(HOEHE) * parts.getValue(DOPPELT) * parts.getValue(KREUZT)
        for (a in 0 until 5) c.axes[a] = SuggestGeo.pyRound(ax[a], 3)
        var s = 0.0
        for (a in 0 until 5) s += ch.axis(AXES[a]) * ax[a]
        c.score = SuggestGeo.pyRound(100.0 * s, 1)
        c.formParts = LinkedHashMap()
        for ((k, v) in parts) if (v < 0.995) c.formParts[k] = SuggestGeo.pyRound(v, 2)
        c.numbers = numbers(P, c, seen)
        c.waterGoal = ch.water && c.numbers.waterPct >= 20
        val roadMax = ch.family["road_walk_max_m"].d
        c.out = if (0 < roadMax && roadMax < c.numbers.roadLongestM) SuggestText.outRoad() else null
    }

    /** score.py `numbers`: the plain facts of a loop for the card – absolute, not relative to the place. */
    fun numbers(P: Place, c: Candidate, sights: List<Sight>): Candidate.Numbers {
        val F = P.F; val edges = c.edges; val total = c.m
        fun pct(test: (Int) -> Boolean): Int {
            var s = 0.0
            for (e in edges) if (test(e)) s += F.ln[e]
            return SuggestGeo.pyRoundInt(100.0 * s / total).toInt()
        }
        val kmBy = LinkedHashMap<Pair<String, String>, Double>()
        for (e in edges) {
            val keys = ArrayList<Pair<String, String>>()
            if (F.area[e] > 0) keys.add("reserves" to F.areas[F.area[e]])
            for (i in F.named[e]) {
                val th = F.things[i]
                val k = when (th.kind) { "route" -> "known"; "lake" -> "lakes"; else -> null } ?: continue
                keys.add(k to th.name)
            }
            for (k in keys) kmBy[k] = (kmBy[k] ?: 0.0) + F.ln[e] / 1000.0
        }
        val sorted = kmBy.entries.sortedBy { -it.value }
        fun top(kind: String, least: Double) =
            sorted.filter { it.key.first == kind && it.value >= least }.map { it.key.second to SuggestGeo.pyRound(it.value, 1) }.take(4)
        fun sum(test: (Int) -> Boolean): Double { var s = 0.0; for (e in edges) if (test(e)) s += F.ln[e]; return s }
        var waterW = 0.0
        for (e in edges) waterW += F.ln[e] * F.water[e]
        var ferry = 0.0
        for (e in PyIntSet.of(edges).toIntArray()) if (F.ferry[e]) ferry += F.ln[e]
        return Candidate.Numbers(
            km = SuggestGeo.pyRound(total / 1000.0, 2), hours = SuggestGeo.pyRound(c.hours, 2), ascent = c.up.toInt(),
            forestPct = pct { F.green[it] >= 0.5 }, deepPct = pct { F.depth[it] >= 0.5 }, townPct = pct { F.built[it] },
            streetPct = pct { F.traffic[it] >= 0.15 }, roadLongestM = roadRun(F, edges).toInt(), signedPct = pct { F.signed[it] > 0 },
            waterKm = SuggestGeo.pyRound(sum { F.water[it] > 0 } / 1000.0, 1), waterPct = SuggestGeo.pyRoundInt(100.0 * waterW / total).toInt(),
            railKm = SuggestGeo.pyRound(sum { F.rail[it] } / 1000.0, 1), groundPct = IntArray(4) { g -> pct { F.ground[it] == g } },
            ferryM = ferry.toInt(), selfCross = c.cross, doubledPct = SuggestGeo.pyRoundInt(100.0 * c.doubledM / total).toInt(),
            stemM = c.stemM.toInt(), reserves = top("reserves", 0.8), known = top("known", 0.8), lakes = top("lakes", 0.4),
            rimKm = SuggestGeo.pyRound(sum { F.rim[it] >= 0.5 } / 1000.0, 1),
            sights = sights.map { it.what to it.name }.take(6),
            pathPct = pct { F.width[it] == 0 }, variety = c.variety, aims = c.reached, trackPct = pct { F.width[it] == 1 },
        )
    }

    /** score.py `road_run`: the longest stretch in metres along a road with cars outside a town. */
    fun roadRun(F: Facts, edges: IntArray): Double {
        var run = 0.0
        var longest = 0.0
        for (e in edges) {
            run = if (F.traffic[e] >= 0.45 && !F.built[e]) run + F.ln[e] else 0.0
            longest = maxOf(longest, run)
        }
        return longest
    }

    private val BRACKET = Regex("""\s*\([^)]*\)""")
    private val PLACE_TAIL = Regex(" (?:zwischen|von|vom|bei|südlich|nördlich|westlich|östlich) ")
    private val SEPS = listOf(", ", " - ", " – ", " / ", " und ").map { Regex("(?<!-)" + Regex.escape(it)) }

    /** score.py `short`: a protected area's name short enough for a title. */
    fun short(name: String, most: Int = 30): String {
        var s = BRACKET.replace(name, "").split(" mit ")[0].trim()
        if (s.length > 40) s = PLACE_TAIL.split(s)[0]
        fun small(p: String) = p.split(Regex("\\s+")).count { it.isNotEmpty() && !it[0].isUpperCase() }
        for (sep in SEPS) {
            while (s.length > most && sep.containsMatchIn(s)) {
                val ab = sep.split(s, 2)
                s = if (small(ab[0]) <= small(ab[1])) ab[0] else ab[1]
            }
        }
        return s.ifEmpty { name }
    }

    /** score.py `short_route`: a signed route's name for a title or the line. */
    fun shortRoute(name: String): String {
        var s = BRACKET.replace(name, "").trim()
        for (sep in listOf(", ", " - ")) if (s.length > 40 && sep in s) s = s.split(sep)[0]
        if (s.length > 40 && " · " in s) s = s.split(" · ", limit = 2)[1]
        return s.ifEmpty { name }
    }

    /** 'am Elbsee', 'an der Dhrontalsperre' ([SuggestText.am]). */
    fun am(name: String): String = SuggestText.am(name)

    /** score.py `line` ([SuggestText.line]). */
    fun line(n: Candidate.Numbers, known: List<Pair<String, Double>> = n.known): String = SuggestText.line(n, known)

    /** score.py `explain`: title, the reason line, the hints and the five-word verdict of a loop. */
    fun explain(P: Place, c: Candidate) {
        val n = c.numbers; val net = P.net
        val sLon = net.lon(P.start); val sLat = net.lat(P.start)
        var far = c.nodes[0]
        var fd = -1.0
        for (x in c.nodes) { val d = SuggestGeo.dist(sLon, sLat, net.lon(x), net.lat(x)); if (d > fd) { fd = d; far = x } }
        val where = SuggestText.toward(SuggestText.direction(sLon, sLat, net.lon(far), net.lat(far)))
        // what the loop leads to names it first: the wished Ziel's aims, then the finest reached (a summit, a lake …)
        // (one per kind of Ziel: a place to eat the one nearest the middle; a lake when gone along a good part)
        fun worthy(a: Aims.Reached) = (a.goal || a.worth >= 0.7) && (a.what != "lake" || a.round >= 0.5) && a.name != "Aussichtspunkt"
        val perKind = LinkedHashMap<String, Aims.Reached>()
        for (a in n.aims.filter { worthy(it) }.sortedWith(compareBy<Aims.Reached>({ !it.goal }, {
            if (it.ziel == "einkehr") Math.abs(it.at - 0.5) else -it.worth * (1 + it.round) - it.ele / 10000.0  // of two summits the higher
        }))) if (!perKind.containsKey(a.ziel)) perKind[a.ziel] = a
        var lead = perKind.values.sortedWith(compareBy<Aims.Reached>({ !it.goal }, { -it.worth })).map { it.name }
        lead = lead.filterIndexed { i, x -> lead.take(i).none { y -> y in x || x in y } }  // "Eckbauer", not "Gasthof Eckbauer"
        val main = (lead.take(2) + n.reserves.take(2).map { short(it.first) } + n.lakes.take(1).map { it.first } +
            n.known.take(1).filter { it.second >= 0.25 * n.km }.map { shortRoute(it.first) } +
            (if (n.railKm >= 0.25 * n.km) listOf(SuggestText.rail()) else emptyList())).distinct().take(2)
        val title = main.joinToString(" – ")
        c.name = if (main.isNotEmpty()) (if (title.length <= 45) title else main[0])
        else c.aim.firstOrNull { " bei " in it || " im " in it || it in P.spotNames } ?: where
        c.bearing = where
        c.reasons = SuggestText.reasons(n)
        c.hints = SuggestText.hints(c, P.ch)
        c.line = line(n)
        c.why = SuggestText.why(c)
    }

    /** the wished time is the wish: loops inside it come before loops near it (the selection) */
    private fun fits(P: Place, c: Candidate) = P.ch.hours[0] <= c.hours && c.hours <= P.ch.hours[1]

    /** score.py `goals`: the wished Ziel kinds a loop leads to (a reached aim of that kind; Wasser also: a fifth of the
     * way at water). */
    fun goals(c: Candidate): Set<String> {
        val out = LinkedHashSet<String>()
        for (a in c.reached) if (a.goal) out.add(a.ziel)
        if (c.waterGoal) out.add("wasser")
        return out
    }

    /**
     * score.py `order`: with a Ziel the loops that lead to it first, then loops about inside the wished time, each by
     * points. "About": a few minutes short or long count as inside (owner 2026-11-17: „wenn nur ganz knapp drüber,
     * drunter – dann auch oben lassen“) – 10 % of the range's end, at least 10 minutes.
     */
    val order: (Place) -> Comparator<Candidate> = { P ->
        val lo = P.ch.hours[0]; val hi = P.ch.hours[1]
        fun slack(h: Double) = maxOf(0.1 * h, 1.0 / 6.0)
        compareBy<Candidate>({ !(P.ch.ziel.isNotEmpty() && goals(it).isNotEmpty()) },
            { !(lo - slack(lo) <= it.hours && it.hours <= hi + slack(hi)) }, { -it.score })
    }

    /** score.py `choose`: one selection over all cards – ours and the official loops that fit the wish alike. */
    fun choose(P: Place, S: SuggestSettings, cands: List<Candidate>): List<Candidate> {
        val F = P.F; val K = S["select"]
        val ahead = K["official_ahead"].d
        fun pts(c: Candidate) = c.score + (if (c.official != null) ahead else 0.0)
        val ranked = cands.filter { it.out == null }.sortedWith(compareBy<Candidate>({ !fits(P, it) }, { -pts(it) }))
        if (ranked.isEmpty()) return emptyList()
        val core = HashMap<Candidate, PyIntSet>()
        for (c in ranked) {
            val s = PyIntSet()
            for (e in c.edges) if (P.core(e)) s.add(e)
            core[c] = if (s.isEmpty()) PyIntSet.of(c.edges) else s
        }
        fun length(s: PyIntSet): Double { var l = 0.0; for (e in s.toIntArray()) l += F.ln[e]; return if (l == 0.0) 1.0 else l }
        fun inter(a: PyIntSet, b: PyIntSet): PyIntSet {
            val (small, big) = if (b.size > a.size) a to b else b to a
            val out = PyIntSet()
            for (e in small.toIntArray()) if (e in big) out.add(e)
            return out
        }
        val overlap = K["overlap"].d
        fun fresh(c: Candidate, shown: List<Candidate>) = shown.all { o ->
            length(inter(core.getValue(c), core.getValue(o))) <= overlap * minOf(length(core.getValue(c)), length(core.getValue(o)))
        }
        fun uses(c: Candidate, t: Place.Spot) =
            length(inter(core.getValue(c), t.edges)) >= (if (t.kind != "area") 0.25 * P.target else minOf(2500.0, 0.3 * t.m))
        val top = pts(ranked[0])
        // what the wish names is always shown: per chosen Ziel the best loop that leads to it (and first); for the
        // gemütlich end the best loop along the first known route here
        val route = if (P.ch.signed >= 1.0) P.lines.firstOrNull { it.kind == "route" && it.rank >= 0.9 } else null
        val shown = arrayListOf(ranked[0])
        for (z in P.ch.ziel) {
            if (shown.none { z in goals(it) }) ranked.firstOrNull { z in goals(it) }?.let { shown.add(it) }
        }
        if (route != null) {  // the clean loop that stays on the route longest
            val onIt = ranked.filter { uses(it, route) && it.axes[4] >= 0.5 }
            onIt.sortedBy { -length(inter(core.getValue(it), route.edges)) }.take(1).forEach { if (it !in shown) shown.add(it) }
        }
        val max = K["max"].i
        val cover = K["cover_behind"].d
        // what stands out here is shown if its best loop is good enough: an official loop inside the wished time (what
        // the place's makers laid out), the finest aims, the top lines, then the areas
        for (c in ranked) {
            if (c.official != null && fits(P, c) && c !in shown && shown.size < max && pts(c) >= top - cover && fresh(c, shown)) shown.add(c)
        }
        val finest = LinkedHashMap<String, Candidate>()
        for (c in ranked) for (a in c.reached) {
            if (a.worth >= 0.9 && (a.what != "lake" || a.round >= 0.5) && !finest.containsKey(a.name)) finest[a.name] = c
        }
        for (c in finest.values.take(2)) {
            if (c !in shown && shown.size < max && pts(c) >= top - cover && fresh(c, shown)) shown.add(c)
        }
        for (t in P.lines.filter { it.rank >= 0.9 }.take(2) + P.areas) {
            val best = ranked.firstOrNull { uses(it, t) }
            if (best != null && best !in shown && shown.size < max && pts(best) >= top - cover && fresh(best, shown)) shown.add(best)
        }
        for ((behind, most) in listOf(K["behind"].d to max, K["least_behind"].d to K["least"].i)) {
            for (c in ranked) {
                if (shown.size >= most) break
                if (c !in shown && pts(c) >= top - behind && fresh(c, shown)) shown.add(c)
            }
        }
        return shown.take(max).sortedWith(order(P))
    }
}
