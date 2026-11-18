package com.onpath.app.suggest

/**
 * Aims – what a loop can lead to: a summit, a lake, a viewpoint, a hut or a café, a castle or a chapel, a waterfall (the
 * POC's `aims.py`, batch of 2026-11-17).
 *
 * One mechanism for every kind (owner 2026-11-17): an aim in reach is a turning point ([Generate]), the loop is built
 * around it; a loop that really reaches one (not 110 m beside it) counts more on Besonderes ([Score.judge]), the more
 * when the wish names its Ziel; the card names it. Where an aim lies:
 * - a point (peak, viewpoint, hut, castle …): the ways within `reach_m` of it (a peak: `peak_reach_m`, and the way not
 *   more than `peak_below` metres lower) – the far end of the nearest one is the turning point, so a loop that turns
 *   there passes it; a dead end up to a summit is walked out and back (the assembly keeps it)
 * - a lake: its shore (the ways along it, [Named.Index]) – the turning point is the shore node farthest from where one
 *   first meets it; the shore's ways are cheap while the loop aims at it, so the loop goes round, or along one side
 *   there and the other back.
 * Sets iterate in Python's order ([PyIntSet]) where the POC takes "the first of equals".
 */
object Aims {
    /** A sight's word (Named.SIGHTS) → the aim kind; a cave is no aim. */
    private val SIGHT_WHAT = mapOf("Aussichtspunkt" to "viewpoint", "Burg" to "castle", "Ruine" to "ruins", "Kapelle" to "chapel",
        "Wasserfall" to "waterfall")

    /**
     * An aim in reach: [ziel] = its Ziel kind (gipfel …), [what] = peak / lake / viewpoint / hut …, [edges] = the ways
     * at it (a lake: its shore), [node] = the turning point, [dense] = a lake's shore points (else the point itself).
     */
    class Aim(val ziel: String, val what: String, val name: String, val ele: Int, val worth: Double, val edges: PyIntSet,
              val node: Int, val lon: Double, val lat: Double, val shoreM: Double, val dense: DoubleArray)

    /** An aim a loop really reaches: [at] = share of the loop where it is first met, [round] = share of a lake's shore
     * the loop runs along (both rounded to 2 places as the POC keeps them), [goal] = of a chosen Ziel and worth > 0,
     * [gain] = what it adds to the loop's raw Besonderes ([worth] of the unrounded shares). */
    class Reached(val ziel: String, val what: String, val name: String, val ele: Int, val at: Double, val round: Double,
                  val worth: Double, val goal: Boolean, val gain: Double)

    private const val K = 0.0012

    private fun cellKey(lon: Double, lat: Double): Long =
        ((lon / K).toInt().toLong() shl 32) or ((lat / K * 1.5).toInt().toLong() and 0xffffffffL)

    private fun shift(key: Long, dx: Int, dy: Int): Long =
        (((key shr 32).toInt() + dx).toLong() shl 32) or ((key.toInt() + dy).toLong() and 0xffffffffL)

    /** aims.py `find`: the aims in reach of the start – only aims a way leads to. */
    fun find(P: Place, S: SuggestSettings): List<Aim> {
        val net = P.net; val F = P.F; val Z = S["aims"]
        val of = HashMap<String, String>()
        for (z in Z["kinds"].keys) if (z != "_about") for (w in Z["kinds"][z].list) of[w.s] = z
        fun reach(e: Int) = F.ok[e] && P.d0[net.linkA[e]] < NetSearch.INF
        // a grid (~90 m squares) of the reachable ways' points near the point aims, to find the ways near one
        // (cell keys in maps without an object per key: the hot cells are numbered, the grid's sets listed by number)
        val hot = LongIntMap(1 shl 10)
        for (th in F.things) {
            if (th.kind != "aim" && th.kind != "sight") continue
            val c = cellKey(th.dense[0], th.dense[1])
            for (dx in -2..2) for (dy in -2..2) { val k = shift(c, dx, dy); if (hot[k] < 0) hot.put(k, hot.size) }
        }
        val gridSets = arrayOfNulls<PyIntSet>(hot.size)
        val lakeEdges = LinkedHashMap<Int, ArrayList<Int>>()
        for (e in 0 until net.linkCount) {
            if (!reach(e)) continue
            for (i in F.named[e]) if (F.things[i].kind == "lake") lakeEdges.getOrPut(i) { ArrayList() }.add(e)
            val a = net.linkA[e]; val b = net.linkB[e]
            if (F.ln[e] < 150.0 && hot[cellKey(net.lon(a), net.lat(a))] < 0 && hot[cellKey(net.lon(b), net.lat(b))] < 0) continue
            val g = Facts.geomOf(net, e)
            for (j in 0 until g.size / 2 - 1) {
                val p0 = g[2 * j]; val p1 = g[2 * j + 1]; val q0 = g[2 * j + 2]; val q1 = g[2 * j + 3]
                val n = maxOf(1, (SuggestGeo.dist(p0, p1, q0, q1) / 50.0).toInt())
                for (k in 0..n) {
                    val c = cellKey(p0 + (q0 - p0) * k / n, p1 + (q1 - p1) * k / n)
                    val h = hot[c]
                    if (h >= 0) (gridSets[h] ?: PyIntSet().also { gridSets[h] = it }).add(e)
                }
            }
        }

        /** The ways within [r] metres of the point, in the POC's order, with their distance. */
        fun near(lon: Double, lat: Double, r: Double): LinkedHashMap<Int, Double> {
            val c = cellKey(lon, lat)
            val union = PyIntSet()
            for (dx in -1..1) for (dy in -1..1) { val h = hot[shift(c, dx, dy)]; if (h >= 0) gridSets[h]?.forEach { union.add(it) } }
            val out = LinkedHashMap<Int, Double>()
            for (e in union.toIntArray()) {
                val g = Facts.geomOf(net, e)
                var d = Double.MAX_VALUE
                for (j in 0 until g.size / 2 - 1) {
                    val a0 = g[2 * j]; val a1 = g[2 * j + 1]; val b0 = g[2 * j + 2]; val b1 = g[2 * j + 3]
                    val n = maxOf(1, (SuggestGeo.dist(a0, a1, b0, b1) / 20.0).toInt())
                    for (k in 0 until n) d = minOf(d, SuggestGeo.dist(lon, lat, a0 + (b0 - a0) * k / n, a1 + (b1 - a1) * k / n))
                }
                d = minOf(d, SuggestGeo.dist(lon, lat, g[g.size - 2], g[g.size - 1]))
                if (d <= r) out[e] = d
            }
            return out
        }

        val reachM = Z["reach_m"].d; val peakReach = Z["peak_reach_m"].d; val peakBelow = Z["peak_below"].d
        val shoreMin = Z["shore_m"].d
        val out = ArrayList<Aim>()
        for ((i, th) in F.things.withIndex()) {
            val what = when (th.kind) {
                "aim" -> th.what
                "sight" -> SIGHT_WHAT[th.what]
                "lake" -> "lake"
                else -> null
            }
            if (what == null || what !in of) continue
            if (th.kind == "sight" && th.name == th.what && what != "viewpoint") continue  // an unnamed castle is no aim
            val ziel = of.getValue(what)
            val worth = Z["worth"][what].d
            if (what == "lake") {
                val es = lakeEdges[i] ?: emptyList<Int>()
                var m = 0.0
                for (e in es) m += F.ln[e]
                if (m < shoreMin) continue
                val nodes = PyIntSet()
                for (e in es) for (x in intArrayOf(net.linkA[e], net.linkB[e])) if (P.d0[x] < NetSearch.INF) nodes.add(x)
                val ns = nodes.toIntArray()
                var entry = ns[0]
                for (x in ns) if (P.len0[x] < P.len0[entry]) entry = x
                val eLon = net.lon(entry); val eLat = net.lat(entry)
                var far = ns[0]
                var fd = -1.0
                for (x in ns) { val d = SuggestGeo.dist(net.lon(x), net.lat(x), eLon, eLat); if (d > fd) { fd = d; far = x } }
                if (P.d0[far] < NetSearch.INF && far != P.start) {
                    out.add(Aim(ziel, what, th.name, th.ele, worth, PyIntSet.of(es), far, eLon, eLat, m, th.dense))
                }
            } else {
                val lon = th.dense[0]; val lat = th.dense[1]
                val peak = what == "peak"
                var hits = near(lon, lat, if (peak) peakReach else reachM)
                if (peak && th.ele != 0) {
                    val low = th.ele - peakBelow
                    val keep = LinkedHashMap<Int, Double>()
                    for ((e, d) in hits) if (maxOf(Place.height(net, net.linkA[e]), Place.height(net, net.linkB[e])) >= low) keep[e] = d
                    hits = keep
                }
                if (hits.isEmpty()) continue
                var e = -1
                var ed = 0.0
                for ((x, d) in hits) if (e < 0 || d < ed) { e = x; ed = d }
                val a = net.linkA[e]; val b = net.linkB[e]
                val node = if (P.d0[b] > P.d0[a]) b else a
                if (P.d0[node] < NetSearch.INF && node != P.start) {
                    out.add(Aim(ziel, what, th.name, th.ele, worth, PyIntSet.of(hits.keys), node, lon, lat, 0.0, th.dense))
                }
            }
        }
        // one aim per kind and name (a peak in two map areas, two points of one hut): the nearest
        val best = LinkedHashMap<Pair<String, String>, Aim>()
        for (a in out) {
            val k = a.what to a.name
            val b = best[k]
            if (b == null || P.d0[a.node] < P.d0[b.node]) best[k] = a
        }
        return best.values.toList()
    }

    /**
     * aims.py `reached`: what a loop really reaches beyond the way out, in walking order. A lake counts from `shore_m`
     * along its shore and `lake_min` of its shore (one side of a small lake) or `lake_min_m` of a big one.
     */
    fun reached(P: Place, S: SuggestSettings, edges: IntArray, ziel: List<String>): List<Reached> {
        val F = P.F; val Z = S["aims"]; val net = P.net
        val first = HashMap<Int, Double>()
        var acc = 0.0
        for (e in edges) {
            if (P.core(e) && e !in first) first[e] = acc  // an aim on the unavoidable way out is no aim of this loop
            acc += F.ln[e]
        }
        val total = if (acc == 0.0) 1.0 else acc
        class Got(val a: Aim, val at: Double, val round: Double)
        val out = ArrayList<Got>()
        var grid: HashMap<Long, DoubleList>? = null
        val near = Z["shore_near"].d; val shoreMin = Z["shore_m"].d; val lakeMin = Z["lake_min"].d; val lakeMinM = Z["lake_min_m"].d
        val KG = 0.0008
        fun key(lon: Double, lat: Double, dx: Int = 0, dy: Int = 0): Long =
            (((lon / KG).toInt() + dx).toLong() shl 32) or (((lat / KG * 1.5).toInt() + dy).toLong() and 0xffffffffL)
        for (a in P.aims) {
            var hit = Double.MAX_VALUE
            var any = false
            for (e in a.edges.toIntArray()) { val f = first[e] ?: continue; any = true; hit = minOf(hit, f) }
            if (!any) continue
            if (a.what == "lake") {
                var along = 0.0
                for (e in a.edges.toIntArray()) if (e in first) along += F.ln[e]
                if (along < shoreMin) continue
                val g0 = grid ?: HashMap<Long, DoubleList>().also { g ->  // the loop's points, for "how much of the shore"
                    for (e in first.keys) {
                        val pts = Facts.geomOf(net, e)
                        for (k in 0 until pts.size / 2) g.getOrPut(key(pts[2 * k], pts[2 * k + 1])) { DoubleList(8) }.add(pts[2 * k], pts[2 * k + 1])
                    }
                    grid = g
                }
                var close = 0
                val n = a.dense.size / 2
                for (k in 0 until n) {
                    val x = a.dense[2 * k]; val y = a.dense[2 * k + 1]
                    var ok = false
                    loop@ for (dx in -1..1) for (dy in -1..1) {
                        val l = g0[key(x, y, dx, dy)] ?: continue
                        for (j in 0 until l.size / 2) if (SuggestGeo.dist(x, y, l[2 * j], l[2 * j + 1]) <= near) { ok = true; break@loop }
                    }
                    if (ok) close++
                }
                val share = close / n.toDouble()
                if (share >= lakeMin || 25.0 * close >= lakeMinM) out.add(Got(a, hit / total, share))
            } else out.add(Got(a, hit / total, 0.0))
        }
        out.sortBy { it.at }  // stable, as Python's sorted
        return out.map { g ->
            val w = worth(S, g.a.ziel, g.a.what, g.a.worth, g.at, g.round, ziel)
            Reached(g.a.ziel, g.a.what, g.a.name, g.a.ele, SuggestGeo.pyRound(g.at, 2), SuggestGeo.pyRound(g.round, 2), g.a.worth,
                g.a.ziel in ziel && w > 0.0, w)
        }
    }

    /**
     * aims.py `worth`: what reaching this aim adds to the loop's raw Besonderes – its worth, more when the wish names
     * its Ziel; a place to eat counts full in the middle of the loop and nothing at its ends; a lake along one side
     * half, round (or there along one side, back along the other) full. [at] / [round] unrounded.
     */
    fun worth(S: SuggestSettings, ziel: String, what: String, worth: Double, at: Double, round: Double, chosen: List<String>): Double {
        val Z = S["aims"]
        var w = worth * Z["reached"].d * (if (ziel in chosen) Z["chosen"].d else 1.0)
        if (ziel == "einkehr") w *= maxOf(0.0, 1.0 - 2.0 * Math.abs(at - 0.5))
        if (what == "lake") w *= 0.5 + 0.5 * minOf(1.0, round / Z["round"].d)
        return w
    }
}
