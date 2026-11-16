package com.onpath.app.suggest

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.asinh
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * What the land around a point is (the POC's `land.py`, reading the suggest files' grids): nature within 100 / 300 /
 * 900 m, built-up within 45 m, open water within 90 / 300 m (shares of the ~45 m squares, by summed-area tables), the
 * protected area a point lies in, an edge with a view ([rim]) from the heights, the nearest named place.
 *
 * One [Land] per suggestion run: like the POC it keeps the grid of the last point and uses it while the next point
 * lies inside it (a grid reaches a little beyond its cell), else it takes the grid of the point's own cell.
 */
class Land(private val files: SuggestFiles) {
    class Grid(val f: SuggestFile) {
        val w = f.w
        val h = f.h
        val east = f.west + w * f.dlon
        val south = f.north - h * f.dlat
        val sat = sat(NATURE_OR_PROTECTED)
        val satBuilt = sat(SuggestFile.BUILT)
        val satWater = sat(SuggestFile.WATER)
        val rimCache = HashMap<Int, Double>()

        private fun sat(mask: Int): IntArray {
            val w1 = w + 1
            val s = IntArray(w1 * (h + 1))
            for (r in 0 until h) {
                var run = 0
                val base = (r + 1) * w1
                val up = r * w1
                for (c in 0 until w) {
                    if (f.grid[r * w + c].toInt() and mask != 0) run++
                    s[base + c + 1] = s[up + c + 1] + run
                }
            }
            return s
        }

        fun contains(lon: Double, lat: Double) = f.west <= lon && lon < east && south < lat && lat <= f.north
    }

    private val grids = HashMap<Long, Grid?>()
    private var last: Grid? = null

    /** The grid of map cell x, y (null when the cell has no suggest file). */
    fun grid(x: Int, y: Int): Grid? {
        val key = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
        if (grids.containsKey(key)) return grids[key]
        val g = files.cell(x, y)?.let { Grid(it) }
        grids[key] = g
        return g
    }

    private fun gridAt(lon: Double, lat: Double): Grid? {
        val m = last
        if (m != null && m.contains(lon, lat)) return m
        val c = cellOf(lon, lat)
        val g = grid(c[0], c[1])
        last = g
        return g
    }

    /** land.py `around`: the shares around a point; all 0 where no grid is. */
    class Around(val n100: Double, val n300: Double, val n900: Double, val built: Double, val w90: Double,
                 val w300: Double, val area: String)

    fun around(lon: Double, lat: Double): Around {
        val m = gridAt(lon, lat) ?: return NOTHING
        val w = m.w; val h = m.h
        val c = minOf(w - 1, ((lon - m.f.west) / m.f.dlon).toInt())
        val r = minOf(h - 1, ((m.f.north - lat) / m.f.dlat).toInt())
        val w1 = w + 1
        fun share(s: IntArray, k: Int): Double {
            val c0 = maxOf(0, c - k); val c1 = minOf(w, c + k + 1); val r0 = maxOf(0, r - k); val r1 = minOf(h, r + k + 1)
            return (s[r1 * w1 + c1] - s[r0 * w1 + c1] - s[r1 * w1 + c0] + s[r0 * w1 + c0]).toDouble() / ((c1 - c0) * (r1 - r0)).toDouble()
        }
        val i = r * w + c
        val area = if (m.f.grid[i].toInt() and SuggestFile.PROTECTED != 0) m.f.labels.getOrElse(m.f.area[i].toInt() and 255) { "" } else ""
        return Around(share(m.sat, 2), share(m.sat, 6), share(m.sat, 20), share(m.satBuilt, 1), share(m.satWater, 2),
            share(m.satWater, 6), area)
    }

    /**
     * land.py `rim`: 0..1 – is the point at an edge with a view? Per direction (8) the ground 90 m … [reachM] away:
     * how much lower it lies on average (nothing below [dropFrom] metres, full from [dropFull]) × how open it is (share
     * not under trees); the best direction.
     */
    fun rim(lon: Double, lat: Double, dropFrom: Double, dropFull: Double, reachM: Double): Double {
        val m = gridAt(lon, lat) ?: return 0.0
        val H = m.f.heights ?: return 0.0
        val w = m.w; val h = m.h
        val c = minOf(w - 1, ((lon - m.f.west) / m.f.dlon).toInt())
        val r = minOf(h - 1, ((m.f.north - lat) / m.f.dlat).toInt())
        val key = r * w + c
        m.rimCache[key]?.let { return it }
        val h0 = H[key]
        var best = 0.0
        val steps = ArrayList<Double>()
        var d = 90
        while (d <= reachM.toInt()) { steps.add(d / 45.0); d += 60 }
        if (h0 != SuggestFile.NO_HEIGHT) {
            for ((dx, dy) in RIM_DIRS) {
                var sum = 0.0
                var n = 0
                var open = 0
                for (k in steps) {
                    val cc = c + kotlin.math.round(k * dx).toInt()
                    val rr = r - kotlin.math.round(k * dy).toInt()
                    if (cc in 0 until w && rr in 0 until h && H[rr * w + cc] != SuggestFile.NO_HEIGHT) {
                        sum += H[rr * w + cc]
                        n++
                        if (m.f.grid[rr * w + cc].toInt() and SuggestFile.WOOD == 0) open++
                    }
                }
                if (n >= 0.6 * steps.size) {
                    val drop = h0 - sum / n
                    best = maxOf(best, ((drop - dropFrom) / (dropFull - dropFrom)).coerceIn(0.0, 1.0) * open / n)
                }
            }
        }
        m.rimCache[key] = best
        return best
    }

    /** land.py `place_near`: the nearest named place in the point's own cell within [maxM]; "" if none. */
    fun placeNear(lon: Double, lat: Double, maxM: Double = 2500.0): String {
        val c = cellOf(lon, lat)
        val m = grid(c[0], c[1]) ?: return ""
        val kx = cos(Math.toRadians(lat))
        var bestD = -1.0
        var best = ""
        for (p in m.f.places) {
            val dx = (p.lon - lon) * kx
            val dy = p.lat - lat
            val d2 = dx * dx + dy * dy
            if (bestD < 0 || d2 < bestD) { bestD = d2; best = p.name }
        }
        return if (bestD >= 0 && sqrt(bestD) * 111320.0 <= maxM) best else ""
    }

    companion object {
        private const val NATURE_OR_PROTECTED = SuggestFile.NATURE or SuggestFile.PROTECTED
        private val NOTHING = Around(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "")
        private val RIM_DIRS = (0 until 360 step 45).map { sin(Math.toRadians(it.toDouble())) to cos(Math.toRadians(it.toDouble())) }

        /** The z10 map cell of a point (cells.py `cell_of`). */
        fun cellOf(lon: Double, lat: Double): IntArray {
            val x = ((lon + 180.0) / 360.0 * 1024).toInt()
            val y = ((1 - asinh(tan(Math.toRadians(lat))) / PI) / 2 * 1024).toInt()
            return intArrayOf(x, y)
        }

        /** The cells a circle of [reachM] around the point touches (cells.py `cells_for`), x outer, y inner. */
        fun cellsFor(lon: Double, lat: Double, reachM: Double): List<IntArray> {
            val dlat = reachM / 111320.0
            val dlon = reachM / (111320.0 * cos(Math.toRadians(lat)))
            val a = cellOf(lon - dlon, lat + dlat)
            val b = cellOf(lon + dlon, lat - dlat)
            val out = ArrayList<IntArray>()
            for (x in a[0]..b[0]) for (y in a[1]..b[1]) out.add(intArrayOf(x, y))
            return out
        }
    }
}
