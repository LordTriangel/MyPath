// The app's own network reader and searches (app/src/main/java/com/onpath/app/suggest/) on the bench's cases – the
// successor of Bench.java's own reading (README.md beside this file). Same start, radius, profile parameters and
// coarse filter as the POC, so the node / link counts must equal Bench.java's.
//   NetBenchKt <segmentsDir> <profileDir> <case> [repeats=2] [tile budget MB=24] [live]
// "live": the sampler forces a GC before each sample (every ~20 ms), so "peak" is the live heap, not garbage the
// collector has not taken yet (slower; the times of a live run are not the times of the read).
import com.onpath.app.suggest.LoopAssembly
import com.onpath.app.suggest.NetReader
import com.onpath.app.suggest.NetSearch
import com.onpath.app.suggest.WayNet
import java.io.File
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos

private class Case(val name: String, val lon: Double, val lat: Double, val profile: String, val bike: Boolean,
                   val radius: Double, val maxM: Double, val targetM: Double, val sectors: Int, val stops: Int, val bounded: Int)

private val CASES = listOf(
    Case("hike2", 6.9395, 51.168, "hiking-mountain", false, 3948.0, 9400.0, 8400.0, 12, 102, 3),
    Case("hike4", 6.9395, 51.168, "hiking-mountain", false, 7000.0, 17800.0, 16800.0, 12, 96, 3),
    Case("bike2", 6.9395, 51.168, "touring", true, 17220.0, 41000.0, 36000.0, 6, 60, 2),
    Case("bike3", 6.9395, 51.168, "touring", true, 22000.0, 59000.0, 54000.0, 6, 58, 2),
    Case("zons-bike3", 6.8448, 51.1231, "touring", true, 22000.0, 59000.0, 54000.0, 6, 54, 2),
    Case("muenchen-bike3", 11.5755, 48.1374, "touring", true, 22000.0, 59000.0, 54000.0, 6, 92, 2),
)

@Volatile private var peak = 0L
@Volatile private var sampling = true
@Volatile private var live = false

private fun used(rt: Runtime) = rt.totalMemory() - rt.freeMemory()
private fun out(f: String, vararg a: Any?) = println(String.format(Locale.ROOT, f, *a))

fun main(args: Array<String>) {
    val rt = Runtime.getRuntime()
    Thread {
        while (sampling) {
            if (live) System.gc()
            val u = used(rt)
            if (u > peak) peak = u
            try { Thread.sleep(if (live) 20 else 2) } catch (e: InterruptedException) { return@Thread }  // expected: bench ends
        }
    }.apply { isDaemon = true; start() }
    val c = CASES.firstOrNull { it.name == args[2] } ?: error("unknown case ${args[2]}")
    val repeats = args.getOrNull(3)?.toInt() ?: 2
    val budget = (args.getOrNull(4)?.toLong() ?: 24L) shl 20
    live = args.getOrNull(5) == "live"
    // the POC's parameters (character.py: kv; settings.json: coarse) – not the app's, so the counts compare with Bench.java
    val kv = HashMap<String, String>()
    kv["processUnusedTags"] = "1"
    kv["mainroad_penalty"] = "3.0"
    if (c.bike) kv["consider_traffic"] = "1" else { kv["SAC_scale_limit"] = "2"; kv["SAC_scale_preferred"] = "2" }
    val profile = NetReader.Profile(File(args[1], c.profile + ".brf"), kv)
    val reader = NetReader(File(args[0]))
    System.gc()
    val base = used(rt)
    out("case %s  profile %s  radius %.0f m  heap max %d MB  base %.1f MB", c.name, c.profile, c.radius, rt.maxMemory() shr 20, base / 1e6)
    var net: WayNet? = null
    for (r in 0 until repeats) {
        net = null
        System.gc()
        peak = used(rt)
        val t0 = System.nanoTime()
        net = reader.read(profile, c.lon, c.lat, c.radius, if (c.bike) NetReader.Coarse.BIKE else NetReader.Coarse.NONE,
            tileBudgetBytes = budget)
        val ms = (System.nanoTime() - t0) / 1e6
        val readPeak = peak - base
        System.gc()
        out("read %d: %.0f ms | %d nodes, %d links | peak while reading %.1f MB, network after %.1f MB (arrays %.1f MB) | %s",
            r + 1, ms, net.nodeCount, net.linkCount, readPeak / 1e6, (used(rt) - base) / 1e6, net.bytes() / 1e6, reader.stats)
        if (r == repeats - 1) out("RESULT %s nodes=%d links=%d read_ms=%.0f peak_read_mb=%.1f net_mb=%.1f arrays_mb=%.1f",
            c.name, net.nodeCount, net.linkCount, ms, readPeak / 1e6, (used(rt) - base) / 1e6, net.bytes() / 1e6)
    }
    runSearches(net!!, c, repeats, rt, base)
    net = null
    sampling = false
    System.gc()
    out("without the network: %.1f MB above the start (BRouter's cached profile with its expression cache)",
        (used(rt) - base) / 1e6)
}

private fun runSearches(n: WayNet, c: Case, repeats: Int, rt: Runtime, base: Long) {
    // a stand-in cost (the method's place.cost comes later): metres × the profile's cost factor, closed = −1
    val cost = DoubleArray(n.linkCount) { e ->
        if (!n.allowed(e)) -1.0
        else n.linkLen[e].toDouble() * minOf(minOf(n.costFactor(e, n.linkA[e]), n.costFactor(e, n.linkB[e])), 5f)
    }
    val allowed = (0 until n.linkCount).count { n.allowed(it) }
    val wayOnly = (0 until n.linkCount).count { n.linkFlags[it].toInt() and (WayNet.WAY_AB or WayNet.WAY_BA) != 0 }
    out("allowed links %d (the POC's way-only rule: %d)", allowed, wayOnly)
    for (r in 0 until repeats) {
        peak = used(rt)
        val t0 = System.nanoTime()
        val s = searches(n, cost, c)
        out("searches run %d: %.0f ms  (full 1, stop %d, bounded %d; settled %d)  peak %.1f MB",
            r + 1, (System.nanoTime() - t0) / 1e6, s[0], s[1], s[2], (peak - base) / 1e6)
    }
}

/** The POC's number of searches for this wish (as Bench.java): one full, turning points per sector, loops, lines. */
private fun searches(n: WayNet, cost: DoubleArray, c: Case): LongArray {
    val s = NetSearch(n)
    s.full(n.start, cost)
    val tree = s.tree()
    val anchors = ArrayList<Int>()
    for (f in doubleArrayOf(0.85, 1.0, 1.15, 0.7)) {
        val best = IntArray(c.sectors) { -1 }
        val bestR = DoubleArray(c.sectors)
        val want = f * c.targetM / 2.2
        for (v in 0 until n.nodeCount) {
            val m = tree.metres[v]
            if (tree.dist[v] == NetSearch.INF || m < 0.9 * want || m > 1.1 * want) continue
            val b = Math.toDegrees(atan2((n.nodeLon[v] - n.nodeLon[n.start]) * cos(Math.toRadians(n.lat(n.start))),
                (n.nodeLat[v] - n.nodeLat[n.start]).toDouble()))
            val k = (((b + 360) % 360) * c.sectors / 360.0).toInt() % c.sectors
            val q = tree.dist[v] / m
            if (best[k] < 0 || q < bestR[k]) { best[k] = v; bestR[k] = q }
        }
        for (v in best) if (v >= 0) anchors.add(v)
    }
    check(anchors.isNotEmpty()) { "no turning points" }
    val asm = LoopAssembly(s, n.start, cost, tree, if (c.targetM > 25000) 2500.0 else 700.0, 6.0, 2.5)
    var stops = 0
    var k = 0
    var loops = 0
    while (stops < c.stops) {
        val size = 1 + (k % 3)
        val pts = IntArray(size) { anchors[(k + it * (1 + anchors.size / 4)) % anchors.size] }
        k++
        asm.assemble(pts)
        stops += size
        loops++
    }
    for (i in 0 until c.bounded) s.bounded(anchors[i % anchors.size], 0.75 * c.maxM, cost)
    return longArrayOf(stops.toLong(), c.bounded.toLong(), s.settled)
}
