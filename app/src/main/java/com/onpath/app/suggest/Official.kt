package com.onpath.app.suggest

import kotlin.math.cos

/**
 * The signed circular routes near a start (the POC's `official.py` near / lay_on / covered): from the suggest files'
 * LOOP sections, of the wish's kind, wholly inside the loaded network, laid onto the network from their own start
 * (the parking place) – candidates beside ours, judged on the same yardstick, chosen in the same selection.
 */
object Official {
    private const val NEAR = 25.0
    private const val PARKING = 250.0
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

    /** official.py `near` → the loops laid onto the network, measured, marked to be shown. */
    fun near(P: Place, S: SuggestSettings, files: SuggestFiles): List<Candidate> {
        val w = P.ch.wish
        val kinds = KINDS[w.activity] ?: listOf("hike")
        val reach = 0.95 * P.ch.radius(S)
        val net = P.net
        val out = ArrayList<Candidate>()
        for (lp in inReach(files, w.lon, w.lat, reach)) {
            if (lp.kind !in kinds) continue
            val n = lp.line.size / 2
            if (n == 0) continue
            var inside = true
            var k = 0
            while (k < n) { if (SuggestGeo.dist(w.lon, w.lat, lp.line[2 * k], lp.line[2 * k + 1]) > reach) { inside = false; break }; k += 5 }
            if (!inside) continue
            var first = 0
            var fd = Double.MAX_VALUE
            for (v in 0 until net.nodeCount) {
                val d = SuggestGeo.dist(net.lon(v), net.lat(v), lp.line[0], lp.line[1])
                if (d < fd) { fd = d; first = v }
            }
            val edges = layOn(P, lp.line, first) ?: continue
            if (edges.isEmpty()) continue
            val c = Generate.measure(P, edges, first) ?: continue
            if (covered(dense(lp.line), lineOf(net, edges, first)) < 0.8) continue
            c.origin = "offiziell"
            c.aim = listOf(lp.name)
            c.show = true
            c.official = Candidate.Official(lp.name, badge(lp), lp.parkingM != null && lp.parkingM < PARKING, lp.line[0], lp.line[1], lp.id,
                SuggestGeo.pyRound(SuggestGeo.dist(w.lon, w.lat, lp.line[0], lp.line[1]) / 1000.0, 1))
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

    /**
     * official.py `lay_on`: the loop on our network – links along it cheap, others 25 × dearer; from [start] through a
     * point every ~200 m of it (in its order) and home; a piece our network lacks is skipped. → links, or null.
     */
    fun layOn(P: Place, coords: DoubleArray, start: Int): IntArray? {
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
        val aims = ArrayList<Int>()
        var last = -99
        for (v in index.keys.sortedBy { index.getValue(it) }) {
            if (index.getValue(v) - last >= 20 && v != start) { aims.add(v); last = index.getValue(v) }
        }
        aims.add(start)
        val edges = ArrayList<Int>()
        var cur = start
        val s = P.search
        for (w in aims) {
            s.toStop(cur, w, cost)
            if (!s.reached(w)) return null
            if (w != start && s.metres[w] > 4.0 * SuggestGeo.dist(net.lon(cur), net.lat(cur), net.lon(w), net.lat(w)) + 1000.0) continue
            for (e in s.path(w)) edges.add(e)
            cur = w
        }
        val out = ArrayList<Int>()
        for (e in edges) if (out.isNotEmpty() && out[out.size - 1] == e) out.removeAt(out.size - 1) else out.add(e)
        return out.toIntArray()
    }
}
