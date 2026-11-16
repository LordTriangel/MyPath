package com.onpath.app.suggest

import kotlin.math.abs
import kotlin.math.cos

/**
 * Building loops – one pool, one assembly routine (the POC's `generate.py`). Every loop is a list of turning points
 * given to [LoopAssembly]: start → point → … → start by the cheapest ways, ways already in the loop dearer. The
 * generators differ only in how they choose the points: turning points per direction (alone, in pairs, high enough for
 * a wished climb), chains through the stand-out areas, a signed route whose ways close into a ring (walked round
 * exactly), lines followed and left. Not ported (build plan, review 2026-11-16): the guessed order of a signed route
 * with gaps ("Rundweg" by 24 directions) – the suggest files' signed loops ([Official]) replace it.
 */
class Generate(private val P: Place, private val S: SuggestSettings) {
    private val net = P.net
    private val F = P.F
    private val ch = P.ch
    private val fam = ch.family
    private val Q = S["search"]
    private val form = S["form"]
    private val asm = LoopAssembly(P.search, P.start, P.cost, P.tree, ch.freeStemM, Q["used"].d, Q["beside_used"].d)

    /** generate.py `assemble`: the loop's links in walking order, or null. */
    fun assemble(points: IntArray, costs: Map<Int, DoubleArray>? = null): IntArray? = asm.assemble(points, costs)

    /** generate.py `measure`: the plain measures of a loop from [start] (default the wish's start); null if not closed. */
    fun measure(edges: IntArray, start: Int = P.start): Candidate? = measure(P, edges, start)

    /** generate.py `pool`: all loops worth judging; loops that fit but climb more than wished go to [Place.steep]. */
    fun pool(): List<Candidate> {
        val hLo = ch.hours[0]; val hHi = ch.hours[1]
        val sLon = net.lon(P.start); val sLat = net.lat(P.start); val sEle = Place.height(net, P.start)
        val hMid = 0.5 * (hLo + hHi)
        val out = ArrayList<Candidate>()
        val seen = HashSet<List<Int>>()
        P.steep.clear()
        val roadMax = fam["road_walk_max_m"].d
        val repair = Q["road_repair"].d
        fun roadside(e: Int) = F.traffic[e] >= 0.45 && !F.built[e]
        fun calm(cost: DoubleArray) = DoubleArray(cost.size) { if (roadside(it)) cost[it] * repair else cost[it] }
        val calmCost = if (roadMax != 0.0) calm(P.cost) else null
        val shortZero = form["short_zero"].d; val longZero = form["long_zero"].d; val maxCross = form["max_cross"].d

        fun add(points: IntArray, origin: String, aim: List<String> = emptyList(), costs: Map<Int, DoubleArray>? = null): Candidate? {
            var edges = assemble(points, costs)
            if (edges != null && calmCost != null && Score.roadRun(F, edges) > roadMax) {
                val c = HashMap<Int, DoubleArray>()
                for (i in 0..points.size) c[i] = calmCost
                if (costs != null) for ((i, cost) in costs) c[i] = calm(cost)
                edges = assemble(points, c) ?: edges
            }
            val key = edges?.let { e -> e.distinct().sorted() }
            val m = if (edges != null && key !in seen) measure(edges) else null
            if (m != null) {
                seen.add(key!!)
                if (m.cross <= maxCross && (1 - shortZero) * hLo <= m.hours && m.hours <= (1 + longZero) * hHi) {
                    m.origin = origin; m.aim = aim
                    if (m.up <= ch.up[1]) out.add(m) else P.steep.add(m)
                }
            }
            return m
        }

        val reached = (0 until net.nodeCount).filter { P.len0[it] > 0 && P.len0[it] < ch.maxM && P.d0[it] < NetSearch.INF }
        val bear = HashMap<Int, Double>(reached.size * 2)
        for (v in reached) bear[v] = SuggestGeo.bearing(sLon, sLat, net.lon(v), net.lat(v))
        fun rise(v: Int): Double { val e = Place.height(net, v); return if (minOf(e, sEle) > -1000) abs(e - sEle) else 0.0 }
        fun hours(metres: Double, vararg via: Int): Double { var r = rise(via[0]); for (v in via) r = maxOf(r, rise(v)); return ch.time(metres, r) }
        fun thereAndBack(v: Int) = hours(2.2 * P.len0[v], v)
        val sectors = fam["sectors"].d

        fun anchors(fits: (Int) -> Boolean): IntArray {
            val best = LinkedHashMap<Int, Pair<Double, Int>>()
            for (v in reached) {
                if (!fits(v)) continue
                val k = (bear.getValue(v) * sectors / 360.0).toInt()
                val r = P.d0[v] / P.len0[v]
                val b = best[k]
                if (b == null || r < b.first) best[k] = r to v
            }
            return best.values.map { it.second }.toIntArray()
        }

        // --- turning points: one per direction (at the short and the long end), then pairs a quarter turn apart
        for (h in if (hHi < 1.25 * hLo) doubleArrayOf(hMid) else doubleArrayOf(hLo, hHi)) {
            for (v in anchors { val t = thereAndBack(it); 0.9 * h <= t && t <= 1.1 * h }) add(intArrayOf(v), "Wendepunkt")
        }
        if (ch.up[0] >= 100.0) {
            for (v in anchors {
                val dz = Place.height(net, it) - sEle
                val t = thereAndBack(it)
                0.8 * ch.up[0] <= dz && dz <= ch.up[1] && hLo <= t && t <= hHi
            }) add(intArrayOf(v), "Anstieg")
        }
        val A = anchors { val t = thereAndBack(it); 0.6 * hLo <= t && t <= 0.8 * hHi }
        class Pr(val fit: Double, val a: Int, val b: Int)
        val pairs = ArrayList<Pr>()
        for (a in A) {
            fun est(b: Int) = hours(P.len0[a] + 1.3 * SuggestGeo.dist(net.lon(a), net.lat(a), net.lon(b), net.lat(b)) + P.len0[b], a, b)
            fun fit(b: Int): Double { val e = est(b); return maxOf(0.0, hLo - e, e - hHi) }
            val side = A.filter { val d = SuggestGeo.pyMod(bear.getValue(it) - bear.getValue(a), 360.0); 40.0 <= d && d <= 110.0 }
            if (side.isNotEmpty()) {
                var bb = side[0]
                for (b in side) if (fit(b) < fit(bb)) bb = b
                pairs.add(Pr(fit(bb), a, bb))
            }
        }
        pairs.sortWith(compareBy<Pr>({ it.fit }, { it.a }, { it.b }))
        for (p in pairs.take(fam["pairs"].i)) add(intArrayOf(p.a, p.b), "Wendepunkte")

        // --- chains through the stand-out areas, in order around the start, each through its heart
        val areas = P.areas.sortedBy { SuggestGeo.bearing(sLon, sLat, it.lon, it.lat) }
        class Ch(val key: Double, val i: Int, val k: Int, val pts: IntArray, val names: List<String>)
        val chains = ArrayList<Ch>()
        for (i in areas.indices) {
            for (k in 1..minOf(4, areas.size)) {
                val seq = List(k) { areas[(i + it) % areas.size] }
                val pts: IntArray
                val est: Double
                if (k == 1) {
                    var turn = seq[0].nodes[0]
                    for (v in seq[0].nodes) if (abs(thereAndBack(v) - hMid) < abs(thereAndBack(turn) - hMid)) turn = v
                    pts = if (P.len0[seq[0].heart] < P.len0[turn] - 200.0) intArrayOf(seq[0].heart, turn) else intArrayOf(turn)
                    est = thereAndBack(turn)
                } else {
                    pts = IntArray(k) { seq[it].heart }
                    var s = 0.0
                    for (j in 0 until k - 1) s += SuggestGeo.dist(net.lon(pts[j]), net.lat(pts[j]), net.lon(pts[j + 1]), net.lat(pts[j + 1]))
                    est = hours(P.len0[pts[0]] + P.len0[pts[k - 1]] + 1.35 * s, *pts)
                }
                if (0.7 * hLo <= est && est <= 1.25 * hHi) {
                    var key = 0.0
                    for (a in seq) key += a.m * a.good
                    chains.add(Ch(-key, i, k, pts, seq.map { it.name }))
                }
            }
        }
        chains.sortWith(compareBy<Ch>({ it.key }, { it.i }, { it.k }))
        for (c in chains.take(fam["chains"].i)) add(c.pts, "Kette", c.names)

        // --- a signed route whose ways close into a ring: walked round exactly
        for ((i, th) in F.things.withIndex()) {
            if (th.kind != "route" || th.points < 40) continue
            var mx = 0.0; var my = 0.0
            for (k in 0 until th.points) { mx += th.dense[2 * k]; my += th.dense[2 * k + 1] }
            mx /= th.points; my /= th.points
            val bins = HashSet<Int>()
            for (k in 0 until th.points) bins.add((SuggestGeo.bearing(mx, my, th.dense[2 * k], th.dense[2 * k + 1]) / 15.0).toInt())
            if (bins.size < 18) continue
            val mine = (0 until net.linkCount).filter { e -> P.d0[net.linkA[e]] < NetSearch.INF && F.named[e].contains(i) }
            val on = runsAlong(th.dense, mine)
            var whole = 0.0
            for (e in on.toIntArray()) whole += F.ln[e]
            var deg = LinkedHashMap<Int, Int>()
            while (!on.isEmpty()) {
                deg = LinkedHashMap()
                val es = on.toIntArray()
                for (e in es) for (x in intArrayOf(net.linkA[e], net.linkB[e])) deg[x] = (deg[x] ?: 0) + 1
                val stubs = PyIntSet()
                for (e in es) if (deg[net.linkA[e]] == 1 || deg[net.linkB[e]] == 1) stubs.add(e)
                if (stubs.isEmpty()) break
                for (e in stubs.toIntArray()) on.remove(e)
            }
            if (deg.isEmpty()) continue
            var entry = deg.keys.first()
            for (x in deg.keys) if (P.len0[x] < P.len0[entry]) entry = x
            var m = 0.0
            for (e in on.toIntArray()) m += F.ln[e]
            val exact = DoubleArray(net.linkCount) { if (it in on) F.ln[it] else -1.0 }
            P.search.full(entry, exact)
            val way = P.search.metres.copyOf()
            val prev = P.search.prevNode.copyOf()
            // (the POC also asks "every ring node reached", on the metres – which are never infinite: always true)
            if (on.isEmpty() || m < 0.85 * whole) continue  // a ring with gaps: its guessed order is not ported
            val t = ch.time(m + 2.0 * P.len0[entry], 0.0)
            if (!(0.6 * hLo <= t && t <= 1.3 * hHi)) continue
            fun third(vs: List<Int>): Int { var b = vs[0]; for (v in vs) if (abs(way[v] - m / 3.0) < abs(way[b] - m / 3.0)) b = v; return b }
            val a = third(deg.keys.toList())
            val firstThird = HashSet<Int>()
            var v = a
            while (v >= 0) { firstThird.add(v); v = prev[v] }
            val rest = deg.keys.filter { it !in firstThird }
            if (rest.isNotEmpty()) add(intArrayOf(entry, a, third(rest)), "Rundweg", listOf(th.name), mapOf(1 to exact, 2 to exact, 3 to exact))
        }

        // --- lines: join at the nearest point, follow it (both ways), come home another way
        val lineF = Q["line"].d
        for (ln in P.lines.take(fam["lines"].i)) {
            val entry = ln.near
            if (P.len0[entry] > 0.35 * ch.targetM) continue
            val along = P.cost.copyOf()
            for (e in ln.edges.toIntArray()) along[e] *= lineF
            P.search.bounded(entry, 0.75 * ch.maxM, along)
            val d = P.search.dist.copyOf()
            val alongM = P.search.metres.copyOf()
            val on = ln.nodes.filter { d[it] < NetSearch.INF && it != entry && bear.containsKey(it) }
            if (on.isEmpty()) continue
            val eLon = net.lon(entry); val eLat = net.lat(entry)
            fun turn(u: Int, w: Int) = abs(SuggestGeo.pyMod(SuggestGeo.bearing(eLon, eLat, net.lon(u), net.lat(u)) - SuggestGeo.bearing(eLon, eLat, net.lon(w), net.lat(w)) + 180.0, 360.0) - 180.0)
            fun half(w: Int) = abs(hours(2.0 * (P.len0[entry] + alongM[w]), w) - hMid)
            fun minBy(xs: List<Int>, f: (Int) -> Double): Int { var b = xs[0]; var bv = f(b); for (x in xs) { val fx = f(x); if (fx < bv) { bv = fx; b = x } }; return b }
            val first = minBy(on, ::half)
            val other = on.filter { turn(it, first) >= 90.0 }
            val guesses = if (other.isNotEmpty()) listOf(first, minBy(other, ::half)) else listOf(first)
            for (guess in guesses) {
                val same = on.filter { turn(it, guess) < 60.0 }
                for (share in doubleArrayOf(1.0, 1.15, 0.85)) {
                    add(intArrayOf(entry, minBy(same) { abs(alongM[it] - share * alongM[guess]) }), "Linie", listOf(ln.name), mapOf(1 to along))
                }
            }
        }
        return out
    }

    /** net.py `runs_along`: the links that run along a line given as dense points (most of the link within [near] m). */
    fun runsAlong(pts: DoubleArray, edges: List<Int>, near: Double = 25.0): PyIntSet = runsAlong(net, pts, edges, near)

    companion object {
        fun measure(P: Place, edges: IntArray, start: Int): Candidate? {
            val net = P.net; val F = P.F; val ch = P.ch
            val nodes = IntArray(edges.size + 1)
            val at = DoubleArray(edges.size + 1)
            nodes[0] = start
            for (i in edges.indices) {
                nodes[i + 1] = net.other(edges[i], nodes[i])
                at[i + 1] = at[i] + F.ln[edges[i]]
            }
            if (nodes[edges.size] != start) return null
            val up = climb(net, nodes)
            val free = ch.freeStemM
            val total = at[edges.size]
            var stem = 0
            while (stem < edges.size / 2 && edges[stem] == edges[edges.size - 1 - stem]) stem++
            val count = LinkedHashMap<Int, Int>()
            for (e in edges) count[e] = (count[e] ?: 0) + 1
            var doubled = 0.0
            for ((e, c) in count) if (c > 1) doubled += F.ln[e] * (c - 1)
            val seen = HashMap<Int, Int>()
            var cross = 0
            var inRun = false
            var last = -1e9
            for (i in stem until nodes.size - 1 - stem) {
                val j = seen[nodes[i]]
                if (j != null && i - j > 1 && !(at[j] <= free && total - at[i] <= free)) {
                    if (!inRun && at[i] - last > 150.0) cross++
                    inRun = true
                    last = at[i]
                } else inRun = false
                seen[nodes[i]] = i
            }
            return Candidate(edges, nodes, total, up, ch.time(total, up), at[stem], maxOf(0.0, doubled - minOf(at[stem], free)), cross)
        }

        /** net.py `climb` on the POC's heights (0.1 m): metres up along a walk of nodes (rises under 6 m are noise). */
        fun climb(net: WayNet, nodes: IntArray): Double {
            var up = 0.0
            var ref = Double.NaN
            for (x in nodes) {
                val ele = Place.height(net, x)
                if (ele < -1000) continue
                if (ref.isNaN() || ele < ref - 6.0) ref = ele
                else if (ele > ref + 6.0) { up += ele - ref; ref = ele }
            }
            return up
        }

        fun runsAlong(net: WayNet, pts: DoubleArray, edges: List<Int>, near: Double = 25.0): PyIntSet {
            val out = PyIntSet()
            if (pts.isEmpty()) return out
            val ky = near / 111320.0
            val kx = ky / cos(Math.toRadians(pts[1]))
            val grid = HashMap<Long, DoubleList>()
            fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
            var w = Double.MAX_VALUE; var s = Double.MAX_VALUE; var e = -Double.MAX_VALUE; var n = -Double.MAX_VALUE
            for (i in 0 until pts.size / 2) {
                val x = pts[2 * i]; val y = pts[2 * i + 1]
                grid.getOrPut(key((x / kx).toInt(), (y / ky).toInt())) { DoubleList(8) }.add(x, y)
                w = minOf(w, x); e = maxOf(e, x); s = minOf(s, y); n = maxOf(n, y)
            }
            // a link entirely beyond 2 × near of the line's box cannot run along it (saves sampling the whole network)
            w -= 2 * kx; e += 2 * kx; s -= 2 * ky; n += 2 * ky
            fun close(x: Double, y: Double): Boolean {
                val cx = (x / kx).toInt(); val cy = (y / ky).toInt()
                for (dx in -1..1) for (dy in -1..1) {
                    val l = grid[key(cx + dx, cy + dy)] ?: continue
                    for (k in 0 until l.size / 2) if (SuggestGeo.dist(x, y, l[2 * k], l[2 * k + 1]) <= near) return true
                }
                return false
            }
            for (ei in edges) {
                val g = Facts.geomOf(net, ei)
                var gw = g[0]; var ge = g[0]; var gs = g[1]; var gn = g[1]
                for (k in 1 until g.size / 2) {
                    gw = minOf(gw, g[2 * k]); ge = maxOf(ge, g[2 * k]); gs = minOf(gs, g[2 * k + 1]); gn = maxOf(gn, g[2 * k + 1])
                }
                if (ge < w || gw > e || gn < s || gs > n) continue
                var samples = 0
                var hits = 0
                for (k in 0 until g.size / 2 - 1) {
                    val a0 = g[2 * k]; val a1 = g[2 * k + 1]; val b0 = g[2 * k + 2]; val b1 = g[2 * k + 3]
                    val m = maxOf(1, (SuggestGeo.dist(a0, a1, b0, b1) / 15.0).toInt())
                    for (j in 0 until m) {
                        samples++
                        if (close(a0 + (b0 - a0) * j / m, a1 + (b1 - a1) * j / m)) hits++
                    }
                }
                samples++
                if (close(g[g.size - 2], g[g.size - 1])) hits++
                if (hits >= 0.7 * samples) out.add(ei)
            }
            return out
        }
    }
}
