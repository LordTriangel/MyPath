package com.onpath.app.suggest


/**
 * Level 2 – what the place offers (the POC's `place.py`, frozen 2026-11-16): per way the absolute value on Natur, Ruhe,
 * Weg, Besonderes for this character; the place's yardstick (low / high quantile – the search's measure – and `top`,
 * what a loop's mean counts full at); "how good is this way here" and the search cost; one search from the start;
 * what stands out (areas, lines); the unavoidable way out ([access]).
 */
class Place(
    val net: WayNet,
    val F: Facts,
    val ch: Character,
    S: SuggestSettings,
    val start: Int,
    val land: Land,
    val search: NetSearch,
    tips: Map<String, Double> = emptyMap(),
    climb: Boolean = false,
) {
    val target = ch.targetM
    val n = net.linkCount
    /** natur, ruhe, weg, besonderes: absolute 0..1 per way */
    val v = Array(4) { DoubleArray(n) }
    val low = DoubleArray(4)
    val high = DoubleArray(4)
    val top = DoubleArray(4)
    val good = DoubleArray(n)
    val cost = DoubleArray(n) { -1.0 }
    val d0: DoubleArray
    val tree: NetSearch.Tree
    val len0: DoubleArray
    val areas: List<Spot>
    val lines: List<Spot>
    val access: Double
    /** loops that fit but climb more than wished (generate.pool) */
    val steep = ArrayList<Candidate>()

    /** An area or a line as the generators and the cards need it (place.py `_thing`). */
    class Spot(val kind: String, val name: String, val rank: Double, val edges: PyIntSet, val nodes: IntArray,
               val m: Double, val lon: Double, val lat: Double, val near: Int, val d: Double, val heart: Int, val good: Double)

    init {
        val P = S["place"]; val Q = S["search"]
        val ok = (0 until n).filter { F.ok[it] }.toIntArray()
        val (wWater, wOther) = if (ch.water) 1.0 to 0.4 else 0.6 to 1.0
        val things = F.things
        val rimNatur = P["rim_natur"].d; val pRim = P["rim"].d; val pSight = P["sight"].d
        for (e in ok) {
            v[NATUR][e] = maxOf((1 - ch.depth) * F.green[e] + ch.depth * F.depth[e], rimNatur * F.rim[e])
            v[RUHE][e] = 1.0 - maxOf(F.traffic[e], 0.6 * (if (F.built[e]) 1 else 0), 0.5 * F.noise[e])
            v[WEG][e] = if (F.rail[e] && ch.bike) 1.0 else ch.ground[F.ground[e]]
            var tip = tips[F.areas[F.area[e]]] ?: 0.0
            for (i in F.named[e]) tip = maxOf(tip, tips[things[i].name] ?: 0.0)
            val other = maxOf(maxOf(F.signed[e] * ch.signed, 0.9 * (if (F.rail[e] && ch.bike) 1 else 0), 0.6 * (if (F.area[e] > 0) 1 else 0)),
                maxOf(tip, pRim * F.rim[e], pSight * (if (F.sight[e]) 1 else 0)))
            v[BESONDERES][e] = minOf(1.0, maxOf(wWater * F.water[e], wOther * other)) * (1.0 - F.traffic[e])
        }
        for (a in 0 until 4) {
            low[a] = quantile(ok, v[a], P["low"].d)
            high[a] = maxOf(quantile(ok, v[a], P["high"].d), low[a] + P["min_span"].d)
        }
        val ax = DoubleArray(4) { ch.axis(AXES[it]) }
        val wsum = ax[0] + ax[1] + ax[2] + ax[3]
        val perUp = if (climb) Q["climb"].d * maxOf(0.0, 1.0 - ch.up[1] / ch.usualUp) * ch.pace * 1000.0 / ch.ascentMPerH else 0.0
        val taste = Q["taste"].d; val road = Q["road"].d; val prof = Q["profile"].d
        for (e in ok) {
            var s = 0.0
            for (a in 0 until 4) s += ax[a] * rel(a, v[a][e])
            val g = s / wsum
            good[e] = g
            cost[e] = F.ln[e] * (1 + taste * (1 - g)) * (1 + road * F.traffic[e]) * (1 + prof * (F.costfactor[e] - 1))
            val ha = height(net, net.linkA[e]); val hb = height(net, net.linkB[e])
            if (perUp != 0.0 && minOf(ha, hb) > -1000) cost[e] += perUp * Math.abs(ha - hb)
        }
        search.full(start, cost)
        tree = search.tree()
        d0 = tree.dist
        len0 = tree.metres
        val reach = ok.filter { d0[net.linkA[it]] < NetSearch.INF }.toIntArray()
        // --- what stands out here: areas and lines
        val bar = quantile(reach, good, 1.0 - P["standout"].d)
        val besideFrom = S["facts"]["beside_from"].d
        val topWays = reach.filter { good[it] >= bar && F.traffic[it] < besideFrom }
        val minArea = maxOf(1000.0, P["min_area"].d * target)
        val byReserve = LinkedHashMap<Int, ArrayList<Int>>()
        for (e in topWays) byReserve.getOrPut(F.area[e]) { ArrayList() }.add(e)
        val found = ArrayList<Spot>()
        for (part in byReserve.values) for (es in groups(part)) if (sumLn(es) >= minArea) found.add(spot(es, "area"))
        areas = found.sortedBy { -it.m * it.good }.take(14)
        lines = lines(reach, things, minArea)
        // --- the unavoidable way out
        val step = maxOf(250.0, 0.03 * target)
        val bands = HashMap<Long, DoubleArray>()
        for (e in reach) {
            val b = bands.getOrPut((len0[net.linkA[e]] / step).toLong()) { DoubleArray(2) }
            b[0] += (if (F.built[e]) 1 else 0) * F.ln[e]; b[1] += F.ln[e]
        }
        val accessBuilt = P["access_built"].d
        val first = bands.keys.sorted().firstOrNull { bands.getValue(it)[0] < accessBuilt * bands.getValue(it)[1] } ?: 0L
        access = minOf(first * step, P["access_max"].d * target)
        // --- the yardstick a loop is judged on (frozen 2026-11-16: the place alone, never the other loops)
        val core = reach.filter { core(it) }.toIntArray().let { if (it.isEmpty()) reach else it }
        val ring = S["form"]["ring"].d
        for (a in 0 until 4) {
            top[a] = maxOf(quantile(core, v[a], P["top"][AXES[a]].d) + (if (a == BESONDERES) ring else 0.0), low[a] + P["min_span"].d)
        }
    }

    /** An absolute value on this place's yardstick: 0 = poor here, 1 = as good as it gets here. */
    fun rel(a: Int, value: Double): Double = ((value - low[a]) / (high[a] - low[a])).coerceIn(0.0, 1.0)

    /** A loop's mean value on an axis: 0 = poor here, 1 = as good as a loop can get here (`top`). */
    fun judge(a: Int, value: Double): Double = ((value - low[a]) / (top[a] - low[a])).coerceIn(0.0, 1.0)

    /** Beyond the unavoidable way out – only this part of a loop is judged and compared. */
    fun core(e: Int): Boolean = minOf(len0[net.linkA[e]], len0[net.linkB[e]]) >= access

    private fun sumLn(es: IntArray): Double { var s = 0.0; for (e in es) s += F.ln[e]; return s }

    /** place.py `groups`: connected groups of [edges], each in the order of [edges], groups by their first edge. */
    fun groups(edges: List<Int>): List<IntArray> {
        val parent = HashMap<Int, Int>()
        fun find(x0: Int): Int {
            var x = x0
            while (true) {
                val p = parent.getOrPut(x) { x }
                if (p == x) return x
                val pp = parent.getOrPut(p) { p }
                parent[x] = pp
                x = pp
            }
        }
        for (e in edges) {
            val ra = find(net.linkA[e])
            parent[ra] = find(net.linkB[e])
        }
        val out = LinkedHashMap<Int, ArrayList<Int>>()
        for (e in edges) out.getOrPut(find(net.linkA[e])) { ArrayList() }.add(e)
        return out.values.map { it.toIntArray() }
    }

    /** place.py `_thing`. */
    fun spot(es: IntArray, kind: String, name0: String = "", rank: Double = 1.0, word: String = ""): Spot {
        val nodeSet = PyIntSet()
        for (e in es) for (x in intArrayOf(net.linkA[e], net.linkB[e])) if (d0[x] < NetSearch.INF) nodeSet.add(x)
        val nodes = nodeSet.toIntArray()
        val m = sumLn(es)
        var sx = 0.0; var sy = 0.0
        for (x in nodes) { sx += net.lon(x); sy += net.lat(x) }
        val cx = sx / nodes.size; val cy = sy / nodes.size
        var near = nodes[0]
        for (x in nodes) if (len0[x] < len0[near]) near = x
        var name = name0
        if (name.isEmpty()) {
            val byArea = LinkedHashMap<Int, Double>()
            for (e in es) if (F.area[e] != 0) byArea[F.area[e]] = (byArea[F.area[e]] ?: 0.0) + F.ln[e]
            var best = 0
            for ((k, l) in byArea) if (best == 0 || l > byArea.getValue(best)) best = k
            if (best != 0 && byArea.getValue(best) >= 0.3 * m) name = F.areas[best]
            else {
                fun share(test: (Int) -> Boolean): Double { var s = 0.0; for (e in es) if (test(e)) s += F.ln[e]; return s / m }
                val what = word.ifEmpty {
                    if (share { F.water[it] > 0 } >= 0.3) "Ufer" else if (share { F.green[it] >= 0.5 } >= 0.5) "Wald" else "Feldflur"
                }
                val town = land.placeNear(cx, cy)
                name = if (town.isNotEmpty()) "$what bei $town"
                else "$what im ${SuggestGeo.direction(net.lon(start), net.lat(start), cx, cy)}"
            }
        }
        var heart = nodes[0]
        var hd = SuggestGeo.dist(net.lon(heart), net.lat(heart), cx, cy)
        for (x in nodes) { val d = SuggestGeo.dist(net.lon(x), net.lat(x), cx, cy); if (d < hd) { hd = d; heart = x } }
        var gs = 0.0
        for (e in es) gs += good[e] * F.ln[e]
        return Spot(kind, name, rank, PyIntSet.of(es), nodes, m, cx, cy, near, len0[near], heart, gs / m)
    }

    /** place.py `_lines`: known routes and lake shores, rail trails, banks of big and small water. */
    private fun lines(reach: IntArray, things: List<Thing>, minM: Double): List<Spot> {
        val out = ArrayList<Spot>()
        val byThing = LinkedHashMap<Int, ArrayList<Int>>()
        for (e in reach) for (i in F.named[e]) byThing.getOrPut(i) { ArrayList() }.add(e)
        for ((i, es) in byThing) {
            val th = things[i]
            if (th.kind != "sight" && sumLn(es.toIntArray()) >= minM) {
                val rank = if (th.kind == "route") th.rank * ch.signed else if (ch.water) 1.0 else 0.6
                var big: IntArray? = null
                for (g in groups(es)) if (big == null || g.size > big.size) big = g
                out.add(spot(big!!, th.kind, th.name, rank))
            }
        }
        val bike = if (ch.bike) 1.0 else 0.0
        val runs = listOf(
            Triple("rail", reach.filter { F.rail[it] }, 0.9 * bike) to "Bahntrasse",
            Triple("bank", reach.filter { F.water[it] == 1.0 && F.traffic[it] < 0.3 }, if (ch.water) 1.0 else 0.6) to "Ufer",
            Triple("brook", reach.filter { F.water[it] == 0.5 && F.green[it] >= 0.2 && F.traffic[it] < 0.3 }, if (ch.water) 0.9 else 0.4) to "Bach",
        )
        val lakeEdges = PyIntSet()
        for (ln in out) if (ln.kind == "lake") for (e in ln.edges.toIntArray()) lakeEdges.add(e)
        for ((t, word) in runs) {
            val (kind, edges, rank) = t
            if (rank == 0.0) continue
            for (es in groups(edges)) {
                var onLake = 0
                for (e in es) if (e in lakeEdges) onLake++
                if (sumLn(es) >= maxOf(minM, 1500.0) && onLake < 0.5 * es.size) out.add(spot(es, kind, rank = rank, word = word))
            }
        }
        return out.filter { it.rank > 0 }.sortedBy { -it.rank * minOf(it.m, 0.5 * target) }
    }

    companion object {
        val AXES = arrayOf("natur", "ruhe", "weg", "besonderes")
        const val NATUR = 0
        const val RUHE = 1
        const val WEG = 2
        const val BESONDERES = 3

        /** A node's height as the POC's network has it (0.1 m; below −1000 = unknown). */
        fun height(net: WayNet, v: Int): Double = Math.round(net.ele(v) * 10.0) / 10.0

        /**
         * place.py `quantile`: length-weighted quantile of (value, metres) over [edges] – every k-th of them (a sample
         * of at most ~30 000), sorted by value then metres.
         */
        fun quantileOf(values: DoubleArray, metres: DoubleArray, q: Double): Double {
            val n = values.size
            if (n == 0) return 0.0
            val step = maxOf(1, n / 30000)
            val idx = (0 until n step step).sortedWith { a, b ->
                val c = values[a].compareTo(values[b]); if (c != 0) c else metres[a].compareTo(metres[b])
            }
            var total = 0.0
            for (i in idx) total += metres[i]
            val want = q * total
            var acc = 0.0
            for (i in idx) {
                acc += metres[i]
                if (acc >= want) return values[i]
            }
            return values[idx.last()]
        }
    }

    private fun quantile(edges: IntArray, value: DoubleArray, q: Double): Double =
        quantileOf(DoubleArray(edges.size) { value[edges[it]] }, DoubleArray(edges.size) { F.ln[edges[it]] }, q)
}
