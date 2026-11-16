package com.onpath.app.suggest

import kotlin.math.cos
import kotlin.math.hypot

/**
 * The way network around a start for the tour suggestions (docs/design/tour-suggestions.md §3 step 2, §7 "option 1"):
 * BRouter's graph nodes and links as plain arrays – no object per node or link, so a bike network of 250 000 nodes
 * takes ~20 MB. Built by [NetReader] from the rd5 files (or by [Builder] in tests); read-only afterwards.
 *
 * Nodes: position as BRouter's integer micro-degrees (`ilon` = (lon + 180) · 10⁶, `ilat` = (lat + 90) · 10⁶) and the
 * height in BRouter's quarter metres ([NO_ELE] = none). Node 0 is not special; [start] is the node next to the start.
 * Links: from [linkA] to [linkB] (the direction the walk found them), length in metres, metres up / down in that
 * direction (link geometry included), the way kind ([WayKinds]: profile cost factors and the few way tags), [linkFlags]
 * and the shape points between the two nodes ([geo], lon / lat pairs from a to b).
 *
 * Pure Kotlin (shared with the web planner): no BRouter, no Android.
 */
class WayNet(
    val start: Int,
    val nodeCount: Int,
    val nodeLon: IntArray,
    val nodeLat: IntArray,
    val nodeEle: ShortArray,
    val linkCount: Int,
    val linkA: IntArray,
    val linkB: IntArray,
    val linkLen: FloatArray,
    val linkUp: FloatArray,
    val linkDown: FloatArray,
    val linkKind: IntArray,
    val linkFlags: ByteArray,
    /** Shape points of link e: geo[2·geoOff[e] until 2·geoOff[e + 1]] as lon, lat pairs (a → b). */
    val geoOff: IntArray,
    val geo: IntArray,
    val kinds: WayKinds,
) {
    /** Adjacency (both directions): the links at node v are adjLink[adjOff[v] until adjOff[v + 1]], the far ends adjNode. */
    val adjOff = IntArray(nodeCount + 1)
    val adjLink = IntArray(2 * linkCount)
    val adjNode = IntArray(2 * linkCount)

    init {
        for (e in 0 until linkCount) { adjOff[linkA[e] + 1]++; adjOff[linkB[e] + 1]++ }
        for (i in 0 until nodeCount) adjOff[i + 1] += adjOff[i]
        val fill = adjOff.copyOf(nodeCount)
        // the order of the POC's Net.adj: per node the links in link order, each seen from both ends
        for (e in 0 until linkCount) {
            var p = fill[linkA[e]]++; adjLink[p] = e; adjNode[p] = linkB[e]
            p = fill[linkB[e]]++; adjLink[p] = e; adjNode[p] = linkA[e]
        }
    }

    fun lon(v: Int): Double = (nodeLon[v] - 180_000_000) / 1e6
    fun lat(v: Int): Double = (nodeLat[v] - 90_000_000) / 1e6

    /** Height in metres; below −1000 = unknown (the POC's convention: BRouter's "no height" is −8192). */
    fun ele(v: Int): Double = nodeEle[v] / 4.0

    fun other(e: Int, v: Int): Int = if (linkA[e] == v) linkB[e] else linkA[e]

    /** May the activity use link [e] leaving node [from] (the profile's verdict as BRouter routes, see [ALLOWED_AB])? */
    fun allowed(e: Int, from: Int): Boolean =
        linkFlags[e].toInt() and (if (from == linkA[e]) ALLOWED_AB else ALLOWED_BA) != 0

    /** Allowed in at least one direction. */
    fun allowed(e: Int): Boolean = linkFlags[e].toInt() and (ALLOWED_AB or ALLOWED_BA) != 0

    /** The profile's cost factor for link [e] leaving node [from]. */
    fun costFactor(e: Int, from: Int): Float =
        kinds.costFactor(linkKind[e], reverse = (linkFlags[e].toInt() and REVERSED != 0) xor (from != linkA[e]))

    fun shapePoints(e: Int): Int = geoOff[e + 1] - geoOff[e]

    /** Heap size of the arrays, spare room and adjacency included (bytes). */
    fun bytes(): Long = nodeLon.size * 10L + linkA.size * 25L + 4L * geoOff.size + 4L * geo.size +
        4L * (nodeCount + 1) + 16L * linkCount + kinds.bytes()

    companion object {
        /** BRouter's "no height". */
        const val NO_ELE: Short = Short.MIN_VALUE
        /** a → b may be used: the way's cost factor in that direction ≤ 9998 (BRouter blocks > 9998) and node b does
         *  not bar it (BRouter's node rule: initial cost ≥ 10⁶ with the way's node access). */
        const val ALLOWED_AB = 1
        const val ALLOWED_BA = 2
        /** a → b is against the direction the way description is stored in (BRouter's "reverse"). */
        const val REVERSED = 4
        /** The way alone allows a → b / b → a (cost factor < 9999, the POC's rule) – without the node check. */
        const val WAY_AB = 8
        const val WAY_BA = 16

        fun ilon(lon: Double): Int = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
        fun ilat(lat: Double): Int = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()

        /** Metres between two BRouter positions – the POC's flat-earth formula (service/RouteService.java `dist`). */
        fun metres(lon1: Int, lat1: Int, lon2: Int, lat2: Int): Double {
            val ky = 0.11132
            val kx = ky * cos(Math.toRadians((lat1 + lat2) / 2e6 - 90.0))
            return hypot((lon1 - lon2) * kx, (lat1 - lat2) * ky)
        }
    }

    /** Collects nodes and links in growing arrays; [build] trims them to size. Used by [NetReader] and tests. */
    class Builder(val kinds: WayKinds = WayKinds()) {
        var nodeCount = 0; private set
        var linkCount = 0; private set
        private var nLon = IntArray(1024)
        private var nLat = IntArray(1024)
        private var nEle = ShortArray(1024)
        private var lA = IntArray(1024)
        private var lB = IntArray(1024)
        private var lLen = FloatArray(1024)
        private var lUp = FloatArray(1024)
        private var lDown = FloatArray(1024)
        private var lKind = IntArray(1024)
        private var lFlags = ByteArray(1024)
        private var gOff = IntArray(1025)
        private var g = IntArray(4096)
        private var gN = 0

        fun addNode(ilon: Int, ilat: Int, selev: Short): Int {
            if (nodeCount == nLon.size) {
                val c = grow(nodeCount)
                nLon = nLon.copyOf(c); nLat = nLat.copyOf(c); nEle = nEle.copyOf(c)
            }
            nLon[nodeCount] = ilon; nLat[nodeCount] = ilat; nEle[nodeCount] = selev
            return nodeCount++
        }

        /** Starts link a → b; then [shape] for each point between the nodes, then [endLink]. */
        fun beginLink(a: Int, b: Int, kind: Int, flags: Int): Int {
            if (linkCount + 1 >= lA.size) {
                val c = grow(linkCount + 1)
                lA = lA.copyOf(c); lB = lB.copyOf(c); lLen = lLen.copyOf(c); lUp = lUp.copyOf(c); lDown = lDown.copyOf(c)
                lKind = lKind.copyOf(c); lFlags = lFlags.copyOf(c); gOff = gOff.copyOf(c + 1)
            }
            lA[linkCount] = a; lB[linkCount] = b; lKind[linkCount] = kind; lFlags[linkCount] = flags.toByte()
            gOff[linkCount] = gN / 2
            return linkCount
        }

        fun shape(ilon: Int, ilat: Int) {
            if (gN + 2 > g.size) g = g.copyOf(grow(g.size))
            g[gN++] = ilon; g[gN++] = ilat
        }

        fun endLink(len: Float, up: Float, down: Float) {
            lLen[linkCount] = len; lUp[linkCount] = up; lDown[linkCount] = down
            linkCount++
            gOff[linkCount] = gN / 2
        }

        /** A link without shape points (tests). */
        fun addLink(a: Int, b: Int, len: Float, kind: Int = 0, flags: Int = ALLOWED_AB or ALLOWED_BA or WAY_AB or WAY_BA,
                    up: Float = 0f, down: Float = 0f): Int {
            val e = beginLink(a, b, kind, flags)
            endLink(len, up, down)
            return e
        }

        fun ilonOf(v: Int) = nLon[v]
        fun ilatOf(v: Int) = nLat[v]

        /** Heap of the arrays so far, with their spare room (bytes). */
        fun capacityBytes(): Long = nLon.size * 10L + lA.size * 25L + gOff.size * 4L + g.size * 4L

        /** The network; the arrays are handed over as they are (spare room at the end is never read – no copy, so
         *  reading needs no second set of arrays at its end). Do not use the builder afterwards. */
        fun build(start: Int): WayNet = WayNet(start, nodeCount, nLon, nLat, nEle, linkCount, lA, lB, lLen, lUp, lDown,
            lKind, lFlags, gOff, g, kinds)

        private fun grow(n: Int) = n + (n shr 1) + 16
    }
}
