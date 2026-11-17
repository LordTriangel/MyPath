package com.onpath.app.suggest

import btools.codec.DataBuffers
import btools.codec.MicroCache
import btools.mapaccess.MatchedWaypoint
import btools.mapaccess.NodesCache
import btools.mapaccess.OsmFile
import btools.mapaccess.OsmNode
import btools.mapaccess.OsmNodePairSet
import btools.mapaccess.OsmNodesMap
import btools.mapaccess.PhysicalFile
import btools.router.ProfileCache
import btools.router.RoutingContext
import btools.util.CheapRuler
import java.io.File

/**
 * Reads the way network around a start from the rd5 files straight into a [WayNet] (docs/design/tour-suggestions.md
 * §7, owner 2026-11-15 "option 1"): the same nodes and links as the POC's route service (`service/RouteService.java`
 * `/graph`) and the phone bench, with the profile's verdict as BRouter routes – but without BRouter's object graph of
 * the whole area (the bench peaked at 230 MB for a 3 h bike wish on the Pixel).
 *
 * How:
 * - The rd5 file format is decoded by BRouter only: per micro tile (1/32°) its `MicroCache2`, per node
 *   `OsmNode.parseNodeBody` – the same code BRouter routes with. What is needed of a tile (node heights, the node's
 *   links in BRouter's order, way and node descriptions, shape bytes) is kept in a [RawTile] of plain arrays; BRouter's
 *   node and link objects are dropped node by node. Raw tiles live in a small LRU ([tileBudgetBytes]); a tile pushed
 *   out is decoded again when needed.
 * - The walk is the POC's: breadth first from the node BRouter snaps the start to (BRouter's own waypoint matching),
 *   every link of an expanded node once, nodes beyond [radiusM] are kept as ends but not expanded, and beyond 500 m from
 *   the start the [Coarse] filter leaves out ways (bikes: minor ways unless a signed route runs on them).
 * - Per distinct way description the profile is evaluated once (cost factors both ways, node access, tag text →
 *   [WayKinds]). [WayNet.ALLOWED_AB] / [WayNet.ALLOWED_BA] follow BRouter's routing rule (`OsmPath`): the way's cost
 *   factor in that direction ≤ 9998 and the node at the end not barred (node initial cost < 10⁶).
 *
 * Not thread-safe; one read at a time per instance. Blocking (a bike network: ~1 s desktop, a few seconds on a phone).
 */
class NetReader(private val segmentsDir: File) {

    /** The routing profile: a .brf file (lookups.dat beside it) and BRouter's parameter overrides. */
    class Profile(val file: File, val keyValues: Map<String, String>)

    /**
     * Ways left out beyond 500 m from the start (the POC's `coarse`): a cost factor ≥ [maxCostFactor] in both
     * directions, or tags matching [skip] unless they also match [keep] (BRouter's tag text, e.g. "highway=footway").
     */
    class Coarse(val maxCostFactor: Float = 1e9f, val skip: Regex? = null, val keep: Regex? = null) {
        companion object {
            val NONE = Coarse()
            /** The POC's bike network (settings.json "wheel" → "coarse"). */
            val BIKE = Coarse(9000f, Regex("highway=(service|footway|steps|pedestrian|platform|corridor|bridleway)"),
                Regex("route_bicycle_[a-z]+=yes|bicycle=designated"))
        }
    }

    /** What the last [read] did (for benches and logs). */
    class Stats {
        var tilesDecoded = 0
        var tilesDecodedAgain = 0
        var tileBytesPeak = 0L
        var nodesDecoded = 0L
        var wayKinds = 0
        /** reverse texts BRouter made (the others are put together from the forward text, Tiles.reverseText) */
        var reverseTexts = 0
        var ms = 0L
        override fun toString() = "tiles $tilesDecoded (again $tilesDecodedAgain), raw tiles peak ${tileBytesPeak / 1_000_000} MB, " +
            "nodes decoded $nodesDecoded, way kinds $wayKinds (reverse texts $reverseTexts), $ms ms"
    }

    class ReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

    var stats = Stats(); private set

    /**
     * The network within [radiusM] of [lon] / [lat] for [profile]. [snapM] = how far the start may be from a way (the
     * POC: 400 m). Throws [ReadException] when there is no routing data or no way near the start.
     */
    fun read(profile: Profile, lon: Double, lat: Double, radiusM: Double, coarse: Coarse = Coarse.NONE,
             snapM: Double = 400.0, tileBudgetBytes: Long = DEFAULT_TILE_BUDGET): WayNet {
        val t0 = System.currentTimeMillis()
        stats = Stats()
        val rc = RoutingContext()
        rc.localFunction = profile.file.absolutePath
        rc.keyValues = HashMap(profile.keyValues)
        ProfileCache.parseProfile(rc)
        val tiles = Tiles(rc, tileBudgetBytes)
        try {
            val startPos = matchStart(rc, lon, lat, snapM)
            val net = Walk(rc, tiles, coarse, WayNet.ilon(lon), WayNet.ilat(lat), radiusM).run(startPos)
            stats.ms = System.currentTimeMillis() - t0
            return net
        } finally {
            tiles.close()
            ProfileCache.releaseProfile(rc)
        }
    }

    /** BRouter's own snapping (as `RoutingEngine` and the POC): the first node of the way next to the point. */
    private fun matchStart(rc: RoutingContext, lon: Double, lat: Double, snapM: Double): Long {
        val nc = NodesCache(segmentsDir, rc.expctxWay, rc.forceSecondaryData, 64L shl 20, null, true)
        try {
            val mwp = MatchedWaypoint()
            mwp.waypoint = OsmNode(WayNet.ilon(lon), WayNet.ilat(lat))
            mwp.name = "from"
            val list = arrayListOf(mwp)
            try {
                nc.matchWaypointsToNodes(list, snapM, OsmNodePairSet(1))
            } catch (e: IllegalArgumentException) {
                throw ReadException("no routing data at the start", e)
            }
            val n1 = mwp.node1 ?: throw ReadException("no way within ${snapM.toInt()} m of the start")
            return n1.idFromPos
        } finally {
            nc.close()
        }
    }

    // ---------------------------------------------------------------- raw tiles

    /**
     * One decoded micro tile, plain arrays (~50 bytes per node). Nodes sorted by position ([find]). Per node: height,
     * node description kind ([Tiles.nodeKind], −1 = none) and its links in BRouter's order (entries entryOff[i] until
     * entryOff[i + 1]): the far node and, for a link whose description is stored here (BRouter's "forward" side), its
     * number in fwdKind / geoOff. A link stored at the far node has −1 (BRouter: "reverse"); its data is read there.
     */
    private class RawTile(
        val baseLon: Int,
        val baseLat: Int,
        val cellsize: Int,
        /** (lon − baseLon) shl 15 or (lat − baseLat), ascending */
        val keys: IntArray,
        val selev: ShortArray,
        val nodeKind: IntArray,
        val entryOff: IntArray,
        val entLon: IntArray,
        val entLat: IntArray,
        val entFwd: IntArray,
        val fwdKind: IntArray,
        /** BRouter's shape bytes of forward link f: geoPool[geoOff[f] until geoOff[f + 1]] */
        val geoOff: IntArray,
        val geoPool: ByteArray,
    ) {
        val bytes: Long = keys.size * 10L + entryOff.size * 4L + entLon.size * 12L + fwdKind.size * 8L + geoPool.size + 300

        /** Index of the node at this position, −1 when the tile has none there. */
        fun find(ilon: Int, ilat: Int): Int {
            val x = ilon - baseLon; val y = ilat - baseLat
            if (x < 0 || y < 0 || x >= cellsize || y >= cellsize) return -1
            return java.util.Arrays.binarySearch(keys, (x shl 15) or y).let { if (it < 0) -1 else it }
        }

        /** The shape bytes of forward link [f] as BRouter keeps them (a fresh array: its decoder caches by identity). */
        fun geometry(f: Int): ByteArray? = if (geoOff[f + 1] == geoOff[f]) null else geoPool.copyOfRange(geoOff[f], geoOff[f + 1])
    }

    private inner class Tiles(val rc: RoutingContext, private val budget: Long) {
        private val buffers = DataBuffers()
        private val files = HashMap<String, PhysicalFile?>()
        private val degrees = HashMap<Int, Degree?>()
        private val lru = object : LinkedHashMap<Long, RawTile>(64, 0.75f, true) {}
        private val decodedOnce = HashSet<Long>()
        /** per tile: nodes queued there and not yet expanded – such a tile is needed again, so it stays */
        private val pending = LongIntMap(256)
        private var bytes = 0L
        private val hollow = OsmNodesMap()

        // way descriptions → kinds by content: a 64-bit hash → kind, checked against a second hash of the kind (no
        // description array is kept – 100 000 kinds of a bike network would hold ~15 MB); a clash goes to [wayClash]
        private val wayByHash = LongIntMap(1 shl 12)
        private var wayCheck = IntArray(256)
        private val wayClash = HashMap<ContentKey, Int>()
        val kinds = WayKinds()
        /** per way kind: bit 0/1 node access granted as stored / reverse, bit 2/3 coarse "skip" as stored / reverse */
        var kindBits = IntArray(256); private set
        private val nodeByContent = HashMap<ContentKey, Int>()
        private val nodeDescs = ArrayList<ByteArray>()
        private val nodeBarred = HashMap<Int, Boolean>()
        var coarse: Coarse = Coarse.NONE

        init { rc.expctxWay.setDecodeForbidden(true) }  // as NodesCache(…, detailed = true): forbidden ways stay in the data

        // the tile returned last (most lookups are in the same tile as the one before): it is the LRU's most recent
        // entry already, so answering it without asking the LRU leaves the LRU's order as it is
        private var lastKey = Long.MIN_VALUE
        private var lastTile: RawTile? = null

        /** The raw tile holding position [ilon] / [ilat]; null when there is no routing data there. */
        fun tile(ilon: Int, ilat: Int): RawTile? {
            val d = degree(ilon / 1_000_000, ilat / 1_000_000) ?: return null
            val key = key(d, ilon, ilat)
            if (key == lastKey) return lastTile
            lru[key]?.let { lastKey = key; lastTile = it; return it }
            val t = decode(d.file, (key ushr 32).toInt(), key.toInt(), d.cellsize)
            if (!decodedOnce.add(key)) stats.tilesDecodedAgain++
            stats.tilesDecoded++
            lru[key] = t
            bytes += t.bytes
            if (bytes > stats.tileBytesPeak) stats.tileBytesPeak = bytes
            if (bytes > budget) evict(key)
            lastKey = key; lastTile = t
            return t
        }

        /** A node at [ilon] / [ilat] was queued (+1) or expanded (−1). */
        fun pending(ilon: Int, ilat: Int, delta: Int) {
            val d = degree(ilon / 1_000_000, ilat / 1_000_000) ?: return
            val key = key(d, ilon, ilat)
            pending.put(key, pending[key].coerceAtLeast(0) + delta)
        }

        /** Least recently used tiles first, those without queued nodes; tiles with queued nodes only above 4 × budget. */
        private fun evict(keep: Long) {
            for (hard in 0..1) {
                val it = lru.entries.iterator()
                while (bytes > (if (hard == 0) budget else 4 * budget) && it.hasNext()) {
                    val e = it.next()
                    if (e.key == keep || (hard == 0 && pending[e.key] > 0)) continue
                    bytes -= e.value.bytes
                    it.remove()
                }
            }
        }

        private fun key(d: Degree, ilon: Int, ilat: Int): Long =
            ((ilon / d.cellsize).toLong() shl 32) or (ilat / d.cellsize).toLong()

        private var lastDegreeKey = Int.MIN_VALUE
        private var lastDegree: Degree? = null

        private fun degree(lonDeg: Int, latDeg: Int): Degree? {
            val key = lonDeg * 1000 + latDeg
            if (key == lastDegreeKey) return lastDegree
            if (degrees.containsKey(key)) return degrees[key].also { lastDegreeKey = key; lastDegree = it }
            val name = fileName(lonDeg, latDeg)
            val pf = if (files.containsKey(name)) files[name] else {
                val file = File(segmentsDir, "$name.rd5")
                val p = if (file.isFile) PhysicalFile(file, buffers, rc.expctxWay.meta.lookupVersion.toInt(),
                    rc.expctxWay.meta.lookupMinorVersion.toInt()) else null
                files[name] = p
                p
            }
            val f = if (pf == null) null else OsmFile(pf, lonDeg, latDeg, buffers).takeIf { it.hasData() }
            val d = if (f == null || pf == null) null else Degree(f, 1_000_000 / pf.divisor)
            degrees[key] = d
            lastDegreeKey = key; lastDegree = d
            return d
        }

        private fun decode(f: OsmFile, lonIdx: Int, latIdx: Int, cellsize: Int): RawTile {
            val mc: MicroCache = f.createMicroCache(lonIdx, latIdx, buffers, rc.expctxWay, null, true, null)
            val n = mc.size
            val baseLon = lonIdx * cellsize; val baseLat = latIdx * cellsize
            // nodes in position order (local offsets < 2^15), the cache index in the low 20 bits
            val order = LongArray(n)
            for (i in 0 until n) {
                val id = mc.getIdForIndex(i)
                val x = (id ushr 32).toInt() - baseLon; val y = id.toInt() - baseLat
                order[i] = (((x.toLong() shl 15) or y.toLong()) shl 20) or i.toLong()
            }
            order.sort()
            val keys = IntArray(n)
            val selev = ShortArray(n)
            val nodeKind = IntArray(n)
            val entryOff = IntArray(n + 1)
            var entLon = IntArray(n * 3 + 4); var entLat = IntArray(n * 3 + 4); var entFwd = IntArray(n * 3 + 4)
            var fwdKind = IntArray(n * 2 + 4); var geoOff = IntArray(n * 2 + 5)
            var pool = ByteArray(n * 8 + 64)
            var m = 0
            var fw = 0
            var gN = 0
            for (p in 0 until n) {
                keys[p] = (order[p] ushr 20).toInt()
                val id = mc.getIdForIndex((order[p] and 0xfffff).toInt())
                val node = OsmNode(id)
                mc.getAndClear(id)
                node.parseNodeBody(mc, hollow, rc.expctxWay)
                stats.nodesDecoded++
                selev[p] = node.selev
                nodeKind[p] = node.nodeDescription?.let { nodeKind(it) } ?: -1
                var l = node.firstlink
                while (l != null) {
                    val t = l.getTarget(node)
                    if (m == entLon.size) {
                        val c = m * 2; entLon = entLon.copyOf(c); entLat = entLat.copyOf(c); entFwd = entFwd.copyOf(c)
                    }
                    entLon[m] = t.ilon; entLat[m] = t.ilat
                    if (!l.isReverse(node) && l.descriptionBitmap != null) {
                        if (fw + 1 >= fwdKind.size) { val c = fw * 2 + 4; fwdKind = fwdKind.copyOf(c); geoOff = geoOff.copyOf(c + 1) }
                        fwdKind[fw] = wayKind(l.descriptionBitmap)
                        val g = l.geometry
                        if (g != null) {
                            if (gN + g.size > pool.size) pool = pool.copyOf(maxOf(pool.size * 2, gN + g.size))
                            System.arraycopy(g, 0, pool, gN, g.size)
                            gN += g.size
                        }
                        geoOff[fw + 1] = gN
                        entFwd[m] = fw++
                    } else {
                        entFwd[m] = -1
                    }
                    m++
                    l = l.getNext(node)
                }
                entryOff[p + 1] = m
                // the far nodes are hollow stand-ins BRouter keeps for weaving – not needed here
                l = node.firstlink
                while (l != null) { hollow.remove(l.getTarget(node)); l = l.getNext(node) }
            }
            return RawTile(baseLon, baseLat, cellsize, keys, selev, nodeKind, entryOff, entLon.copyOf(m), entLat.copyOf(m),
                entFwd.copyOf(m), fwdKind.copyOf(fw), geoOff.copyOf(fw + 1), pool.copyOf(gN))
        }

        private fun wayKind(desc: ByteArray): Int {
            val h = hash64(desc)
            val check = desc.contentHashCode()
            val k = wayByHash[h]
            if (k < 0) return newWayKind(desc).also { wayByHash.put(h, it); wayCheck[it] = check }
            if (wayCheck[k] == check) return k
            val key = ContentKey(desc)
            return wayClash[key] ?: newWayKind(desc).also { wayClash[key] = it; wayCheck[it] = check }
        }

        private fun newWayKind(desc: ByteArray): Int {
            val way = rc.expctxWay
            way.evaluate(false, desc)
            val cf = way.costfactor
            val grantF = way.nodeAccessGranted != 0f
            way.evaluate(true, desc)
            val cb = way.costfactor
            val grantB = way.nodeAccessGranted != 0f
            val textF = way.getKeyValueDescription(false, desc)
            val k = kinds.add(cf, cb, textF)
            if (k >= kindBits.size) { kindBits = kindBits.copyOf(kindBits.size * 2); wayCheck = wayCheck.copyOf(kindBits.size) }
            // the reverse text only where the coarse filter reads it
            val skipB = coarse.skip != null && skips(reverseText(desc, textF))
            kindBits[k] = (if (grantF) 1 else 0) or (if (grantB) 2 else 0) or
                (if (skips(textF)) 4 else 0) or (if (skipB) 8 else 0)
            return k
        }

        // BRouter's text of a description read against its direction differs from the forward text only in the first
        // lookup (BExpressionContext.decode sets lookup 0, "reversedirection", and nothing else): the reverse text is
        // that lookup's token in front of the forward text. Checked on the first kinds against BRouter's own text
        // (else every reverse text is BRouter's) – one text per way kind instead of two.
        private var reverseHead: String? = null
        private var reverseChecked = 0
        private var reverseOwn = false

        private fun reverseText(desc: ByteArray, textF: String): String {
            val head = reverseHead
            if (reverseOwn || head == null || reverseChecked < 8) {
                val real = rc.expctxWay.getKeyValueDescription(true, desc)
                stats.reverseTexts++
                if (!reverseOwn) {
                    if (head == null) {
                        if (textF.isNotEmpty() && real.length > textF.length && real.endsWith(" $textF")) {
                            reverseHead = real.substring(0, real.length - textF.length - 1)
                        } else if (textF.isNotEmpty()) reverseOwn = true
                    } else if (real != joined(head, textF)) reverseOwn = true
                    reverseChecked++
                }
                return real
            }
            return joined(head, textF)
        }

        private fun joined(head: String, textF: String) = if (textF.isEmpty()) head else "$head $textF"

        private fun skips(text: String): Boolean {
            val skip = coarse.skip ?: return false
            return skip.containsMatchIn(text) && coarse.keep?.containsMatchIn(text) != true
        }

        private fun nodeKind(desc: ByteArray): Int {
            val key = ContentKey(desc)
            return nodeByContent[key] ?: (nodeDescs.size).also { nodeDescs.add(desc); nodeByContent[key] = it }
        }

        /** BRouter's node rule (StdPath.processTargetNode): entering a node with this description is barred. */
        fun barred(nodeKind: Int, accessGranted: Boolean): Boolean {
            if (nodeKind < 0) return false
            val key = nodeKind * 2 + (if (accessGranted) 1 else 0)
            return nodeBarred.getOrPut(key) {
                rc.expctxNode.evaluate(accessGranted, nodeDescs[nodeKind])
                rc.expctxNode.initialcost >= 1_000_000f
            }
        }

        fun releaseTiles() {
            lru.clear()
            lastKey = Long.MIN_VALUE; lastTile = null
            bytes = 0
            pending.release()
            wayByHash.release()
            wayClash.clear()
        }

        fun close() {
            releaseTiles()
            for (p in files.values) p?.close()
        }
    }

    /** One whole degree of an rd5 file and its micro tile size (micro-degrees). */
    private class Degree(val file: OsmFile, val cellsize: Int)

    /** FNV-1a, 64 bit, then mixed – the way description's key. */
    private fun hash64(b: ByteArray): Long {
        var h = -0x340d631b7bdddcdbL
        for (x in b) { h = h xor (x.toLong() and 0xff); h *= 0x100000001b3L }
        h = h xor (h ushr 33); h *= -0xae502812aa7333L; h = h xor (h ushr 33)
        return if (h == Long.MIN_VALUE) 0L else h
    }

    /** A byte array compared by content (way descriptions arrive as separate copies from different tiles). */
    private class ContentKey(val b: ByteArray) {
        private val h = b.contentHashCode()
        override fun hashCode() = h
        override fun equals(other: Any?) = other is ContentKey && other.b.contentEquals(b)
    }

    // ---------------------------------------------------------------- the walk (RouteService.graph)

    private inner class Walk(val rc: RoutingContext, val tiles: Tiles, val coarse: Coarse,
                             val centreLon: Int, val centreLat: Int, val radius: Double) {
        private val ids = LongIntMap(1 shl 14)
        private val net = WayNet.Builder(tiles.kinds)
        private var expanded = BooleanArray(1 shl 14)
        private var queue = IntArray(1 shl 14)
        private var qHead = 0
        private var qTail = 0
        private val src = OsmNode()
        private val dst = OsmNode()
        // per expansion: the far nodes' stored links back to this node, used in order
        private val usedBack = LongIntMap(16)

        fun run(startPos: Long): WayNet {
            tiles.coarse = coarse
            val sLon = (startPos shr 32).toInt(); val sLat = startPos.toInt()
            val st = tiles.tile(sLon, sLat)
            val si = st?.find(sLon, sLat) ?: -1
            if (st == null || si < 0) throw ReadException("start node not in the routing data")
            val start = addNode(startPos, st.selev[si])
            push(start)
            while (qHead < qTail) expand(queue[qHead++])
            stats.wayKinds = tiles.kinds.size
            // the walk's helpers go before the network's adjacency is built (the peak of a big read is here)
            tiles.releaseTiles()
            ids.release()
            queue = IntArray(0)
            expanded = BooleanArray(0)
            return net.build(start)
        }

        private fun pos(v: Int): Long = (net.ilonOf(v).toLong() shl 32) or (net.ilatOf(v).toLong() and 0xffffffffL)

        private fun addNode(p: Long, selev: Short): Int {
            val v = net.addNode((p shr 32).toInt(), p.toInt(), selev)
            ids.put(p, v)
            if (v == expanded.size) expanded = expanded.copyOf(v * 2)
            return v
        }

        private fun push(v: Int) {
            if (qTail == queue.size) queue = queue.copyOf(queue.size * 2)
            queue[qTail++] = v
            tiles.pending(net.ilonOf(v), net.ilatOf(v), +1)
        }

        /** OsmNode.calcDistance to the start point (BRouter's CheapRuler, whole metres, at least 1). */
        private fun fromCentre(ilon: Int, ilat: Int): Int =
            maxOf(1.0, Math.round(CheapRuler.distance(ilon, ilat, centreLon, centreLat)).toDouble()).toInt()

        private fun expand(v: Int) {
            expanded[v] = true
            tiles.pending(net.ilonOf(v), net.ilatOf(v), -1)
            val p = pos(v)
            val tile = tiles.tile((p shr 32).toInt(), p.toInt()) ?: return
            val i = tile.find((p shr 32).toInt(), p.toInt())
            if (i < 0) return
            val selevV = tile.selev[i]
            val kindV = tile.nodeKind[i]
            usedBack.clear()
            val from = tile.entryOff[i]; val to = tile.entryOff[i + 1]
            for (k in from until to) {
                val tLon = tile.entLon[k]; val tLat = tile.entLat[k]
                val fwd = tile.entFwd[k]
                if (fwd >= 0) {
                    link(v, selevV, kindV, tLon, tLat, tile.fwdKind[fwd], tile.geometry(fwd), reverse = false, null, -1)
                } else {
                    backLink(v, selevV, kindV, p, tLon, tLat, onlyOne = true)
                }
            }
            // links stored at a far node whose entry here BRouter merged into another link to the same node
            for (k in from until to) backLink(v, selevV, kindV, p, tile.entLon[k], tile.entLat[k], onlyOne = false)
        }

        /** The far node's stored links to v not yet used in this expansion: the next one ([onlyOne]) or all. */
        private fun backLink(v: Int, selevV: Short, kindV: Int, p: Long, tLon: Int, tLat: Int, onlyOne: Boolean) {
            val tPos = (tLon.toLong() shl 32) or (tLat.toLong() and 0xffffffffL)
            val ft = tiles.tile(tLon, tLat) ?: return
            val j = ft.find(tLon, tLat)
            if (j < 0) return
            var used = usedBack[tPos].coerceAtLeast(0)
            var seen = 0
            for (k in ft.entryOff[j] until ft.entryOff[j + 1]) {
                val fwd = ft.entFwd[k]
                if (fwd < 0 || ft.entLon[k] != (p shr 32).toInt() || ft.entLat[k] != p.toInt()) continue
                if (seen++ < used) continue
                used++
                usedBack.put(tPos, used)
                link(v, selevV, kindV, tLon, tLat, ft.fwdKind[fwd], ft.geometry(fwd), reverse = true, ft, j)
                if (onlyOne) return
            }
        }

        /** One link seen from the expanded node v (the POC's loop body). [reverse] = stored at the far node. */
        private fun link(v: Int, selevV: Short, kindV: Int, tLon: Int, tLat: Int, kind: Int, geom: ByteArray?,
                         reverse: Boolean, farTile: RawTile?, farIndex: Int) {
            val tPos = (tLon.toLong() shl 32) or (tLat.toLong() and 0xffffffffL)
            var t = ids[tPos]
            if (t >= 0 && expanded[t]) return  // seen when t was expanded
            // the far node must be in the data (BRouter: obtainNonHollowNode)
            val ft = farTile ?: tiles.tile(tLon, tLat) ?: return
            val j = if (farTile != null) farIndex else ft.find(tLon, tLat)
            if (j < 0) return
            val kinds = tiles.kinds
            val cf = kinds.costFactor(kind, reverse)  // leaving v
            val cb = kinds.costFactor(kind, !reverse)
            val far = fromCentre(tLon, tLat) > 500
            if (far && minOf(cf, cb) >= coarse.maxCostFactor) return
            val bits = tiles.kindBits[kind]
            if (far && bits and (if (reverse) 8 else 4) != 0) return
            if (t < 0) {
                t = addNode(tPos, ft.selev[j])
                if (fromCentre(tLon, tLat) <= radius) push(t)
            }
            val grantAB = bits and (if (reverse) 2 else 1) != 0
            val grantBA = bits and (if (reverse) 1 else 2) != 0
            var flags = 0
            if (cf < 9999f) flags = flags or WayNet.WAY_AB
            if (cb < 9999f) flags = flags or WayNet.WAY_BA
            if (cf <= 9998f && !tiles.barred(ft.nodeKind[j], grantAB)) flags = flags or WayNet.ALLOWED_AB
            if (cb <= 9998f && !tiles.barred(kindV, grantBA)) flags = flags or WayNet.ALLOWED_BA
            if (reverse) flags = flags or WayNet.REVERSED
            net.beginLink(v, t, kind, flags)
            // shape: BRouter's geometry decoder, from v to t
            src.ilon = net.ilonOf(v); src.ilat = net.ilatOf(v); src.selev = selevV
            dst.ilon = tLon; dst.ilat = tLat; dst.selev = ft.selev[j]
            var len = 0.0
            var up = 0.0
            var down = 0.0
            var pl = src.ilon; var pa = src.ilat
            var pe = selevV.toInt()
            var tn = if (geom == null) null else rc.geometryDecoder.decodeGeometry(geom, src, dst, reverse)
            while (tn != null) {
                len += WayNet.metres(pl, pa, tn.ilon, tn.ilat)
                pl = tn.ilon; pa = tn.ilat
                net.shape(pl, pa)
                val e = tn.selev.toInt()
                if (e != WayNet.NO_ELE.toInt()) {
                    if (pe != WayNet.NO_ELE.toInt()) { if (e > pe) up += e - pe else down += pe - e }
                    pe = e
                }
                tn = tn.next
            }
            len += WayNet.metres(pl, pa, tLon, tLat)
            val e = dst.selev.toInt()
            if (e != WayNet.NO_ELE.toInt() && pe != WayNet.NO_ELE.toInt()) { if (e > pe) up += e - pe else down += pe - e }
            net.endLink(len.toFloat(), (up / 4.0).toFloat(), (down / 4.0).toFloat())
        }
    }

    companion object {
        /** Raw tiles kept beyond those with queued nodes (bytes): the tiles around the ring the walk works on. */
        const val DEFAULT_TILE_BUDGET = 12L shl 20

        /** BRouter's rd5 file name for a whole degree (NodesCache.fileForSegment), e.g. E5_N50. */
        fun fileName(lonDeg: Int, latDeg: Int): String {
            val lon = lonDeg - 180 - lonDeg % 5
            val lat = latDeg - 90 - latDeg % 5
            return (if (lon < 0) "W${-lon}" else "E$lon") + "_" + (if (lat < 0) "S${-lat}" else "N$lat")
        }
    }
}
