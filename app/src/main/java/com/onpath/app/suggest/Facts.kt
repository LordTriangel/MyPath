package com.onpath.app.suggest


/**
 * Level 1 – what a way is (the POC's `facts.py`): facts per link of the [WayNet], the same everywhere, from the way
 * tags ([WayKinds]) and the suggest files' grids ([Land]) and named things ([Named]):
 * ok (the profile allows it), ground 0 paved · 1 firm · 2 natural · 3 rough, traffic 0..1 (a road with cars, or a
 * separate way right beside one – found by geometry), built (in settlement land), green (100 m) and depth (300 / 900 m),
 * water (1 big, 0.5 small), rim (edge with a view), sight nearby, signed rank, rail trail, ferry, noise, width (0 a
 * narrow path – path / footway / steps / bridleway, not paved · 1 a track, grade 1 / 2 or none · 2 a rough track, grade
 * 3–5 · 3 a road or a wide made way: what quality trails call the way's format), the named protected area and the named
 * things it lies on.
 *
 * Lengths and heights as the POC sees them (0.1 m; its route service rounds them so) and cost factors as their
 * shortest decimal text – the port computes on the very same numbers. The facts with few distinct values per network
 * are kept as [CodedDoubles] (2 bytes per link instead of 8, the very same numbers).
 */
class Facts(val net: WayNet, index: Named.Index, K: SuggestSettings.J, bike: Boolean, land: Land) {
    val n = net.linkCount
    val ln = DoubleArray(n) { Math.round(net.linkLen[it] * 10.0) / 10.0 }
    val ok = BooleanArray(n) { net.allowed(it) }
    val costfactor = CodedDoubles(n)
    val ground = SmallInts(n)
    val traffic = CodedDoubles(n)
    val built = BooleanArray(n)
    val green = CodedDoubles(n)
    val depth = CodedDoubles(n)
    val water = CodedDoubles(n)
    val signed = CodedDoubles(n)
    val rail = BooleanArray(n)
    val ferry = BooleanArray(n)
    val noise = CodedDoubles(n)
    val rim = CodedDoubles(n)
    val sight = BooleanArray(n)
    /** 0 path · 1 track · 2 rough track · 3 road / wide (facts.py `of_tags`) */
    val width = SmallInts(n, 3)
    val area = SmallInts(n)
    val named: Array<IntArray> = Array(n) { EMPTY }
    /** names of the protected areas; 0 = none */
    val areas = arrayListOf("")
    val things: List<Thing> = index.things

    /** Per way kind: road weight, ground, signed rank, rail, ferry, noise, river class, tunnel, width. */
    private class Tags(val road: Double, val ground: Int, val signed: Double, val rail: Boolean, val ferry: Boolean,
                       val noise: Double, val river: Int, val tunnel: Boolean, val width: Int)

    init {
        val kinds = net.kinds
        val perKind = arrayOfNulls<Tags>(kinds.size)
        fun tagsOf(k: Int): Tags = perKind[k] ?: ofTags(kinds, k, K, bike).also { perKind[k] = it }
        val besideFrom = K["beside_from"].d
        val roads = RoadGrid()
        for (e in 0 until n) {
            val t = tagsOf(net.linkKind[e])
            if (t.road >= besideFrom && !t.tunnel) roads.add(geom(e), t.road)
        }
        val beside = K["beside"].d
        val rimK = K["rim"]
        val rimFrom = rimK[0].d; val rimFull = rimK[1].d; val rimReach = rimK[2].d
        val bigWater = K["big_water"].d
        // the land grids, grid by grid: per link the grid it is read in (Land.gridAt in link order, as the POC moves
        // from grid to grid), then each grid's links together – only one grid's tables are in memory at a time
        val gridNo = IntArray(n) { -1 }
        val grids = ArrayList<Land.Grid>()
        val gridIds = java.util.IdentityHashMap<Land.Grid, Int>()
        for (e in 0 until n) {
            if (!ok[e]) continue
            val mid = mid(e)
            val g = land.gridAt(mid[0], mid[1]) ?: continue
            gridNo[e] = gridIds.getOrPut(g) { grids.add(g); grids.size - 1 }
        }
        val wet = ByteArray(n)  // bit 0: water within 90 m > 4 %, bit 1: within 300 m ≥ big_water
        val areaName = arrayOfNulls<String>(n)
        val byGrid = IntArray(grids.size + 1)
        for (e in 0 until n) if (gridNo[e] >= 0) byGrid[gridNo[e] + 1]++
        for (i in 0 until grids.size) byGrid[i + 1] += byGrid[i]
        val order = IntArray(byGrid[grids.size])
        val fill = byGrid.copyOf(grids.size)
        for (e in 0 until n) if (gridNo[e] >= 0) order[fill[gridNo[e]]++] = e
        for (i in 0 until grids.size) {
            val g = grids[i]
            for (k in byGrid[i] until byGrid[i + 1]) {
                val e = order[k]
                val mid = mid(e)
                val around = land.around(g, mid[0], mid[1])
                built[e] = around.built >= 0.5
                green[e] = around.n100
                depth[e] = 0.5 * (around.n300 + around.n900)
                rim[e] = land.rim(g, mid[0], mid[1], rimFrom, rimFull, rimReach)
                wet[e] = ((if (around.w90 > 0.04) 1 else 0) or (if (around.w300 >= bigWater) 2 else 0)).toByte()
                if (around.area.isNotEmpty()) areaName[e] = around.area
            }
            g.release()
        }
        val areaIndex = HashMap<String, Int>()
        var lastHits = EMPTY
        for (e in 0 until n) {
            val a = net.linkA[e]; val b = net.linkB[e]
            costfactor[e] = minOf(cf(net.costFactor(e, a)), cf(net.costFactor(e, b)), 5.0)
            if (!ok[e]) continue
            val t = tagsOf(net.linkKind[e])
            ground[e] = t.ground; rail[e] = t.rail; ferry[e] = t.ferry; noise[e] = t.noise; width[e] = t.width
            var road = t.road
            var signedE = t.signed
            val mid = mid(e)
            if (road == 0.0 && ln[e] >= 25.0 && roads.isNotEmpty()) road = beside * roads.beside(geom(e))
            traffic[e] = road
            val w = wet[e].toInt()
            var bank = w and 1 != 0
            var hits = index.at(mid[0], mid[1], 1)
            for (i in hits) {
                val th = things[i]
                if (th.kind == "route" && (road < besideFrom || signedE != 0.0)) signedE = maxOf(signedE, th.rank)
                bank = bank || th.kind == "lake"
                sight[e] = sight[e] || th.kind == "sight"
            }
            water[e] = if (bank && w and 2 != 0) 1.0 else if (bank || (t.river >= 4 && green[e] >= 0.2)) 0.5 else 0.0
            signed[e] = signedE
            // one array for the many links with the same things as the link before (most have none)
            if (hits.isEmpty()) hits = EMPTY else if (hits.contentEquals(lastHits)) hits = lastHits else lastHits = hits
            named[e] = hits
            val nm = areaName[e]
            if (nm != null) {
                area[e] = areaIndex.getOrPut(nm) { areas.add(nm); areas.size - 1 }
            }
        }
    }

    /** The shape of link [e] as lon / lat pairs, from node [from] (default its first node) – net.py `geom`. */
    fun geom(e: Int, from: Int = -1): DoubleArray = geomOf(net, e, from)

    /** net.py `mid`: the middle shape point, or the middle between the two nodes. */
    fun mid(e: Int): DoubleArray {
        val k = net.shapePoints(e)
        if (k > 0) {
            val p = net.geoOff[e] + k / 2
            return doubleArrayOf((net.geo[2 * p] - 180_000_000) / 1e6, (net.geo[2 * p + 1] - 90_000_000) / 1e6)
        }
        val a = net.linkA[e]; val b = net.linkB[e]
        return doubleArrayOf((net.lon(a) + net.lon(b)) / 2, (net.lat(a) + net.lat(b)) / 2)
    }

    /** Where roads with real car traffic run (squares of ~12 m), to find a separate way *beside* one by geometry. */
    class RoadGrid {
        /** square → the heaviest road's weight in it (no object per square: a bike network has a few hundred thousand) */
        private val cells = LongDoubleMap(1 shl 12)

        fun isNotEmpty() = cells.size > 0

        private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

        fun add(pts: DoubleArray, w: Double) {
            for (j in 0 until pts.size / 2 - 1) {
                val a0 = pts[2 * j]; val a1 = pts[2 * j + 1]; val b0 = pts[2 * j + 2]; val b1 = pts[2 * j + 3]
                val n = maxOf(1, (SuggestGeo.dist(a0, a1, b0, b1) / 10.0).toInt())
                for (k in 0..n) {
                    val kk = key(((a0 + (b0 - a0) * k / n) / CX).toInt(), ((a1 + (b1 - a1) * k / n) / CY).toInt())
                    if (cells[kk] < w) cells.put(kk, w)
                }
            }
        }

        /** Weight of the road a way runs beside: found at two of its points at 1/4, 1/2, 3/4 (a crossing has one). */
        fun beside(pts: DoubleArray): Double {
            val segs = pts.size / 2 - 1
            var total = 0.0
            for (j in 0 until segs) total += SuggestGeo.dist(pts[2 * j], pts[2 * j + 1], pts[2 * j + 2], pts[2 * j + 3])
            val hits = ArrayList<Double>()
            for (f in doubleArrayOf(0.25, 0.5, 0.75)) {
                val want = f * total
                var acc = 0.0
                for (j in 0 until segs) {
                    val a0 = pts[2 * j]; val a1 = pts[2 * j + 1]; val b0 = pts[2 * j + 2]; val b1 = pts[2 * j + 3]
                    val d = SuggestGeo.dist(a0, a1, b0, b1)
                    if (acc + d >= want && d > 0) {
                        val u = (want - acc) / d
                        val cx = ((a0 + (b0 - a0) * u) / CX).toInt(); val cy = ((a1 + (b1 - a1) * u) / CY).toInt()
                        var best = 0.0
                        for (i in -1..1) for (jj in -1..1) best = maxOf(best, cells[key(cx + i, cy + jj)])
                        hits.add(best)
                        break
                    }
                    acc += d
                }
            }
            hits.sort()
            return if (hits.size >= 2) hits[hits.size - 2] else 0.0
        }

        companion object {
            const val CX = 0.00018
            const val CY = 0.00011
        }
    }

    companion object {
        private val EMPTY = IntArray(0)
        private val ROADS = setOf("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "living_street", "service")
        private val PAVED = setOf("asphalt", "paved", "concrete", "concrete:plates", "concrete:lanes", "paving_stones")
        private val FIRM = setOf("compacted", "fine_gravel", "gravel", "pebblestone", "sett", "cobblestone", "unhewn_cobblestone", "metal", "wood", "grass_paver")
        private val ROUGH = setOf("rock", "roots", "stone", "scree", "mud")
        private val PATHS = setOf("path", "footway", "steps", "bridleway")
        private val ROUGH_TRACK = setOf("grade3", "grade4", "grade5")

        /** A cost factor as the POC got it: the float's shortest decimal text. */
        private fun cf(f: Float): Double = f.toString().toDouble()

        fun geomOf(net: WayNet, e: Int, from: Int = -1): DoubleArray {
            val a = net.linkA[e]; val b = net.linkB[e]
            val k = net.shapePoints(e)
            val out = DoubleArray(2 * (k + 2))
            out[0] = net.lon(a); out[1] = net.lat(a)
            for (j in 0 until k) {
                val p = net.geoOff[e] + j
                out[2 + 2 * j] = (net.geo[2 * p] - 180_000_000) / 1e6
                out[3 + 2 * j] = (net.geo[2 * p + 1] - 90_000_000) / 1e6
            }
            out[2 * k + 2] = net.lon(b); out[2 * k + 3] = net.lat(b)
            if (from == b && a != b) {
                val r = DoubleArray(out.size)
                val m = out.size / 2
                for (j in 0 until m) { r[2 * j] = out[2 * (m - 1 - j)]; r[2 * j + 1] = out[2 * (m - 1 - j) + 1] }
                return r
            }
            return out
        }

        /** facts.py `of_tags`. */
        private fun ofTags(kinds: WayKinds, k: Int, K: SuggestSettings.J, bike: Boolean): Tags {
            fun tag(key: Int) = kinds.tag(k, key) ?: ""
            val hw = tag(WayKinds.HIGHWAY).replace("_link", "")
            var road = if (hw in ROADS) K["road"][hw].let { if (it.isNull) 0.0 else it.d } else 0.0
            if (road != 0.0 && tag(WayKinds.MAXSPEED) in setOf("10", "20", "30", "walk")) road *= K["calm"].d
            val surf = tag(WayKinds.SURFACE); val tt = tag(WayKinds.TRACKTYPE)
            val sac = tag(WayKinds.SAC_SCALE)
            val mtb = kinds.tag(k, WayKinds.MTB_SCALE) ?: "0"
            val demanding = sac !in setOf("", "hiking", "mountain_hiking") || (mtb.take(1).let { it.isEmpty() || it in setOf("2", "3", "4", "5", "6") })
            val ground = when {
                surf in ROUGH || demanding || tag(WayKinds.SMOOTHNESS) in setOf("horrible", "very_horrible", "impassable") -> 3
                surf.isNotEmpty() -> if (surf in PAVED) 0 else if (surf in FIRM) 1 else 2
                tt.isNotEmpty() -> if (tt == "grade1") 0 else if (tt == "grade2") 1 else 2
                else -> if (hw in ROADS || hw in setOf("cycleway", "pedestrian", "steps")) 0 else if (hw == "track") 1 else 2
            }
            var signed = 0.0
            var any = false
            val ranks = K["rank"]
            val bits = kinds.routes(k)
            for (bit in 0 until kinds.routeKeyCount) {
                if (bits and (1 shl bit) == 0) continue
                val key = kinds.routeKey(bit)
                val fits = if (bike) key.startsWith("route_bicycle_") else key.startsWith("route_hiking_") || key.startsWith("route_foot_")
                if (!fits) continue
                val c = key.substringAfterLast('_').take(1)
                val r = if (ranks.has(c)) ranks[c].d else ranks["l"].d
                signed = if (any) maxOf(signed, r) else r
                any = true
            }
            val rail = tag(WayKinds.RAILWAY) == "abandoned" && hw in setOf("path", "cycleway", "track") &&
                (kinds.tag(k, WayKinds.BICYCLE) ?: "yes") != "no"
            fun num(key: Int) = tag(key).toIntOrNull() ?: 0
            val width = if (hw in PATHS && ground > 0) 0 else if (hw == "track") (if (tt in ROUGH_TRACK) 2 else 1) else 3
            return Tags(road, ground, signed, rail, tag(WayKinds.ROUTE) == "ferry", num(WayKinds.NOISE_CLASS) / 6.0,
                num(WayKinds.RIVER_CLASS), tag(WayKinds.TUNNEL) == "yes", width)
        }
    }
}

/**
 * A double per link where a network has few distinct values (a road weight, a share of a small square, a rank …):
 * a 2-byte code per link and each distinct value once – [get] gives back exactly the value [set] was given. More than
 * 65 535 distinct values: it turns into a plain array (still exact).
 */
class CodedDoubles(val size: Int) {
    private var codes = ShortArray(size)
    private var plain: DoubleArray? = null
    private var values = DoubleArray(16)
    private var count = 1  // code 0 = 0.0, the start value of every link
    private val codeOf = LongIntMap(64)

    init { codeOf.put(key(0.0), 0) }

    operator fun get(e: Int): Double {
        val p = plain
        return if (p != null) p[e] else values[codes[e].toInt() and 0xffff]
    }

    operator fun set(e: Int, value: Double) {
        val p = plain
        if (p != null) { p[e] = value; return }
        val k = key(value)
        var c = if (k == Long.MIN_VALUE) -1 else codeOf[k]
        if (c < 0) {
            if (count > 0xffff || k == Long.MIN_VALUE) {  // too many values (or the one bit pattern the map cannot hold)
                val all = DoubleArray(size) { values[codes[it].toInt() and 0xffff] }
                all[e] = value
                plain = all
                codes = ShortArray(0)
                return
            }
            c = count++
            if (c == values.size) values = values.copyOf(c * 2)
            values[c] = value
            codeOf.put(k, c)
        }
        codes[e] = c.toShort()
    }

    /** the value's bits, shifted by one (LongIntMap cannot hold Long.MIN_VALUE, which is −0.0) */
    private fun key(value: Double): Long = java.lang.Double.doubleToRawLongBits(value) + 1
}

/** A small int per link (a class 0..3, an area's number): one byte per link while every value fits 0..255, else a
 * plain int array (exact either way). */
class SmallInts(val size: Int, initial: Int = 0) {
    private var bytes: ByteArray? = ByteArray(size).also { if (initial != 0) it.fill(initial.toByte()) }
    private var ints: IntArray? = null

    operator fun get(e: Int): Int {
        val b = bytes
        return if (b != null) b[e].toInt() and 0xff else ints!![e]
    }

    operator fun set(e: Int, value: Int) {
        val b = bytes
        if (b == null) { ints!![e] = value; return }
        if (value in 0..255) { b[e] = value.toByte(); return }
        ints = IntArray(size) { b[it].toInt() and 0xff }.also { it[e] = value }
        bytes = null
    }
}
