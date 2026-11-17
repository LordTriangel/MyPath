// Time and memory of the Kotlin tour-suggestion core on the desktop JVM, one wish per JVM (no reference in memory):
//   tools/suggest-poc/port/measure.sh <hilden-2h|mittenwald-gipfel-3h|zons-rad-3h|nordkirchen-rad-3h> [runs]
// The app's NetReader on the rd5 files (network read included), the suggest files of SUGGEST_FILES. Prints the time of
// each run (the last one warm) and the heap's peak of the last run (sum of the pools' peaks); measure.sh finds the
// smallest -Xmx that still runs it.
import com.onpath.app.suggest.NetReader
import com.onpath.app.suggest.ReaderNetSource
import com.onpath.app.suggest.SuggestSettings
import com.onpath.app.suggest.TourSuggester
import com.onpath.app.suggest.Wish
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.util.Locale

fun main(args: Array<String>) {
    val home = System.getProperty("user.home")
    val root = File(System.getenv("ONPATH_ROOT") ?: ".")
    val S = SuggestSettings.parse(File(root, "tools/suggest-poc/settings.json").readText())
    val files = DirFiles(File(System.getenv("SUGGEST_FILES") ?: "$home/onpath-suggest-poc/suggest-file/cells-c2"))
    val reader = NetReader(File(System.getenv("ONPATH_SEGMENTS") ?: "$home/onpath-routing/segments"))
    val prof = File(System.getenv("PROFILES") ?: "$home/.cache/onpath-suggest-poc/profiles-8791")
    val source = ReaderNetSource(reader) { ch -> NetReader.Profile(File(prof, ch.profile + ".brf"), HashMap(ch.kv).also { it["processUnusedTags"] = "1" }) }
    val wish = when (args[0]) {
        "hilden-2h" -> Wish.of(S, 6.9395, 51.168, "hike", hours = doubleArrayOf(2.0))
        "mittenwald-gipfel-3h" -> Wish.of(S, 11.26143, 47.44178, "hike", hours = doubleArrayOf(3.0), ziel = listOf("gipfel"))
        "zons-rad-3h" -> Wish.of(S, 6.8448, 51.1231, "bike", hours = doubleArrayOf(3.0))
        "nordkirchen-rad-3h" -> Wish.of(S, 7.52518, 51.73802, "bike", hours = doubleArrayOf(3.0))
        else -> error("unknown wish ${args[0]}")
    }
    val runs = args.getOrNull(1)?.toInt() ?: 3
    val pools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
    for (k in 1..runs) {
        System.gc()
        pools.forEach { it.resetPeakUsage() }
        val t0 = System.nanoTime()
        val r = TourSuggester.suggest(wish, source, files, S)
        val secs = (System.nanoTime() - t0) / 1e9
        println(String.format(Locale.ROOT, "%s run %d: %.2f s, heap peak %.0f MB, %d loops, %d cards: %s · %s", args[0], k, secs,
            pools.sumOf { it.peakUsage.used } / 1048576.0, r.candidates.size, r.shown.size, r.shown.firstOrNull()?.name ?: "–",
            r.times.mapValues { String.format(Locale.ROOT, "%.2f", it.value) }))
        if (k == runs && System.getenv("MEASURE_CARDS") != null) {  // the cards' words (SUGGEST_LANG=en: the English ones)
            for (n in r.notes) println("  note: $n")
            for (c in r.shown) println("  ${c.name} · ${c.bearing}\n    ${c.reasons.joinToString(" · ")}\n    ${c.line}\n    ${c.hints.joinToString(" · ")} | ${c.why}")
        }
    }
}
