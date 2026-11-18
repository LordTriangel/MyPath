package com.onpath.app.suggest


/**
 * Named things around a start from the suggest files (the POC's `named.py`): known signed routes (by label, joined
 * across cells), lakes, sights – each with dense points every ~25 m within reach –, the aims a loop can turn at (kind
 * "aim": [what] = peak / hut / restaurant / cafe / spring / museum, [ele] its height or 0; content 2 of the files, an
 * older file has none) and an [Index] that says which things a point lies on (aims are not in it: an aim is a turning
 * point, [Aims], not a fact of the ways near it).
 */
class Thing(val kind: String, val name: String, val rank: Double, val dense: DoubleArray, val what: String = "", val ele: Int = 0) {
    val points: Int get() = dense.size / 2
}

object Named {
    private val NET = mapOf(4 to "i", 3 to "n", 2 to "r", 1 to "l")
    val SIGHTS = mapOf("viewpoint" to "Aussichtspunkt", "castle" to "Burg", "ruins" to "Ruine", "chapel" to "Kapelle",
        "cave" to "Höhle", "waterfall" to "Wasserfall")
    private val NOT_A_LAKE = Regex("becken|filter|klär|fang$|hafen", RegexOption.IGNORE_CASE)
    private val BRACKETS = Regex("""^\[[^\]]*\]\s*|\s*\[[^\]]*\]\s*$""")
    private val PART = Regex("""\s+-\s+(Teilstrecke|Verbindung|Abschnitt|Etappe|Teil|Variante)\b.*$|\s*\((West|Ost|Nord|Süd|Mitte)\)$""")

    /** build_suggest.py `label` without a ref: one label per route whatever its pieces are called. */
    fun label(name: String): String = PART.replace(BRACKETS.replace(name, "").trim(), "")

    /** named.py `_dense`: points every ~[step] metres along the lines (each line's own last point too). */
    fun dense(lines: List<DoubleArray>, step: Double = 25.0): DoubleArray {
        val out = DoubleList()
        for (ln in lines) {
            val n = ln.size / 2
            if (n == 0) continue
            for (j in 0 until n - 1) {
                val a0 = ln[2 * j]; val a1 = ln[2 * j + 1]; val b0 = ln[2 * j + 2]; val b1 = ln[2 * j + 3]
                val k = maxOf(1, (SuggestGeo.dist(a0, a1, b0, b1) / step).toInt())
                for (i in 0 until k) out.add(a0 + (b0 - a0) * i / k, a1 + (b1 - a1) * i / k)
            }
            out.add(ln[2 * n - 2], ln[2 * n - 1])
        }
        return out.toArray()
    }

    private class Group(var net: Int = 0, val lines: ArrayList<DoubleArray> = ArrayList(), var trail: Boolean = true)

    /** named.py `load`: the named things within [reachM] of the start. [ranks] = settings facts.rank. */
    fun load(files: SuggestFiles, lon: Double, lat: Double, reachM: Double, bike: Boolean, ranks: SuggestSettings.J): List<Thing> {
        val routes = LinkedHashMap<String, Group>()
        val lakes = LinkedHashMap<String, ArrayList<DoubleArray>>()
        val sights = ArrayList<SuggestFile.Sight>()
        val aims = ArrayList<SuggestFile.Aim>()
        for (c in Land.cellsFor(lon, lat, reachM)) {
            val f = files.cell(c[0], c[1]) ?: continue
            for (r in f.routes) {
                if (r.kind != (if (bike) "bike" else "hike")) continue
                val g = routes.getOrPut(label(r.label)) { Group() }
                g.net = maxOf(g.net, r.net)
                g.trail = g.trail && r.trail
                g.lines.addAll(r.lines)
            }
            for (lk in f.lakes) lakes.getOrPut(lk.name) { ArrayList() }.addAll(lk.lines)
            sights.addAll(f.sights)
            f.aims?.let { aims.addAll(it) }
        }
        val real = routes.filter { !it.value.trail }.keys.map { it.lowercase() }
        for (k in routes.filter { it.value.trail }.keys.toList()) {
            val low = k.lowercase()
            if (real.any { low in it }) routes.remove(k)
        }
        val out = ArrayList<Thing>()
        for ((name, g) in routes) {
            val near = near(dense(g.lines), lon, lat, reachM)
            if (near.size / 2 * 25.0 >= (if (g.trail) 1000.0 else 25.0)) {
                out.add(Thing("route", name, ranks[NET[g.net] ?: "l"].d, near))
            }
        }
        for ((name, lines) in lakes) {
            val near = near(dense(lines), lon, lat, reachM)
            if (near.size / 2 * 25.0 >= 600.0 && !NOT_A_LAKE.containsMatchIn(name)) out.add(Thing("lake", name, 1.0, near))
        }
        for (s in sights) {
            if (SuggestGeo.dist(lon, lat, s.lon, s.lat) <= reachM) {
                val what = SIGHTS.getValue(s.kind)
                out.add(Thing("sight", s.name.ifEmpty { what }, 1.0, doubleArrayOf(s.lon, s.lat), what))
            }
        }
        for (a in aims) {
            if (SuggestGeo.dist(lon, lat, a.lon, a.lat) <= reachM) out.add(Thing("aim", a.name, 1.0, doubleArrayOf(a.lon, a.lat), a.kind, a.ele))
        }
        return out
    }

    private fun near(pts: DoubleArray, lon: Double, lat: Double, reachM: Double): DoubleArray {
        val out = DoubleList()
        for (i in 0 until pts.size / 2) if (SuggestGeo.dist(lon, lat, pts[2 * i], pts[2 * i + 1]) <= reachM) out.add(pts[2 * i], pts[2 * i + 1])
        return out.toArray()
    }

    /**
     * named.py `Index`: which named things a point lies on – a grid of the things' dense points (cells of ~30 m);
     * lakes reach two cells further (a shore path often runs 50–100 m from the water).
     */
    class Index(val things: List<Thing>) {
        // cell → its things, in the order they were added, each once: cell c's are items[start[c] until start[c + 1]]
        // (plain arrays – a bike wish has ~50 000 cells; the cell's set is made from them when asked, the same set)
        private val cellOf = LongIntMap(1 shl 12)
        private val start: IntArray
        private val items: IntArray

        init {
            // a thing adds itself to a cell for each of its points there: all its adds come before the next thing's, so
            // "already in" = "the cell's last thing is this one"
            val last = IntList(); val count = IntList()
            fun visit(fill: ((Int, Int) -> Unit)?) {
                for ((i, a) in things.withIndex()) {
                    if (a.kind == "aim") continue  // an aim is a turning point (Aims), not a fact of the ways near it
                    val pad = if (a.kind == "lake") 2 else 0
                    for (k in 0 until a.points) {
                        val cx = (a.dense[2 * k] / CX).toInt(); val cy = (a.dense[2 * k + 1] / CY).toInt()
                        for (dx in -pad..pad) for (dy in -pad..pad) {
                            val key = key(cx + dx, cy + dy)
                            var c = cellOf[key]
                            if (c < 0) { c = last.size; cellOf.put(key, c); last.add(-1); count.add(0) }
                            if (last[c] == i) continue
                            last[c] = i
                            if (fill == null) count[c] = count[c] + 1 else fill(c, i)
                        }
                    }
                }
            }
            visit(null)
            start = IntArray(count.size + 1)
            for (c in 0 until count.size) start[c + 1] = start[c] + count[c]
            items = IntArray(start[count.size])
            val at = start.copyOf(count.size)
            for (c in 0 until last.size) last[c] = -1
            visit { c, i -> items[at[c]++] = i }
        }

        /** The things within [reach] cells of the point, in the POC's order. */
        fun at(lon: Double, lat: Double, reach: Int = 1): IntArray {
            val cx = (lon / CX).toInt(); val cy = (lat / CY).toInt()
            val hit = PyIntSet()
            for (dx in -reach..reach) for (dy in -reach..reach) {
                val c = cellOf[key(cx + dx, cy + dy)]
                if (c < 0) continue
                val cell = PyIntSet()  // the cell's set as it was built: the same adds in the same order
                for (j in start[c] until start[c + 1]) cell.add(items[j])
                hit.addAll(cell)
            }
            return hit.toIntArray()
        }

        private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

        companion object {
            const val CX = 0.00045
            const val CY = 0.00028
        }
    }
}

/** A growing list of doubles (lon / lat pairs). */
class DoubleList(capacity: Int = 64) {
    private var a = DoubleArray(capacity)
    var size = 0; private set
    fun add(x: Double) { if (size == a.size) a = a.copyOf(a.size * 2 + 2); a[size++] = x }
    fun add(x: Double, y: Double) { add(x); add(y) }
    operator fun get(i: Int) = a[i]
    fun toArray(): DoubleArray = a.copyOf(size)
}

/** A growing list of ints. */
class IntList(capacity: Int = 16) {
    private var a = IntArray(capacity)
    var size = 0; private set
    fun add(x: Int) { if (size == a.size) a = a.copyOf(a.size * 2 + 2); a[size++] = x }
    operator fun get(i: Int) = a[i]
    operator fun set(i: Int, x: Int) { a[i] = x }
    fun toArray(): IntArray = a.copyOf(size)
}
