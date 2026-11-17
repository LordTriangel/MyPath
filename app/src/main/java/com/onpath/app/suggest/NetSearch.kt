package com.onpath.app.suggest

import kotlin.math.cos
import kotlin.math.hypot

/**
 * The graph searches of the tour suggestions on a [WayNet] – a port of the POC's `net.py` (`dijkstra`, `path`,
 * `seat_start`, `climb`) and of `generate.py`'s loop assembly (`assemble`, `cut_spurs`), same results for the same
 * inputs (NetSearchParityTest against the Python). Costs are passed in per link (`cost[link] < 0` = closed), so the
 * method decides what "pleasant" means; the network only carries facts.
 *
 * One [NetSearch] per network and thread: its arrays (cost so far, previous node / link, metres so far) are reused –
 * a search resets only the nodes the last one touched, so the hundred searches of one wish allocate nothing. The
 * results of [run] stay valid until the next run; [tree] keeps a copy (the one full search from the start).
 *
 * Pure Kotlin (shared with the web planner).
 */
class NetSearch(
    val net: WayNet,
    /** Metres per link the searches count (default the network's; the method passes its own 0.1 m lengths, `Facts.ln`). */
    val lengths: DoubleArray = DoubleArray(net.linkCount) { net.linkLen[it].toDouble() },
) {
    private val n = net.nodeCount
    /** Cost so far per node ([INF] = not reached) of the last [run]. */
    var dist = DoubleArray(n) { INF }; private set
    /** Metres so far along the cheapest way. */
    var metres = DoubleArray(n); private set
    var prevNode = IntArray(n) { -1 }; private set
    var prevLink = IntArray(n) { -1 }; private set
    /** Nodes taken from the queue in the last run (a measure of the work). */
    var settled = 0L; private set

    private var touched = IntArray(n)
    private var touchedN = 0

    /**
     * Gives the work arrays back (a result kept on screen should not hold them); the next [run] makes them again. Until
     * then the last run's results ([dist], [path] …) are gone.
     */
    fun release() {
        dist = DoubleArray(0); metres = DoubleArray(0); prevNode = IntArray(0); prevLink = IntArray(0); touched = IntArray(0)
        touchedN = 0; heapNode = IntArray(256); heapKey = DoubleArray(256)
    }
    private var heapNode = IntArray(256)
    private var heapKey = DoubleArray(256)
    private var heapSize = 0

    /**
     * Cheapest ways from [src] (net.py `dijkstra`). [pen] = a factor per link for this search only (0 = none – the
     * POC's `pen` dict); [stop] ≥ 0 ends the search when that node is taken; beyond [maxLen] metres a node is not
     * expanded. Ties are broken by node number, as Python's heap of (cost, node) does. [withMetres] false: [metres]
     * is not kept (the loop assembly's searches only need the path – [pathMetres] gives a path's metres, the same sum).
     * [scaleMask] / [scale]: the links of the mask cost [scale] × – as if the cost array were a scaled copy (the copy's
     * very numbers: cost × scale, then the closed test, then [pen]), without making one.
     */
    fun run(src: Int, cost: DoubleArray, pen: DoubleArray? = null, stop: Int = -1, maxLen: Double = INF, withMetres: Boolean = true,
            scaleMask: BooleanArray? = null, scale: Double = 1.0) {
        if (dist.size != n) {  // after release()
            dist = DoubleArray(n) { INF }; metres = DoubleArray(n); prevNode = IntArray(n) { -1 }; prevLink = IntArray(n) { -1 }
            touched = IntArray(n); touchedN = 0
        }
        for (i in 0 until touchedN) {
            val v = touched[i]
            dist[v] = INF; metres[v] = 0.0; prevNode[v] = -1; prevLink[v] = -1
        }
        touchedN = 0
        heapSize = 0
        dist[src] = 0.0
        touched[touchedN++] = src
        push(src, 0.0)
        val adjOff = net.adjOff; val adjLink = net.adjLink; val adjNode = net.adjNode; val len = lengths
        while (heapSize > 0) {
            val du = heapKey[0]
            val u = pop()
            if (du > dist[u]) continue
            settled++
            if (u == stop) break
            if (metres[u] > maxLen) continue
            for (p in adjOff[u] until adjOff[u + 1]) {
                val e = adjLink[p]
                var c = cost[e]
                if (scaleMask != null && scaleMask[e]) c *= scale
                if (c < 0) continue
                if (pen != null) { val f = pen[e]; if (f != 0.0) c *= f }
                val v = adjNode[p]
                val nd = du + c
                if (nd < dist[v]) {
                    if (dist[v] == INF) touched[touchedN++] = v
                    dist[v] = nd; prevNode[v] = u; prevLink[v] = e
                    if (withMetres) metres[v] = metres[u] + len[e]
                    push(v, nd)
                }
            }
        }
    }

    /** The full search (every reachable node). */
    fun full(src: Int, cost: DoubleArray) = run(src, cost)

    /** The search that ends at [stop]; [withMetres] false: no [metres] (see [run]). */
    fun toStop(src: Int, stop: Int, cost: DoubleArray, pen: DoubleArray? = null, withMetres: Boolean = true,
               scaleMask: BooleanArray? = null, scale: Double = 1.0) =
        run(src, cost, pen, stop = stop, withMetres = withMetres, scaleMask = scaleMask, scale = scale)

    /** Metres along [path] (links in walking order from the source) – what [metres] holds at its end, summed the same
     *  way (from 0, link by link). */
    fun pathMetres(path: IntArray): Double {
        var m = 0.0
        for (e in path) m += lengths[e]
        return m
    }

    /** The search that expands no node beyond [maxLen] metres. */
    fun bounded(src: Int, maxLen: Double, cost: DoubleArray, pen: DoubleArray? = null) = run(src, cost, pen, maxLen = maxLen)

    fun reached(v: Int): Boolean = dist[v] < INF

    /** Links from the last search's source to [t] in walking order (empty when not reached or t is the source). */
    fun path(t: Int): IntArray = path(prevNode, prevLink, t)

    /** A copy of the last search's result (net.py keeps the full search from the start: `P.prev0`, `P.pe0`). */
    fun tree(): Tree = Tree(dist.copyOf(), prevNode.copyOf(), prevLink.copyOf(), metres.copyOf())

    class Tree(val dist: DoubleArray, val prevNode: IntArray, val prevLink: IntArray, val metres: DoubleArray) {
        fun path(t: Int): IntArray = path(prevNode, prevLink, t)
    }

    // a 4-ary heap of (cost, node): (cost, node) pairs are unique (a node is pushed again only cheaper), so any heap
    // takes them out in the same order as Python's heapq – this one with half the levels of a binary heap
    private fun push(v: Int, k: Double) {
        if (heapSize == heapNode.size) {
            heapNode = heapNode.copyOf(heapSize * 2); heapKey = heapKey.copyOf(heapSize * 2)
        }
        var i = heapSize++
        while (i > 0) {
            val p = (i - 1) shr 2
            val pk = heapKey[p]
            if (pk < k || (pk == k && heapNode[p] < v)) break
            heapNode[i] = heapNode[p]; heapKey[i] = pk; i = p
        }
        heapNode[i] = v; heapKey[i] = k
    }

    private fun pop(): Int {
        val top = heapNode[0]
        heapSize--
        if (heapSize == 0) return top
        val v = heapNode[heapSize]
        val k = heapKey[heapSize]
        var i = 0
        while (true) {
            val c = 4 * i + 1
            if (c >= heapSize) break
            var m = c
            var mk = heapKey[c]
            var mn = heapNode[c]
            val end = minOf(c + 4, heapSize)
            for (j in c + 1 until end) {
                val jk = heapKey[j]
                if (jk < mk || (jk == mk && heapNode[j] < mn)) { m = j; mk = jk; mn = heapNode[j] }
            }
            if (mk > k || (mk == k && mn > v)) break
            heapNode[i] = mn; heapKey[i] = mk; i = m
        }
        heapNode[i] = v; heapKey[i] = k
        return top
    }

    companion object {
        const val INF = Double.POSITIVE_INFINITY

        /** net.py `path`: the links from the source to [t], in walking order. */
        fun path(prevNode: IntArray, prevLink: IntArray, t: Int): IntArray {
            var k = 0
            var x = t
            while (prevNode[x] >= 0) { k++; x = prevNode[x] }
            val out = IntArray(k)
            x = t
            while (prevNode[x] >= 0) { out[--k] = prevLink[x]; x = prevNode[x] }
            return out
        }

        /** net.py `dist`: metres between two points given in degrees (flat earth, fine for a few kilometres). */
        fun dist(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
            val kx = cos(Math.toRadians((lat1 + lat2) / 2.0)) * 111320.0
            return hypot((lon1 - lon2) * kx, (lat1 - lat2) * 111320.0)
        }

        /**
         * net.py `seat_start`: a start on a dead piece of way (connected over links with [ok] only) moves to the nearest
         * node of the biggest piece, unless its own piece has at least a fifth of that size. → the start and the metres
         * moved (0 = it stays).
         */
        fun seatStart(net: WayNet, start: Int, ok: BooleanArray): Pair<Int, Int> {
            val n = net.nodeCount
            val comp = IntArray(n) { -1 }
            val sizes = ArrayList<Int>()
            val stack = IntArray(n)
            for (v0 in 0 until n) {
                if (comp[v0] >= 0) continue
                val c = sizes.size
                comp[v0] = c
                var sp = 0
                stack[sp++] = v0
                var size = 0
                while (sp > 0) {
                    val u = stack[--sp]
                    size++
                    for (p in net.adjOff[u] until net.adjOff[u + 1]) {
                        val v = net.adjNode[p]
                        if (comp[v] < 0 && ok[net.adjLink[p]]) { comp[v] = c; stack[sp++] = v }
                    }
                }
                sizes.add(size)
            }
            var big = 0
            for (i in sizes.indices) if (sizes[i] > sizes[big]) big = i
            if (comp[start] == big || sizes[comp[start]] >= 0.2 * sizes[big]) return start to 0
            val lon = net.lon(start); val lat = net.lat(start)
            var best = -1
            var bestD = INF
            for (v in 0 until n) {
                if (comp[v] != big) continue
                val d = dist(lon, lat, net.lon(v), net.lat(v))
                if (d < bestD) { bestD = d; best = v }
            }
            return best to bestD.toInt()
        }

        /** net.py `climb`: metres up along a walk of nodes (rises under 6 m are noise; nodes without height skipped). */
        fun climb(net: WayNet, nodes: IntArray): Double {
            var up = 0.0
            var ref = Double.NaN
            for (x in nodes) {
                val ele = net.ele(x)
                if (ele < -1000) continue
                if (ref.isNaN() || ele < ref - 6.0) ref = ele
                else if (ele > ref + 6.0) { up += ele - ref; ref = ele }
            }
            return up
        }
    }
}

/**
 * generate.py `assemble` + `cut_spurs`: a loop from [start] through the turning points and back, each part the
 * cheapest way; ways already in the loop cost [used] ×, ways touching them [besideUsed] × (beyond the first
 * [freeStemM] metres, which stay free: a short way out and back is fine). [fromStart] = the full search from the start
 * with [cost] (its tree gives the first part).
 */
class LoopAssembly(
    private val search: NetSearch,
    private val start: Int,
    private val cost: DoubleArray,
    private val fromStart: NetSearch.Tree,
    private val freeStemM: Double,
    private val used: Double,
    private val besideUsed: Double,
    /** the links a [calm] assembly makes dearer ([calmScale] ×): roads with cars outside towns */
    private val calmMask: BooleanArray? = null,
    private val calmScale: Double = 1.0,
) {
    private val net = search.net
    private val pen = DoubleArray(net.linkCount)
    private val penSet = IntArray(net.linkCount)

    /**
     * → the loop's links in walking order, or null. [costs] = other costs for some parts (index into points + home);
     * [keep] = nodes (aims) whose way out and back stays in the loop (the last stretch up to a summit). [calm]: every
     * part on its cost with the [calmMask] links [calmScale] × dearer (generate.py's rebuild without the road).
     */
    fun assemble(points: IntArray, costs: Map<Int, DoubleArray>? = null, keep: Set<Int> = emptySet(), calm: Boolean = false): IntArray? {
        var penN = 0
        try {
            val edges = ArrayList<Int>()
            var cur = start
            var walked = 0.0
            for (i in 0..points.size) {
                val w = if (i < points.size) points[i] else start
                val c = costs?.get(i) ?: cost
                val leg: IntArray? = if (i == 0 && c === cost && !calm) fromStart.path(w) else {
                    if (calm) search.toStop(cur, w, c, pen, withMetres = false, scaleMask = calmMask, scale = calmScale)
                    else search.toStop(cur, w, c, pen, withMetres = false)
                    if (search.reached(w)) search.path(w) else null
                }
                if (leg == null || (leg.isEmpty() && w != cur)) return null
                var node = cur
                for (e in leg) {
                    walked += search.lengths[e]
                    node = net.other(e, node)
                    if (walked > freeStemM) {
                        if (pen[e] == 0.0) penSet[penN++] = e
                        pen[e] = used
                        for (p in net.adjOff[node] until net.adjOff[node + 1]) {
                            val e2 = net.adjLink[p]
                            if (pen[e2] == 0.0) { pen[e2] = besideUsed; penSet[penN++] = e2 }
                        }
                    }
                    edges.add(e)
                }
                cur = w
            }
            val cut = cutSpurs(net, start, edges.toIntArray(), search.lengths, keep)
            return if (cut.size >= 3) cut else null
        } finally {
            for (i in 0 until penN) pen[penSet[i]] = 0.0
        }
    }

    companion object {
        /**
         * generate.py `cut_spurs`: cut a spur (out to a point and back the same way, also with a small loop at its tip) –
         * not when its tip is an aim in [keep]: the way up to a summit and back down is the point of the loop.
         */
        fun cutSpurs(net: WayNet, start: Int, input: IntArray,
                     lengths: DoubleArray = DoubleArray(net.linkCount) { net.linkLen[it].toDouble() }, keep: Set<Int> = emptySet()): IntArray {
            var edges = input
            while (true) {
                val out = ArrayList<Int>(edges.size)
                val at = ArrayList<Int>(edges.size + 1)
                at.add(start)
                for (e in edges) {
                    if (out.isNotEmpty() && out[out.size - 1] == e && at[at.size - 1] !in keep) {
                        out.removeAt(out.size - 1); at.removeAt(at.size - 1)
                    } else {
                        out.add(e); at.add(net.other(e, at[at.size - 1]))
                    }
                }
                edges = out.toIntArray()
                var node = start
                val nodes = IntArray(edges.size + 1)
                nodes[0] = start
                val first = HashMap<Int, Int>()
                first[start] = 0
                var cutAt = -1
                var cutEnd = -1
                for (k in edges.indices) {
                    node = net.other(edges[k], node)
                    nodes[k + 1] = node
                    val i = first.getOrPut(node) { k + 1 }
                    val pieceLen = k + 1 - i
                    var tipKept = false
                    if (keep.isNotEmpty() && i in 1..k) for (j in i..k + 1) if (nodes[j] in keep) { tipKept = true; break }
                    if (i in 1..k && pieceLen < 0.6 * edges.size && !tipKept) {
                        var walked = 0.0
                        for (j in i..k) walked += lengths[edges[j]]
                        var once = 0.0
                        val piece = PyIntSet()
                        for (j in i..k) piece.add(edges[j])
                        for (e in piece.toIntArray()) once += lengths[e]
                        if (walked - once >= 0.3 * walked) { cutAt = i; cutEnd = k; break }
                    }
                }
                if (cutAt < 0) return edges
                edges = edges.copyOfRange(0, cutAt) + edges.copyOfRange(cutEnd + 1, edges.size)
            }
        }
    }
}
