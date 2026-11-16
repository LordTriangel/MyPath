package com.onpath.app.suggest

import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * The suggest file of a map cell (`suggest.bin`, docs/design/tour-suggestions.md §4a): what the tour suggestions read
 * instead of map tiles and terrain – the ~45 m land grid, protected-area names, heights, place names, signed routes,
 * lakes, sights and the signed loops in walking order. The format is defined in `tools/suggest_file.py` (writer +
 * Python reader); this is its Kotlin reader. Needs an inflater and a byte buffer, nothing else (shared with the web
 * planner: TeaVM's classlib has java.util.zip.Inflater).
 *
 * Lines are kept as DoubleArray lon, lat pairs (degrees = file integers / 10⁶, as the Python reader divides).
 */
class SuggestFile(
    val version: Int,
    val content: Int,
    val flags: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val west: Double,
    val north: Double,
    val dlon: Double,
    val dlat: Double,
    val osmDate: String,
    /** w × h land bits per square, row by row from the north-west corner ([NATURE] …). */
    val grid: ByteArray,
    /** w × h protected-area number per square (index into [labels]). */
    val area: ByteArray,
    val labels: List<String>,
    /** w × h heights in metres, [NO_HEIGHT] = unknown; null when the file has none. */
    val heights: ShortArray?,
    val places: List<PlaceName>,
    val routes: List<Route>,
    val lakes: List<Lake>,
    val sights: List<Sight>,
    /** null = the file was built without the OSM cut (no loops known), empty = none in this cell. */
    val loops: List<Loop>?,
) {
    class PlaceName(val name: String, val lon: Double, val lat: Double, val cls: String)
    class Route(val kind: String, val net: Int, val trail: Boolean, val label: String, val lines: List<DoubleArray>)
    class Lake(val name: String, val lines: List<DoubleArray>)
    class Sight(val kind: String, val name: String, val lon: Double, val lat: Double)
    class Loop(
        val id: Long, val kind: String, val t: Int,
        /** metres from the loop's first point to the nearest parking place, null = none within 300 m */
        val parkingM: Int?,
        val m: Double,
        /** metres up from the cell's heights, null = unknown (the loop leaves the cell) */
        val ascent: Int?,
        val name: String, val ref: String, val network: String,
        /** the closed loop in walking order from the point nearest a parking place */
        val line: DoubleArray,
    )

    class NotASuggestFile(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        const val FORMAT_VERSION = 1
        const val CONTENT = 1
        const val NO_HEIGHT: Short = Short.MIN_VALUE
        const val NATURE = 1
        const val PROTECTED = 2
        const val BUILT = 4
        const val WATER = 8
        const val WOOD = 16
        const val HAS_HEIGHTS = 1
        const val HAS_ROUTELINES = 2
        const val HAS_LOOPS = 4
        const val NAME = "suggest.bin"
        val PLACE_CLASSES = listOf("city", "town", "village", "suburb", "hamlet", "quarter", "neighbourhood")
        val SIGHT_KINDS = listOf("viewpoint", "castle", "ruins", "chapel", "cave", "waterfall")
        val ROUTE_KINDS = listOf("hike", "bike", "mtb")
        val LOOP_KINDS = listOf("hike", "bicycle", "mtb")

        /** The format version of a file's first 8 bytes, null when it is no suggest file. */
        fun versionOf(head: ByteArray): Int? =
            if (head.size >= 8 && head[0] == 'O'.code.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'S'.code.toByte() && head[3] == 'G'.code.toByte()) (head[4].toInt() and 255) or ((head[5].toInt() and 255) shl 8)
            else null

        /** The file's bytes → its content. Throws [NotASuggestFile] for anything else or a newer format. */
        fun read(data: ByteArray): SuggestFile {
            val version = versionOf(data) ?: throw NotASuggestFile("no suggest file")
            if (version > FORMAT_VERSION) throw NotASuggestFile("suggest file of a newer format ($version > $FORMAT_VERSION)")
            val body = inflate(data)
            return try {
                parse(version, body)
            } catch (e: IndexOutOfBoundsException) {
                throw NotASuggestFile("suggest file cut short", e)
            }
        }

        private fun inflate(data: ByteArray): ByteArray {
            val inf = Inflater()
            try {
                inf.setInput(data, 8, data.size - 8)
                var out = ByteArray(maxOf(1 shl 16, data.size * 4))
                var n = 0
                while (!inf.finished()) {
                    if (n == out.size) out = out.copyOf(out.size * 2)
                    val k = inf.inflate(out, n, out.size - n)
                    if (k == 0 && (inf.needsInput() || inf.needsDictionary())) throw NotASuggestFile("suggest file cut short")
                    n += k
                }
                return out.copyOf(n)
            } catch (e: DataFormatException) {
                throw NotASuggestFile("suggest file body broken", e)
            } finally {
                inf.end()
            }
        }

        private fun parse(version: Int, body: ByteArray): SuggestFile {
            var head: R? = null
            var grid: ByteArray? = null
            var area: ByteArray? = null
            var labels: List<String> = listOf("")
            var heightsAt = -1
            var heightsLen = 0
            val places = ArrayList<PlaceName>()
            val routes = ArrayList<Route>()
            val lakes = ArrayList<Lake>()
            val sights = ArrayList<Sight>()
            var loops: ArrayList<Loop>? = null
            var x = 0; var y = 0; var w = 0; var h = 0; var content = 0; var flags = 0
            var west = 0.0; var north = 0.0; var dlon = 0.0; var dlat = 0.0; var osmDate = ""
            var i = 0
            while (i + 8 <= body.size) {
                val tag = String(CharArray(4) { (body[i + it].toInt() and 255).toChar() })
                val n = R(body, i + 4).u32()
                val start = i + 8
                val end = start + n
                if (end > body.size) throw NotASuggestFile("section $tag cut short")
                i = end
                val r = R(body, start)
                when (tag) {
                    "HEAD" -> {
                        x = r.u16(); y = r.u16(); w = r.u16(); h = r.u16()
                        west = r.f64(); north = r.f64(); dlon = r.f64(); dlat = r.f64()
                        content = r.u16(); flags = r.u16()
                        osmDate = r.text()
                        head = r
                    }
                    "GRID" -> grid = body.copyOfRange(start, end)
                    "AREA" -> {
                        val size = w * h
                        area = body.copyOfRange(start, start + size)
                        r.i = start + size
                        labels = List(r.uv().toInt()) { r.text() }
                    }
                    "HGHT" -> if (body[start].toInt() == 1) { heightsAt = start + 1; heightsLen = n - 1 }
                    "PLAC" -> repeat(r.uv().toInt()) {
                        val nm = r.text()
                        val lon = r.zz(); val lat = r.zz()
                        places.add(PlaceName(nm, lon / E6, lat / E6, PLACE_CLASSES[r.u8()]))
                    }
                    "ROUT" -> repeat(r.uv().toInt()) {
                        val kind = ROUTE_KINDS[r.u8()]
                        val net = r.u8()
                        val fl = r.u8()
                        val label = r.text()
                        routes.add(Route(kind, net, fl and 1 != 0, label, r.lines()))
                    }
                    "LAKE" -> repeat(r.uv().toInt()) { lakes.add(Lake(r.text(), r.lines())) }
                    "SGHT" -> repeat(r.uv().toInt()) {
                        val kind = SIGHT_KINDS[r.u8()]
                        val nm = r.text()
                        val lon = r.zz(); val lat = r.zz()
                        sights.add(Sight(kind, nm, lon / E6, lat / E6))
                    }
                    "LOOP" -> {
                        val out = ArrayList<Loop>()
                        repeat(r.uv().toInt()) {
                            val id = r.uv()
                            val kind = LOOP_KINDS[r.u8()]
                            val t = r.u8()
                            val park = r.uv().toInt()
                            val m = r.uv() / 100.0
                            val up = r.uv().toInt()
                            out.add(Loop(id, kind, t, if (park > 0) park - 1 else null, m, if (up > 0) up - 1 else null,
                                r.text(), r.text(), r.text(), r.line()))
                        }
                        loops = out
                    }
                    else -> Unit  // a section this reader does not know
                }
            }
            if (head == null || grid == null || area == null) throw NotASuggestFile("suggest file without a grid")
            if (grid.size != w * h) throw NotASuggestFile("grid size")
            val heights = if (heightsAt >= 0 && heightsLen >= 2 * w * h) heights(body, heightsAt, w, h) else null
            return SuggestFile(version, content, flags, x, y, w, h, west, north, dlon, dlat, osmDate, grid, area, labels,
                heights, places, routes, lakes, sights, loops)
        }

        /** HGHT coding 1: per square the difference to left + above − above-left, 16-bit, zig-zag; low then high bytes. */
        private fun heights(b: ByteArray, at: Int, w: Int, h: Int): ShortArray {
            val size = w * h
            val out = ShortArray(size)
            for (row in 0 until h) {
                val base = row * w
                var left = 0
                var corner = 0
                for (c in 0 until w) {
                    val z = (b[at + base + c].toInt() and 255) or ((b[at + size + base + c].toInt() and 255) shl 8)
                    val up = if (row > 0) out[base - w + c].toInt() else 0
                    val d = (z ushr 1) xor -(z and 1)
                    val v = (((left + up - corner + d + 32768) and 0xFFFF) - 32768)
                    out[base + c] = v.toShort()
                    left = v; corner = up
                }
            }
            return out
        }

        private const val E6 = 1000000.0
    }

    private class R(val b: ByteArray, var i: Int) {
        fun u8(): Int = b[i++].toInt() and 255
        fun u16(): Int { val v = (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8); i += 2; return v }
        fun u32(): Int { var v = 0; for (k in 0 until 4) v = v or ((b[i + k].toInt() and 255) shl (8 * k)); i += 4; return v }
        fun f64(): Double { var v = 0L; for (k in 0 until 8) v = v or ((b[i + k].toLong() and 255) shl (8 * k)); i += 8; return Double.fromBits(v) }
        fun uv(): Long {
            var v = 0L
            var s = 0
            while (true) {
                val c = b[i++].toInt() and 255
                v = v or ((c and 127).toLong() shl s)
                if (c < 128) return v
                s += 7
            }
        }
        fun zz(): Long { val v = uv(); return (v ushr 1) xor -(v and 1) }
        fun text(): String { val n = uv().toInt(); val s = String(b, i, n, Charsets.UTF_8); i += n; return s }
        fun line(): DoubleArray {
            val n = uv().toInt()
            val out = DoubleArray(2 * n)
            var lon = 0L; var lat = 0L
            for (k in 0 until n) {
                lon += zz(); lat += zz()
                out[2 * k] = lon / E6; out[2 * k + 1] = lat / E6
            }
            return out
        }
        fun lines(): List<DoubleArray> = List(uv().toInt()) { line() }
    }
}

/** Where the tour suggestions get the suggest file of a map cell (z10 x, y); null = the cell has none (yet). */
fun interface SuggestFiles {
    fun cell(x: Int, y: Int): SuggestFile?
}
