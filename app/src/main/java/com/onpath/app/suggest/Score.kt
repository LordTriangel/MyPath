package com.onpath.app.suggest


/**
 * Judging loops (the POC's `score.py`, frozen 2026-11-16): five axes – Natur, Ruhe, Weg, Besonderes read on the place's
 * yardstick, Form absolute –, a few hard rules, one selection over our loops and the official ones, and the words that
 * say why (title, reason line, hints, the generated line on what the loop passes – never an operator's text).
 */
object Score {
    val AXES = arrayOf("natur", "ruhe", "weg", "besonderes", "form")
    private val WORDS = mapOf("natur" to "Natur", "ruhe" to "Ruhe", "weg" to "Weg", "besonderes" to "Besonderes", "form" to "Form")

    class Sight(val name: String, val what: String, val lon: Double, val lat: Double)

    /** score.py `judge`: axes, points, numbers and the hard-rule reason of every loop – each on its own. */
    fun judge(P: Place, S: SuggestSettings, cands: List<Candidate>, sights: List<Sight>) {
        val F = P.F; val net = P.net; val K = S["form"]
        val sightBonus = K["sight"].d; val ringBonus = K["ring"].d; val ringRoad = K["ring_road_m"].d
        for (c in cands) {
            var core = c.edges.filter { P.core(it) }.toIntArray()
            if (core.isEmpty()) core = c.edges
            var m = 0.0
            for (e in core) m += F.ln[e]
            for (a in 0 until 4) {
                var s = 0.0
                for (e in core) s += P.v[a][e] * F.ln[e]
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
            val ring = (c.origin == "Rundweg" || c.origin == "offiziell") && roadRun(F, c.edges) <= ringRoad
            c.raw[3] += sightBonus * minOf(3, seen.size) + ringBonus * (if (ring) 1 else 0)
            finish(P, S, c, DoubleArray(4) { P.judge(it, c.raw[it]) }, seen)
        }
    }

    private fun finish(P: Place, S: SuggestSettings, c: Candidate, ax4: DoubleArray, seen: List<Sight>) {
        val ch = P.ch; val K = S["form"]
        fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
        val h = c.hours
        val parts = LinkedHashMap<String, Double>()
        parts["Zeit"] = 1.0 - clamp(maxOf((ch.hours[0] - h) / (K["short_zero"].d * ch.hours[0]), (h - ch.hours[1]) / (K["long_zero"].d * ch.hours[1])))
        parts["Höhenmeter"] = 1.0 - 0.7 * clamp((ch.up[0] - c.up) / maxOf(ch.up[0], 1.0))
        parts["doppelt"] = 1.0 - clamp(c.doubledM / c.m / K["doubled_zero"].d)
        parts["kreuzt sich"] = Math.pow(K["cross"].d, c.cross.toDouble())
        val ax = DoubleArray(5)
        for (a in 0 until 4) ax[a] = ax4[a]
        ax[4] = parts.getValue("Zeit") * parts.getValue("Höhenmeter") * parts.getValue("doppelt") * parts.getValue("kreuzt sich")
        for (a in 0 until 5) c.axes[a] = SuggestGeo.pyRound(ax[a], 3)
        var s = 0.0
        for (a in 0 until 5) s += ch.axis(AXES[a]) * ax[a]
        c.score = SuggestGeo.pyRound(100.0 * s, 1)
        c.formParts = LinkedHashMap()
        for ((k, v) in parts) if (v < 0.995) c.formParts[k] = SuggestGeo.pyRound(v, 2)
        c.numbers = numbers(P, c, seen)
        val roadMax = ch.family["road_walk_max_m"].d
        c.out = if (0 < roadMax && roadMax < c.numbers.roadLongestM) "langes Stück an einer Straße mit Autos" else null
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

    /** 'am Elbsee', 'an der Dhrontalsperre'. */
    fun am(name: String): String {
        val l = name.lowercase()
        return (if (listOf("sperre", "bucht", "aue", "lache").any { l.endsWith(it) }) "an der " else "am ") + name
    }

    private fun both(xs: List<String>) = if (xs.size < 3) xs.joinToString(" und ") else xs.dropLast(1).joinToString(", ") + " und " + xs.last()

    private val OWN_WORD = listOf("burg", "schloss", "ruine", "kapelle", "höhle", "wasserfall", "turm", "blick", "kirche", "aussicht", "fels")

    /** score.py `line`: one line on what the loop really passes – from the card's numbers, nothing invented. */
    fun line(n: Candidate.Numbers, known: List<Pair<String, Double>> = n.known): String {
        fun kind(nm: String) = if ("schutzgebiet" in nm.lowercase() || nm.startsWith("Nationalpark") || nm.startsWith("Naturpark")) nm else "das Schutzgebiet $nm"
        fun say(what: String, nm: String) = if (nm != what && OWN_WORD.any { it in nm.lowercase() }) nm else if (nm == what) what else "$what $nm"
        val named = n.sights.filter { it.first != "Aussichtspunkt" && it.second != it.first }
        val namedWhat = named.map { it.first }.toSet()
        val things = named.map { say(it.first, it.second) } +
            n.sights.filter { it.second == it.first && it.first != "Aussichtspunkt" && it.first !in namedWhat }.map { it.first }
        val views = n.sights.filter { it.first == "Aussichtspunkt" }.map { it.second }
        val bits = ArrayList<String>()
        if (n.reserves.isNotEmpty()) bits.add("durch " + both(n.reserves.take(2).map { kind(short(it.first)) }))
        if (n.lakes.isNotEmpty()) bits.add(am(n.lakes[0].first))
        if (things.isNotEmpty()) bits.add("vorbei an " + both(things.distinct().take(3)))
        if (n.rimKm >= 0.5) bits.add("${SuggestGeo.comma(n.rimKm)} km an Kanten mit Weitblick")
        if (views.isNotEmpty()) bits.add("Aussicht " + (if (views[0] != "Aussichtspunkt") views[0] else "unterwegs"))
        for ((nm, k) in known.take(1)) if (k >= 0.25 * n.km) bits.add("${SuggestGeo.comma(k)} km auf ${shortRoute(nm)}")
        if (n.waterKm >= 1 && n.lakes.isEmpty()) bits.add("${SuggestGeo.comma(n.waterKm)} km am Wasser")
        if (n.railKm >= 1) bits.add("${SuggestGeo.comma(n.railKm)} km Bahntrasse")
        val text = if (bits.isNotEmpty()) bits.joinToString(", ") else "${n.forestPct} % am Wald, ${n.groundPct[2] + n.groundPct[3]} % auf Naturwegen"
        return text.take(1).uppercase() + text.drop(1)
    }

    /** score.py `explain`: title, the reason line, the hints and the five-word verdict of a loop. */
    fun explain(P: Place, c: Candidate) {
        val n = c.numbers; val net = P.net
        val sLon = net.lon(P.start); val sLat = net.lat(P.start)
        var far = c.nodes[0]
        var fd = -1.0
        for (x in c.nodes) { val d = SuggestGeo.dist(sLon, sLat, net.lon(x), net.lat(x)); if (d > fd) { fd = d; far = x } }
        val where = "Richtung " + SuggestGeo.direction(sLon, sLat, net.lon(far), net.lat(far))
        val main = (n.reserves.take(2).map { short(it.first) } + n.lakes.take(1).map { it.first } +
            n.known.take(1).filter { it.second >= 0.25 * n.km }.map { shortRoute(it.first) } +
            (if (n.railKm >= 0.25 * n.km) listOf("Bahntrasse") else emptyList())).distinct().take(2)
        val title = main.joinToString(" – ")
        c.name = if (main.isNotEmpty()) (if (title.length <= 45) title else main[0])
        else c.aim.firstOrNull { " bei " in it || " im " in it } ?: where
        c.bearing = where
        fun km(rows: List<Pair<String, Double>>, pre: String) = rows.map { "${SuggestGeo.comma(it.second)} km $pre${it.first}" }
        fun kind(nm: String) = if ("schutzgebiet" in nm.lowercase() || nm.startsWith("Nationalpark") || nm.startsWith("Naturpark")) nm else "Schutzgebiet $nm"
        c.reasons = (km(n.reserves.map { kind(it.first) to it.second }, "im ") + n.lakes.map { "${SuggestGeo.comma(it.second)} km ${am(it.first)}" } +
            km(n.known, "auf ") + (if (n.waterKm >= 1 && n.lakes.isEmpty()) listOf("${SuggestGeo.comma(n.waterKm)} km am Wasser") else emptyList()) +
            (if (n.railKm >= 1) listOf("${SuggestGeo.comma(n.railKm)} km Bahntrasse") else emptyList()) +
            n.sights.take(1).map { (w, nm) -> if (nm != w) "$w $nm" else w } + (if (n.ferryM > 0) listOf("mit Fähre") else emptyList())).take(5)
        val ch = P.ch; val h = c.hours
        fun edge(v: Double, lo: Double, hi: Double): Double = if (hi > lo) (v - lo) / (hi - lo) else 0.0
        val hints = ArrayList<String>()
        if (c.up > ch.up[1]) hints.add("↑ ${c.up.toInt()} m – mehr als gewünscht (${ch.up[1].toInt()} m)")
        if (h < ch.hours[0]) hints.add("etwas kürzer als gewünscht")
        else if (h > ch.hours[1]) hints.add("etwas länger als gewünscht")
        else if (edge(h, ch.hours[0], ch.hours[1]) > 0.9) hints.add("am oberen Rand der gewünschten Zeit")
        if (c.up < ch.up[0]) hints.add("weniger Höhenmeter als gewünscht")
        else { val e = edge(c.up, ch.up[0], ch.up[1]); if (0.9 < e && e <= 1.0) hints.add("am oberen Rand der Höhenmeter") }
        if (n.roadLongestM >= 200) hints.add("${n.roadLongestM} m an einer Straße am Stück")
        if (n.doubledPct >= 8) hints.add("${n.doubledPct} % doppelt")
        if (n.selfCross != 0) hints.add("kreuzt sich einmal")
        if (n.townPct >= 30) hints.add("${n.townPct} % durch Häuser")
        c.hints = hints
        c.line = line(n)
        val ranked = (0 until 5).sortedBy { -c.axes[it] }
        val weak = ranked.reversed().filter { c.axes[it] < 0.5 }.take(2)
        c.why = "stark bei " + ranked.take(2).joinToString(" und ") { WORDS.getValue(AXES[it]) } +
            (if (weak.isNotEmpty()) " · schwächer bei " + weak.joinToString(" und ") { a ->
                WORDS.getValue(AXES[a]) + (if (a == 4 && c.formParts.isNotEmpty()) " (" + c.formParts.keys.joinToString(", ") + ")" else "")
            } else "")
    }

    private fun fits(P: Place, c: Candidate) = P.ch.hours[0] <= c.hours && c.hours <= P.ch.hours[1]

    /** score.py `order`: with "am Wasser entlang" a loop really at the water first, then inside the wished time, by points. */
    val order: (Place) -> Comparator<Candidate> = { P ->
        compareBy<Candidate>({ !(P.ch.water && it.numbers.waterPct >= 20) }, { !fits(P, it) }, { -it.score })
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
        fun atWater(c: Candidate) = P.ch.water && c.numbers.waterPct >= 20
        val route = if (P.ch.signed >= 1.0) P.lines.firstOrNull { it.kind == "route" && it.rank >= 0.9 } else null
        val shown = arrayListOf(ranked[0])
        if (!atWater(ranked[0])) ranked.firstOrNull { atWater(it) }?.let { shown.add(it) }
        if (route != null) {
            val onIt = ranked.filter { uses(it, route) && it.axes[4] >= 0.5 }
            onIt.sortedBy { -length(inter(core.getValue(it), route.edges)) }.take(1).forEach { if (it !in shown) shown.add(it) }
        }
        val max = K["max"].i
        val cover = K["cover_behind"].d
        for (c in ranked) {
            if (c.official != null && fits(P, c) && c !in shown && shown.size < max && pts(c) >= top - cover && fresh(c, shown)) shown.add(c)
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
