// Stage 0 of the multi-day tours (docs/design/multi-day-tours.md §6): a ~560 km bike tour (München → Venezia) as a
// plan – TrackToPlan, the plan code's size, the first routing of the whole plan, the map cells along the corridor.
// Run (the GPX stays outside the repo; e.g. muenchen-venezia.info "Muenchen-Venezia_west.gpx"):
//   TRACK_GPX=<file.gpx> OUT=<dir> MODE=ttp|plan|hand|code|cells EXTRA_KT="app/…/cells/CellMath.kt app/…/routing/PlanCode.kt" \
//   KTTEST=tools/routing/jvm-test/MultiDayTourTest.kt JAVA_XMX=192m run.sh <segmentsDir with E10_N45.rd5>
// MODE=ttp writes the plan points to OUT/plan_points.txt; plan / code / cells read them (each MODE its own JVM = cold).
// TTP_MAX_CALLS=<n> (run.sh) raises TrackToPlan.MAX_CALLS for a comparison run.
import com.onpath.app.cells.CellId
import com.onpath.app.data.Geo
import com.onpath.app.routing.*
import java.io.File
import java.io.RandomAccessFile
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType

class FakeCtx(private val dir: File) : android.content.Context() {
    override fun getApplicationContext(): android.content.Context = this
    override fun getFilesDir(): File = dir
    override fun getAssets(): android.content.res.AssetManager = android.content.res.AssetManager()
}

val SEG = File("/home/user/build/routing-test/segments")

fun ms(t0: Long) = (System.nanoTime() - t0) / 1_000_000

fun resetPeak() = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.forEach { it.resetPeakUsage() }
fun peakMb() = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }.sumOf { it.peakUsage.used } / 1e6

fun readGpx(f: String): List<GeoPoint> = Regex("<trkpt lat=\"([-\\d.]+)\" lon=\"([-\\d.]+)\"").findAll(File(f).readText())
    .map { GeoPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }.toList()

fun readPts(f: File) = f.readLines().filter { it.isNotBlank() }.map { l -> l.split(",").let { GeoPoint(it[0].toDouble(), it[1].toDouble()) } }

/** How far [line] is from the track: samples every ~[stepM] along the track. */
fun deviation(track: List<GeoPoint>, line: List<GeoPoint>, stepM: Double = 250.0): String {
    val s = TrackToPlan.resample(track, stepM)
    val d = s.map { Geo.distanceToLine(it, line) }.sorted()
    fun share(m: Double) = 100.0 * d.count { it > m } / d.size
    return "%d samples: %.1f %% > 40 m, %.1f %% > 200 m, %.1f %% > 1 km, median %.0f m, max %.0f m".format(
        d.size, share(40.0), share(200.0), share(1000.0), d[d.size / 2], d.last())
}

/** The hand plan (~10 points, the official west variant: Bad Tölz – Achensee – Innsbruck – Brenner – Pustertal – Cortina – Belluno – Treviso). */
val HAND = listOf(
    GeoPoint(48.1374, 11.5755), GeoPoint(47.7606, 11.5590), GeoPoint(47.4460, 11.7135), GeoPoint(47.2655, 11.3940),
    GeoPoint(47.0030, 11.5060), GeoPoint(46.7300, 12.2240), GeoPoint(46.5400, 12.1360), GeoPoint(46.1400, 12.2170),
    GeoPoint(45.6670, 12.2430), GeoPoint(45.4380, 12.3190),
)

fun main() {
    android.content.res.AssetManager.root = File("/home/user/onpath/app/src/main/assets")
    val files = File("/home/user/build/routing-test/kt-files").apply { deleteRecursively(); mkdirs() }
    val out = File(System.getenv("OUT") ?: error("OUT=<dir> needed")).apply { mkdirs() }
    val track = readGpx(System.getenv("TRACK_GPX") ?: error("TRACK_GPX=<file.gpx> needed"))
    val act = Activity.BIKE_TOURING
    val ptsFile = File(out, "plan_points.txt")
    println("track: %d points, %.1f km, max heap %.0f MB".format(track.size, Geo.length(track) / 1000, Runtime.getRuntime().maxMemory() / 1e6))
    when (System.getenv("MODE") ?: "ttp") {
        "ttp" -> {
            val e = BRouterEngine(FakeCtx(files), SEG)
            resetPeak()
            val t0 = System.nanoTime()
            val r = TrackToPlan.fit(track, e, act, 2)
            val t = ms(t0)
            ptsFile.writeText(r.points.joinToString("\n") { "${it.lat},${it.lon}" })
            println("TrackToPlan (MAX_CALLS=%d): %d route points, %d calls, follows %.1f %%, %d failed legs, %.1f s, peak heap %.0f MB".format(
                TrackToPlan.MAX_CALLS, r.points.size, r.calls, r.follows * 100, r.failedLegs, t / 1000.0, peakMb()))
            val gaps = r.points.zipWithNext().map { (a, b) -> Geo.distance(a, b) / 1000 }
            println("  straight gaps between route points: min %.1f, median %.1f, max %.1f km".format(gaps.min(), gaps.sorted()[gaps.size / 2], gaps.max()))
        }
        "plan", "hand" -> {
            val hand = System.getenv("MODE") == "hand"
            val wps = if (hand) HAND else readPts(ptsFile)
            val e = BRouterEngine(FakeCtx(files), SEG)
            val cache = LegCache()
            resetPeak()
            val t0 = System.nanoTime()
            val p = Planner(e, legs = cache)
            val r = p.plan(wps, act, 2)
            val t = ms(t0)
            val line = r.legs.flatMap { it.points }
            println("%s: %d points, %d legs, first routing (cold) %.1f s, peak heap %.0f MB, %.1f km, ascent %.0f m, %d straight legs, error=%s".format(
                if (hand) "hand plan" else "plan from track", wps.size, wps.size - 1, t / 1000.0, peakMb(), r.distanceM / 1000, r.ascentM,
                r.legs.count { it.straight }, r.error))
            val legMs = p.tries.map { it.ms }.sorted()
            println("  leg requests: %d, median %d ms, max %d ms; line %d points".format(legMs.size, legMs[legMs.size / 2], legMs.last(), line.size))
            println("  vs the track: " + deviation(track, line))
            File(out, if (hand) "hand_line.txt" else "plan_line.txt").writeText(line.joinToString("\n") { "${it.lat},${it.lon}" })
            // the same plan again with the same leg cache (an edit elsewhere = all other legs should come from it)
            val t1 = System.nanoTime()
            val p2 = Planner(e, legs = cache)
            p2.plan(wps, act, 2)
            println("  again, same leg cache (capacity %d): %.1f s, %d of %d legs from the cache".format(LegCache.CAPACITY, ms(t1) / 1000.0, p2.cachedLegs, wps.size - 1))
        }
        "code" -> {
            val wps = readPts(ptsFile)
            fun code(pts: List<GeoPoint>, names: Map<Int, String> = emptyMap(), title: String? = null) =
                PlanCode.encode(SharedPlan(pts, act, 2, emptySet(), names, title))
            val bare = code(wps)
            val hotels = listOf("Gasthof Zantl Bad Tölz", "Hotel Seehof Achenkirch", "Hotel Grauer Bär Innsbruck",
                "Hotel Gasser Sterzing", "Hotel Union Cortina d'Ampezzo", "Albergo Cappello Belluno")
            val idx = (1..6).map { it * wps.lastIndex / 7 }
            val named = code(wps, idx.zip(hotels).toMap(), "München – Venezia in 7 Tagen")
            println("plan code, %d points: %d chars bare, %d chars with title + 6 hotel names (%.1f chars per point); limits: points %d, text %d, sync plan %d, blob %d bytes".format(
                wps.size, bare.length, named.length, bare.length.toDouble() / wps.size, PlanCode.MAX_POINTS, 16_000, 16_000, 24_000))
            println("  decodes again: %s".format(PlanCode.decode(named)?.points?.size))
            // the whole 7-day tour as stage 1 stores it: days, limits, 6 hotels (name, address, link), a description
            val nights = idx.zip(hotels).associate { (i, h) ->
                i to NightMark(Hotel(h, "Hauptstraße 12, 83646 Bad Tölz, Deutschland", "https://www.booking.com/hotel/de/gasthof-beispiel-${i}.de.html"))
            }
            val desc = "Sieben Tage mit dem Rad von München nach Venedig: Isar, Achensee, Inn, Brenner, Pustertal, Dolomiten, Piave. " +
                "Hotels gebucht, Frühstück jeweils ab 7 Uhr. Am Brenner Wintersperre beachten.".repeat(2)
            val full = PlanCode.encode(SharedPlan(wps, act, 2, emptySet(), emptyMap(), "München – Venezia in 7 Tagen",
                days = 7, limits = DayLimits(maxTimeMin = 360, maxAscentM = 1200), nights = nights, description = desc))
            println("  7-day tour (6 hotels with address + link, %d-char description): %d chars, decodes: %s".format(
                desc.length, full.length, PlanCode.decode(full)?.nights?.size))
            for (n in listOf(100, 200, 500)) {
                val tl = Geo.length(track)
                val pts = TrackToPlan.resample(track, tl / (n - 1)).take(n)
                println("  %d points evenly on the track (every %.1f km): %d chars".format(pts.size, tl / (n - 1) / 1000, code(pts, idx.filter { it < pts.size }.zip(hotels).toMap(), "München – Venezia in 7 Tagen").length))
            }
        }
        "cells" -> {
            val line = track // the tour as ridden (a plan that follows it has the same corridor)
            println("line: %d points, %.1f km".format(line.size, Geo.length(line) / 1000))
            val rd5 = File(SEG, "E10_N45.rd5")
            val head = ByteArray(Rd5Parts.INDEX_BYTES).also { RandomAccessFile(rd5, "r").use { f -> f.readFully(it) } }
            val index = Rd5Parts.Index(head)
            for (m in listOf(20_000.0, 10_000.0, 5_000.0, 1_000.0)) {
                val all = CellId.alongLine(line, m, max = 100_000)
                val capped = CellId.alongLine(line, m)
                val pk = CellId.packagesFor(all)
                val parts = Rd5Parts.forCorridor(line, m)
                val partMb = parts.sumOf { if (it.tile == "E10_N45.rd5") index.size(it.index) else 0L } / 1e6
                println("corridor %.0f km: %d cells (alongLine default max 200 → %d), %d region packages, %d routing parts %.0f MB (%s)".format(
                    m / 1000, all.size, capped.size, pk.size, parts.size, partMb, parts.joinToString(" ") { it.label.replace("°", "") }))
            }
        }
        else -> error("MODE?")
    }
}
