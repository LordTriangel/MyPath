package com.onpath.app.suggest


/**
 * Level 2 – what the place offers (the POC's `place.py`, frozen 2026-11-16): per way the absolute value on Natur, Ruhe,
 * Weg, Besonderes for this character; the place's yardstick (low / high quantile – the search's measure – and `top`,
 * what a loop's mean counts full at); "how good is this way here" and the search cost; one search from the start;
 * what stands out (areas, lines); the unavoidable way out ([access]); what a loop can lead to ([aims]). Since the batch
 * of 2026-11-17: a natural shore counts as nature (`water_natur`), on foot a narrow path is worth more on Weg than a
 * wide track of the same ground (quality trails).
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
    /** extra Besonderes per area or thing name ([value]) */
    private val tips = tips
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
    /** summits, lakes, viewpoints, huts, castles … in reach, each with its turning point (aims.py `find`) */
    val aims: List<Aims.Aim>
    /** the names made up for unnamed areas and lines ("Wald bei Hilden") – a card may be called after one */
    val spotNames = HashSet<String>()
    /** union-find over nodes for [groups]: −1 = not in the current call (reset after each); given back after init */
    private var parent: IntArray? = null

    /** An area or a line as the generators and the cards need it (place.py `_thing`). */
    class Spot(val kind: String, val name: String, val rank: Double, val edges: PyIntSet, val nodes: IntArray,
               val m: Double, val lon: Double, val lat: Double, val near: Int, val d: Double, val heart: Int, val good: Double)

    // [value]'s numbers: natur, ruhe, weg, besonderes per way are worked out when asked, not kept (4 × 8 bytes per way)
    private val wWater: Double
    private val wOther: Double
    private val rimNatur: Double
    private val pRim: Double
    private val pSight: Double
    private val waterNatur: Double
    private val width: DoubleArray

    init {
        val P = S["place"]; val Q = S["search"]
        val ok = Generate.linksWhere(n) { F.ok[it] }
        if (ch.water) { wWater = 1.0; wOther = 0.4 } else { wWater = 0.6; wOther = 1.0 }
        rimNatur = P["rim_natur"].d; pRim = P["rim"].d; pSight = P["sight"].d; waterNatur = P["water_natur"].d
        val W = S["quality"]["width"]
        width = doubleArrayOf(W["path"].d, W["track"].d, W["rough_track"].d, W["wide"].d)
        val things = F.things
        for (a in 0 until 4) {
            low[a] = quantile(ok, a, P["low"].d)
            high[a] = maxOf(quantile(ok, a, P["high"].d), low[a] + P["min_span"].d)
        }
        val ax = DoubleArray(4) { ch.axis(AXES[it]) }
        val wsum = ax[0] + ax[1] + ax[2] + ax[3]
        val perUp = if (climb) Q["climb"].d * maxOf(0.0, 1.0 - ch.up[1] / ch.usualUp) * ch.pace * 1000.0 / ch.ascentMPerH else 0.0
        val taste = Q["taste"].d; val road = Q["road"].d; val prof = Q["profile"].d
        for (e in ok) {
            var s = 0.0
            for (a in 0 until 4) s += ax[a] * rel(a, value(a, e))
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
        val reach = ok.filterTo(d0)
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
            top[a] = maxOf(quantile(core, a, P["top"][AXES[a]].d) + (if (a == BESONDERES) ring else 0.0), low[a] + P["min_span"].d)
        }
        // --- what a loop can lead to: summits, lakes, viewpoints, huts, castles … (Aims)
        parent = null
        @Suppress("LeakingThis")
        aims = Aims.find(this, S)
    }

    /** The absolute value of way [e] on axis [a] (natur, ruhe, weg, besonderes), 0..1; 0 for a way not allowed. */
    fun value(a: Int, e: Int): Double {
        if (!F.ok[e]) return 0.0
        return when (a) {
            // an open edge with a view and a natural shore (quality trails: "Ränder – Waldrand, Ufer") are nature too
            NATUR -> maxOf((1 - ch.depth) * F.green[e] + ch.depth * F.depth[e], rimNatur * F.rim[e],
                waterNatur * F.water[e] * (if (F.built[e]) 0 else 1))
            RUHE -> 1.0 - maxOf(F.traffic[e], 0.6 * (if (F.built[e]) 1 else 0), 0.5 * F.noise[e])
            // on foot a narrow path is worth more than a wide track of the same ground (quality trails)
            WEG -> if (F.rail[e] && ch.bike) 1.0 else (1.0 - ch.path) * ch.ground[F.ground[e]] + ch.path * width[F.width[e]]
            else -> {
                var tip = tips[F.areas[F.area[e]]] ?: 0.0
                for (i in F.named[e]) tip = maxOf(tip, tips[F.things[i].name] ?: 0.0)
                val other = maxOf(maxOf(F.signed[e] * ch.signed, 0.9 * (if (F.rail[e] && ch.bike) 1 else 0), 0.6 * (if (F.area[e] > 0) 1 else 0)),
                    maxOf(tip, pRim * F.rim[e], pSight * (if (F.sight[e]) 1 else 0)))
                minOf(1.0, maxOf(wWater * F.water[e], wOther * other)) * (1.0 - F.traffic[e])
            }
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
    fun groups(edges: List<Int>): List<IntArray> = groups(edges.toIntArray())

    fun groups(edges: IntArray): List<IntArray> {
        val parent = parent ?: IntArray(net.nodeCount) { -1 }.also { parent = it }
        fun find(x0: Int): Int {
            var x = x0
            while (true) {
                var p = parent[x]
                if (p < 0) { parent[x] = x; p = x }
                if (p == x) return x
                var pp = parent[p]
                if (pp < 0) { parent[p] = p; pp = p }
                parent[x] = pp
                x = pp
            }
        }
        try {
            for (e in edges) {
                val ra = find(net.linkA[e])
                parent[ra] = find(net.linkB[e])
            }
            // groups in the order of their first edge (the root's number does not matter, only who is together)
            val groupOf = LinkedHashMap<Int, IntList>()
            for (e in edges) groupOf.getOrPut(find(net.linkA[e])) { IntList() }.add(e)
            return groupOf.values.map { it.toArray() }
        } finally {
            for (e in edges) { parent[net.linkA[e]] = -1; parent[net.linkB[e]] = -1 }
        }
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
                    if (share { F.water[it] > 0 } >= 0.3) SuggestText.shore() else if (share { F.green[it] >= 0.5 } >= 0.5) SuggestText.forest()
                    else SuggestText.fields()
                }
                name = SuggestText.spot(what, land.placeNear(cx, cy), SuggestText.direction(net.lon(start), net.lat(start), cx, cy))
                spotNames.add(name)
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
            Triple("rail", reach.filter { F.rail[it] }, 0.9 * bike) to SuggestText.rail(),
            Triple("bank", reach.filter { F.water[it] == 1.0 && F.traffic[it] < 0.3 }, if (ch.water) 1.0 else 0.6) to SuggestText.shore(),
            Triple("brook", reach.filter { F.water[it] == 0.5 && F.green[it] >= 0.2 && F.traffic[it] < 0.3 }, if (ch.water) 0.9 else 0.4) to SuggestText.brook(),
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
        fun quantileOf(values: DoubleArray, metres: DoubleArray, q: Double): Double =
            quantileOf(values.size, { values[it] }, { metres[it] }, q)

        /** [quantileOf] of n entries given by index – only the sampled ones are read (no arrays of all n). */
        inline fun quantileOf(n: Int, valueAt: (Int) -> Double, metresAt: (Int) -> Double, q: Double): Double {
            if (n == 0) return 0.0
            val step = maxOf(1, n / 30000)
            val m = (n + step - 1) / step
            val values = DoubleArray(m) { valueAt(it * step) }
            val metres = DoubleArray(m) { metresAt(it * step) }
            val idx = IntArray(m) { it }
            stableSort(idx) { a, b -> val c = values[a].compareTo(values[b]); if (c != 0) c else metres[a].compareTo(metres[b]) }
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

        /** A stable merge sort of ints (the order Kotlin's sortedWith gives – a stable sort's result is unique –
         *  without a boxed list). */
        inline fun stableSort(a: IntArray, cmp: (Int, Int) -> Int) {
            var src = a
            var dst = IntArray(a.size)
            var width = 1
            while (width < a.size) {
                var lo = 0
                while (lo < a.size) {
                    val mid = minOf(lo + width, a.size); val hi = minOf(lo + 2 * width, a.size)
                    var i = lo; var j = mid; var k = lo
                    while (i < mid && j < hi) dst[k++] = if (cmp(src[j], src[i]) < 0) src[j++] else src[i++]
                    while (i < mid) dst[k++] = src[i++]
                    while (j < hi) dst[k++] = src[j++]
                    lo = hi
                }
                val t = src; src = dst; dst = t
                width *= 2
            }
            if (src !== a) src.copyInto(a)
        }
    }

    private fun IntArray.filterTo(d: DoubleArray): IntArray {
        var k = 0
        for (e in this) if (d[net.linkA[e]] < NetSearch.INF) k++
        val out = IntArray(k)
        k = 0
        for (e in this) if (d[net.linkA[e]] < NetSearch.INF) out[k++] = e
        return out
    }

    private fun quantile(edges: IntArray, value: DoubleArray, q: Double): Double =
        quantileOf(edges.size, { value[edges[it]] }, { F.ln[edges[it]] }, q)

    /** [quantile] of axis [a]'s values ([value]). */
    private fun quantile(edges: IntArray, a: Int, q: Double): Double =
        quantileOf(edges.size, { value(a, edges[it]) }, { F.ln[edges[it]] }, q)
}
