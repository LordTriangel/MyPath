package com.onpath.app.suggest

import kotlin.math.cos

/**
 * The signed circular routes near a start (the POC's `official.py` near / lay_on / covered): from the suggest files'
 * LOOP sections, of the wish's kind, wholly inside the loaded network – each as a ride FROM the wished start (owner
 * 2026-11-17): the cheapest way to the loop's best joining point, once round in its own order, home – candidates beside
 * ours, judged on the same yardstick as a whole ride, chosen in the same selection.
 */
object Official {
    private const val NEAR = 25.0
    private val KM = mapOf("hike" to (4.0 to 20.0), "bicycle" to (15.0 to 60.0), "mtb" to (15.0 to 60.0))
    /** jogging and fitness circuits are signed loops too, but no tour to suggest */
    private val NOT_A_TOUR = Regex("laufstrecke|lauftreff|nordic[- ]?walking|trimm|finnenbahn|walking[- ]?strecke", RegexOption.IGNORE_CASE)
    private val FAMILY = listOf("Traumpfädchen", "Traumpfad", "Saar-Hunsrück-Traumschleife", "Nationalpark-Traumschleife",
        "Traumschleife", "MTB-Park Pfälzerwald")
    private val NETWORK = mapOf("hike" to "Rundwanderweg", "bicycle" to "Radrundweg", "mtb" to "MTB-Rundtour")
    private val KINDS = mapOf("hike" to listOf("hike"), "bike" to listOf("bicycle"), "gravel" to listOf("bicycle", "mtb"),
        "mtb" to listOf("mtb", "bicycle"))

    /** 'Traumpfad' for a Traumpfad, else what kind of signed loop it is. */
    fun badge(lp: SuggestFile.Loop): String = FAMILY.firstOrNull { lp.name.startsWith(it) } ?: NETWORK[lp.kind] ?: "Rundweg"

    /** official.py `loops_in_reach` (files only, as phone and planner work). */
    fun inReach(files: SuggestFiles, lon: Double, lat: Double, reach: Double): List<SuggestFile.Loop> {
        val out = ArrayList<SuggestFile.Loop>()
        for (c in Land.cellsFor(lon, lat, reach)) {
            val f = files.cell(c[0], c[1]) ?: continue
            for (lp in f.loops ?: continue) {
                val km = KM[lp.kind] ?: continue
                if (lp.m / 1000.0 >= km.first && lp.m / 1000.0 <= km.second && !NOT_A_TOUR.containsMatchIn(lp.name)) out.add(lp)
            }
        }
        return out
    }

    /**
     * official.py `near` → each loop as a ride from the start: [Generate.assemble] with the loop's turning points from
     * its best joining point (the one cheapest to reach by the search cost) round in its order – either direction, the
     * shorter kept, a ride meeting its own way more than allowed dropped as ours are – the loop's parts on the laid-on
     * cost, the way there and home as for our own loops.
     */
    fun near(P: Place, S: SuggestSettings, files: SuggestFiles): List<Candidate> {
        val w = P.ch.wish
        val kinds = KINDS[w.activity] ?: listOf("hike")
        val reach = 0.95 * P.ch.radius(S)
        val net = P.net
        val gen = Generate(P, S)
        val maxCross = S["form"]["max_cross"].d
        val out = ArrayList<Candidate>()
        for (lp in inReach(files, w.lon, w.lat, reach)) {
            if (lp.kind !in kinds) continue
            val n = lp.line.size / 2
            if (n == 0) continue
            var inside = true
            var k = 0
            while (k < n) { if (SuggestGeo.dist(w.lon, w.lat, lp.line[2 * k], lp.line[2 * k + 1]) > reach) { inside = false; break }; k += 5 }
            if (!inside) continue
            val t = trail(P, lp.line)
            val seq = thin(t.index, -1).filter { P.d0[it] < NetSearch.INF }
            if (seq.size < 3) continue
            var j = 0
            for (i in seq.indices) if (P.d0[seq[i]] < P.d0[seq[j]]) j = i
            val join = seq[j]
            val kept = follow(P, t.cost, join, seq.subList(j + 1, seq.size) + seq.subList(0, j))?.second ?: continue
            if (kept.size < 2) continue
            var best: Candidate? = null
            for (dir in listOf(kept, kept.reversed())) {  // round in the loop's order, either direction
                val points = IntArray(dir.size + 2)
                points[0] = join
                for (i in dir.indices) points[i + 1] = dir[i]
                points[dir.size + 1] = join
                val costs = HashMap<Int, DoubleArray>()
                for (i in 1 until points.size) costs[i] = t.cost
                val edges = gen.assemble(points, costs) ?: continue
                val m = Generate.measure(P, edges, P.start) ?: continue
                // like our own loops: a ride that meets its own way more often than allowed is not offered
                if (m.cross <= maxCross && (best == null || m.m < best.m)) best = m
            }
            val c = best ?: continue
            val edges = c.edges
            if (covered(dense(lp.line), lineOf(net, edges, P.start)) < 0.8) continue
            var onM = 0.0
            for (e in edges) if (e in t.on) onM += P.F.ln[e]
            c.origin = "offiziell"
            c.aim = listOf(lp.name)
            c.show = true
            c.official = Candidate.Official(lp.name, badge(lp), lp.id, SuggestGeo.pyRound(onM / 1000.0, 1))
            out.add(c)
        }
        return out
    }

    /** official.py `dense`: points every [step] metres along a line (first point, then each piece's points after it). */
    fun dense(c: DoubleArray, step: Double = 10.0): DoubleArray {
        val out = DoubleList()
        out.add(c[0], c[1])
        for (j in 0 until c.size / 2 - 1) {
            val a0 = c[2 * j]; val a1 = c[2 * j + 1]; val b0 = c[2 * j + 2]; val b1 = c[2 * j + 3]
            val n = maxOf(1, (SuggestGeo.dist(a0, a1, b0, b1) / step).toInt())
            for (k in 1..n) out.add(a0 + (b0 - a0) * k / n, a1 + (b1 - a1) * k / n)
        }
        return out.toArray()
    }

    private fun square(lon: Double, lat: Double, size: Double): Long {
        val x = (lon * 111320.0 * cos(Math.toRadians(lat)) / size).toInt()
        val y = (lat * 111320.0 / size).toInt()
        return (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    }

    private fun around(lon: Double, lat: Double): LongArray {
        val k = square(lon, lat, NEAR)
        val x = (k shr 32).toInt(); val y = k.toInt()
        val out = LongArray(9)
        var i = 0
        for (dx in -1..1) for (dy in -1..1) out[i++] = ((x + dx).toLong() shl 32) or ((y + dy).toLong() and 0xffffffffL)
        return out
    }

    /** official.py `covered`: share of [pts] within 25 m of the line [other]. */
    fun covered(pts: DoubleArray, other: DoubleArray): Double {
        val grid = HashMap<Long, DoubleList>()
        val d = dense(other)
        for (i in 0 until d.size / 2) grid.getOrPut(square(d[2 * i], d[2 * i + 1], NEAR)) { DoubleList(8) }.add(d[2 * i], d[2 * i + 1])
        var hit = 0
        val n = pts.size / 2
        for (i in 0 until n) {
            val x = pts[2 * i]; val y = pts[2 * i + 1]
            var ok = false
            for (key in around(x, y)) {
                val l = grid[key] ?: continue
                for (k in 0 until l.size / 2) if (SuggestGeo.dist(x, y, l[2 * k], l[2 * k + 1]) <= NEAR) { ok = true; break }
                if (ok) break
            }
            if (ok) hit++
        }
        return hit.toDouble() / maxOf(1, n)
    }

    /** official.py `line_of`: the line of a walk of links from [start]. */
    fun lineOf(net: WayNet, edges: IntArray, start: Int): DoubleArray {
        val out = DoubleList()
        var node = start
        out.add(net.lon(node), net.lat(node))
        for (e in edges) {
            val g = Facts.geomOf(net, e, node)
            for (k in 1 until g.size / 2) out.add(g[2 * k], g[2 * k + 1])
            node = net.other(e, node)
        }
        return out.toArray()
    }

    /** official.py `trail`: the laid-on cost (links along the loop cheap, others 25 × dearer), the links along it, and
     * the nodes of those links with their index along the loop. */
    class Trail(val cost: DoubleArray, val on: PyIntSet, val index: LinkedHashMap<Int, Int>)

    fun trail(P: Place, coords: DoubleArray): Trail {
        val net = P.net; val F = P.F
        val pts = dense(coords)
        val okEdges = (0 until net.linkCount).filter { F.ok[it] }
        val on = Generate.runsAlong(net, pts, okEdges, NEAR)
        val cost = DoubleArray(net.linkCount) { if (!F.ok[it]) -1.0 else if (it in on) F.ln[it] else 25.0 * F.ln[it] }
        val igrid = HashMap<Long, ArrayList<Int>>()
        for (i in 0 until pts.size / 2) igrid.getOrPut(square(pts[2 * i], pts[2 * i + 1], NEAR)) { ArrayList() }.add(i)
        val index = LinkedHashMap<Int, Int>()
        for (e in on.toIntArray()) {
            for (v in intArrayOf(net.linkA[e], net.linkB[e])) {
                val x = net.lon(v); val y = net.lat(v)
                var best = -1
                var bd = 0.0
                for (key in around(x, y)) for (i in igrid[key] ?: continue) {
                    val d = SuggestGeo.dist(x, y, pts[2 * i], pts[2 * i + 1])
                    if (best < 0 || d < bd) { best = i; bd = d }
                }
                if (best >= 0 && bd <= NEAR) index[v] = best
            }
        }
        return Trail(cost, on, index)
    }

    /** official.py `thin`: one turning point per ~200 m of the loop, in its order (not [start]; -1 = none). */
    fun thin(index: LinkedHashMap<Int, Int>, start: Int): List<Int> {
        val aims = ArrayList<Int>()
        var last = -99
        for (v in index.keys.sortedBy { index.getValue(it) }) {
            if (index.getValue(v) - last >= 20 && v != start) { aims.add(v); last = index.getValue(v) }
        }
        return aims
    }

    /**
     * official.py `follow`: from [start] through [aims] and home on [cost]; a point reached only by a long detour (a
     * piece our network lacks) is skipped. → (links, the points kept), or null.
     */
    fun follow(P: Place, cost: DoubleArray, start: Int, aims: List<Int>): Pair<IntArray, List<Int>>? {
        val net = P.net
        val edges = ArrayList<Int>()
        val kept = ArrayList<Int>()
        var cur = start
        val s = P.search
        for (w in aims + start) {
            s.toStop(cur, w, cost)
            if (!s.reached(w)) return null
            if (w != start && s.metres[w] > 4.0 * SuggestGeo.dist(net.lon(cur), net.lat(cur), net.lon(w), net.lat(w)) + 1000.0) continue
            for (e in s.path(w)) edges.add(e)
            if (w != start) kept.add(w)
            cur = w
        }
        val out = ArrayList<Int>()
        for (e in edges) if (out.isNotEmpty() && out[out.size - 1] == e) out.removeAt(out.size - 1) else out.add(e)
        return out.toIntArray() to kept
    }

    /**
     * official.py `lay_on`: the loop on our network – links along it cheap, others 25 × dearer; from [start] through a
     * point every ~200 m of it (in its order) and home; a piece our network lacks is skipped. → links, or null.
     */
    fun layOn(P: Place, coords: DoubleArray, start: Int): IntArray? {
        val t = trail(P, coords)
        return follow(P, t.cost, start, thin(t.index, start))?.first
    }
}
