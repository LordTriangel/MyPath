// Parity of the app's tour-suggestion core (app/src/main/java/com/onpath/app/suggest/TourSuggester.kt) with the frozen
// POC (tools/suggest-poc/FROZEN.md): runs the reference wishes (reference.py output, *.json.gz) through the Kotlin core
// with the same suggest files and compares every loop of the pool, the choice and the owner's checks of cases.py.
//   tools/suggest-poc/port/parity.sh [graph|reader] [name words…]   (PARITY_REPEAT=3: times of the 3rd run)
// graph  = the network from the POC's route service (/graph, the same text the reference runs had) – the method alone
// reader = the network read by the app's NetReader from the rd5 files (same nodes and links, another order)
import com.onpath.app.suggest.Candidate
import com.onpath.app.suggest.Character
import com.onpath.app.suggest.NetReader
import com.onpath.app.suggest.NetSource
import com.onpath.app.suggest.ReaderNetSource
import com.onpath.app.suggest.SuggestFile
import com.onpath.app.suggest.SuggestFiles
import com.onpath.app.suggest.SuggestGeo
import com.onpath.app.suggest.SuggestResult
import com.onpath.app.suggest.SuggestSettings
import com.onpath.app.suggest.TourSuggester
import com.onpath.app.suggest.WayKinds
import com.onpath.app.suggest.WayNet
import com.onpath.app.suggest.Wish
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.zip.GZIPInputStream

private fun env(k: String, d: String) = System.getenv(k) ?: d
private val HOME = System.getProperty("user.home")

/** The POC's route service: the same /graph text the reference runs had, as a [WayNet]. */
class GraphNetSource(private val service: String) : NetSource {
    override fun read(ch: Character, radiusM: Double): WayNet {
        val lines = arrayListOf("profile=${ch.profile}", String.format(Locale.ROOT, "at=%.6f,%.6f", ch.wish.lon, ch.wish.lat),
            "radius=${radiusM.toInt()}")
        for ((k, v) in ch.kv) lines.add("kv=$k=$v")
        val c = ch.family["coarse"]
        if (!c.isNull) for (k in c.keys) lines.add("$k=" + (if (k == "maxcf") c[k].d.toLong().toString() else c[k].s))
        val con = URI("$service/graph").toURL().openConnection() as HttpURLConnection
        con.doOutput = true
        con.readTimeout = 300_000
        con.outputStream.use { it.write(lines.joinToString("\n").toByteArray()) }
        val text = con.inputStream.use { String(it.readBytes()) }
        if (text.startsWith("ERR")) error(text.take(300))
        return parse(text)
    }

    companion object {
        fun parse(text: String): WayNet {
            val kinds = WayKinds()
            val b = WayNet.Builder(kinds)
            val tags = HashMap<Int, String>()
            val kindOf = HashMap<String, Int>()
            var start = 0
            for (ln in text.split('\n')) {
                if (ln.isEmpty()) continue
                when (ln[0]) {
                    'S' -> start = ln.split(' ')[1].toInt()
                    'N' -> {
                        val f = ln.split(' ')
                        val ele = f[4].toDouble()
                        b.addNode(WayNet.ilon(f[2].toDouble()), WayNet.ilat(f[3].toDouble()), Math.round(ele * 4).toInt().coerceIn(-32768, 32767).toShort())
                    }
                    'T' -> { val f = ln.split(' ', limit = 3); tags[f[1].toInt()] = if (f.size > 2) f[2] else "" }
                    'E' -> {
                        val f = ln.split(' ', limit = 8)
                        val cf = f[4].toFloat(); val cb = f[5].toFloat()
                        val key = "${f[6]} ${f[4]} ${f[5]}"
                        val kind = kindOf.getOrPut(key) { kinds.add(cf, cb, tags.getValue(f[6].toInt())) }
                        var flags = 0
                        if (cf < 9999f) flags = flags or WayNet.WAY_AB or WayNet.ALLOWED_AB
                        if (cb < 9999f) flags = flags or WayNet.WAY_BA or WayNet.ALLOWED_BA
                        b.beginLink(f[1].toInt(), f[2].toInt(), kind, flags)
                        if (f.size > 7 && f[7].isNotEmpty()) for (p in f[7].split(';')) {
                            val xy = p.split(',')
                            b.shape(WayNet.ilon(xy[0].toDouble()), WayNet.ilat(xy[1].toDouble()))
                        }
                        b.endLink(f[3].toFloat(), 0f, 0f)
                    }
                }
            }
            return b.build(start)
        }
    }
}

class DirFiles(private val root: File) : SuggestFiles {
    private val mem = HashMap<String, SuggestFile?>()
    override fun cell(x: Int, y: Int): SuggestFile? = mem.getOrPut("$x-$y") {
        val f = File(root, "10-$x-$y/${SuggestFile.NAME}")
        if (f.isFile) SuggestFile.read(f.readBytes()) else null
    }
}

private fun gz(f: File): SuggestSettings.J = SuggestSettings.parse(GZIPInputStream(f.inputStream()).use { String(it.readBytes()) }).root

private fun key(coords: DoubleArray): String {
    val sb = StringBuilder()
    for (v in coords) sb.append(Math.round(v * 1e6)).append(',')
    return sb.toString()
}

private fun keyRef(c: SuggestSettings.J): String {
    val sb = StringBuilder()
    for (p in c.list) { sb.append(Math.round(p[0].d * 1e6)).append(','); sb.append(Math.round(p[1].d * 1e6)).append(',') }
    return sb.toString()
}

private fun f(x: Double) = String.format(Locale.ROOT, "%.1f", x)

/** cases.py `view`: the shown suggestions as small records. */
class View(val name: String, val km: Double, val up: Int, val forest: Int, val town: Int, val road: Int, val cross: Int,
           val doubled: Int, val named: Map<String, Double>, val reserves: Int, val dir: String, val hints: List<String>,
           val hours: Double, val official: Boolean, val fromM: Double)

private val DIRS = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
private val WORD_DIR = mapOf("Norden" to "N", "Nordosten" to "NO", "Osten" to "O", "Südosten" to "SO", "Süden" to "S",
    "Südwesten" to "SW", "Westen" to "W", "Nordwesten" to "NW")

fun view(r: SuggestResult): List<View> = r.shown.map { c ->
    val n = c.numbers
    val named = LinkedHashMap<String, Double>()
    for (row in n.reserves + n.lakes + n.known) named[row.first] = (named[row.first] ?: 0.0) + row.second
    View(c.name, n.km, n.ascent, n.forestPct, n.townPct, n.roadLongestM, n.selfCross, n.doubledPct, named, n.reserves.size,
        c.bearing, c.hints, n.hours, c.official != null,
        SuggestGeo.dist(r.net.lon(c.nodes[0]), r.net.lat(c.nodes[0]), r.ch.wish.lon, r.ch.wish.lat))
}

fun km(v: View, vararg words: String) = v.named.entries.filter { (k, _) -> words.any { k.lowercase().contains(it.lowercase()) } }.sumOf { it.value }
val RHEIN = arrayOf("Rheinradweg", "EV15")

/** cases.py CASES: name → (statement, check over the shown records and the run's notes). */
val CASES: Map<String, List<Pair<String, (List<View>, List<String>) -> Boolean>>> = mapOf(
    "hilden-wandern" to listOf(
        "mindestens 3 Vorschläge" to { s, _ -> s.size >= 3 },
        "der Lauf sagt, dass der Weg ins Grüne nicht mitzählt" to { _, notes -> notes.any { "Grüne" in it } },
        "erste Runde ohne langes Straßenstück" to { s, _ -> s[0].road < 200 }),
    "heide-wandern" to listOf(
        "erste Runde ≥ 2 km in der Ohligser Heide" to { s, _ -> km(s[0], "Ohligser Heide") >= 2.0 },
        "erste Runde ≥ 60 % am Wald, nichts doppelt" to { s, _ -> s[0].forest >= 60 && s[0].doubled <= 5 }),
    "elbsee-wasser" to listOf("erste Runde ≥ 1,5 km an Elbsee / Menzelsee" to { s, _ -> km(s[0], "Elbsee", "Menzelsee") >= 1.5 }),
    "nordkirchen-wandern" to listOf(
        "eine Runde ≥ 2,5 km im Hirschpark" to { s, _ -> s.any { km(it, "Hirschpark") >= 2.5 } },
        "keine Runde mit > 300 m Landstraße" to { s, _ -> s.all { it.road <= 300 } },
        "keine Runde > 40 % durch Häuser" to { s, _ -> s.all { it.town <= 40 } }),
    "nordkirchen-rad" to listOf(
        "erste Runde ≤ 20 % durch Häuser" to { s, _ -> s[0].town <= 20 },
        "Vorschläge in mindestens 3 Richtungen" to { s, _ -> s.map { it.dir }.toSet().size >= 3 }),
    "nordkirchen-rad-4h" to listOf("erste Runde ≤ 3 % doppelt" to { s, _ -> s[0].doubled <= 3 }),
    "hilden-rad" to listOf(
        "eine Runde durch die Urdenbacher Kämpe" to { s, _ -> s.any { km(it, "Urdenbacher") >= 1.0 } },
        "erste Runde ≤ 35 % durch Häuser" to { s, _ -> s[0].town <= 35 },
        "erste Runde durch ≥ 2 Schutzgebiete" to { s, _ -> s[0].reserves >= 2 }),
    "rhein-rad" to listOf(
        "unter den ersten 3 eine Runde ≥ 15 km auf dem Rheinradweg" to { s, _ -> s.take(3).any { km(it, *RHEIN) >= 15 } },
        "keine Runde mit > 1 km an einer Straße am Stück" to { s, _ -> s.all { it.road <= 1000 } },
        "keine Runde > 30 % durch Häuser" to { s, _ -> s.all { it.town <= 30 } }),
    "zons-rad-3h" to listOf("eine Runde ≥ 25 km auf dem Rheinradweg" to { s, _ -> s.any { km(it, *RHEIN) >= 25 } }),
    "koeln-rad" to listOf(
        "mindestens 3 Vorschläge" to { s, _ -> s.size >= 3 },
        "eine Runde ≥ 10 km auf dem Rheinradweg" to { s, _ -> s.any { km(it, *RHEIN) >= 10 } }),
    "duesseldorf-wandern" to listOf(
        "mindestens 2 Vorschläge" to { s, _ -> s.size >= 2 },
        "erste Runde ≤ 35 % durch Häuser" to { s, _ -> s[0].town <= 35 }),
    "meran-wandern" to listOf("eine Runde ≥ 2 km auf Waalweg / Tappeiner" to { s, _ -> s.any { km(it, "Waalweg", "Tappeiner") >= 2.0 } }),
    "meran-flach" to listOf("keine Runde über 200 m" to { s, _ -> s.all { it.up <= 200 } }),
    "nordkirchen-wandern-3h" to listOf(
        "mindestens 3 Vorschläge" to { s, _ -> s.size >= 3 },
        "keine Runde mit > 300 m an einer Straße" to { s, _ -> s.all { it.road <= 300 } }),
    "nordkirchen-rad-3h" to listOf("höchstens 5 Karten, mindestens 3" to { s, _ -> s.size in 3..5 }),
    "dhuenn-rad-350" to listOf(
        "keine Karte über 350 m ohne Hinweis" to { s, _ -> s.all { it.up <= 350 || it.hints.any { h -> "mehr als gewünscht" in h } } },
        "geht es hier nicht so flach, sagt der Lauf es" to { s, notes -> s.all { it.up <= 350 } || notes.any { "Höhenmetern" in it } }),
    "nordkirchen-rad-kurz" to listOf(
        "jede Karte fährt am Startpunkt los, die ganze Fahrt in der gewünschten Zeit" to { s, _ -> s.all { it.fromM <= 300 && it.hours <= 1.1 * 2 } },
        "mindestens eine eigene Runde" to { s, _ -> s.any { !it.official } }),
    "meran-steil" to listOf("erste Runde 500–900 m" to { s, _ -> s[0].up in 500..900 }),
)
val EVERY: List<Pair<String, (List<View>, List<String>) -> Boolean>> = listOf(
    "keine Runde kreuzt sich mehr als einmal" to { s, _ -> s.all { it.cross <= 1 } },
    "jede Runde hat einen echten Namen" to { s, _ -> s.all { it.name.isNotEmpty() && "Grünstück" !in it.name } })

fun wishOf(S: SuggestSettings, w: SuggestSettings.J): Wish {
    val asc = w["ascent"]
    return Wish.of(S, w["start"][0].d, w["start"][1].d, w["activity"].s, w["t_level"].i, w["hours"].doubles(), w["pace"].d,
        if (asc.isNull) null else arrayOf(asc[0].v as Double?, asc[1].v as Double?), w["natur"].s, w["ruhe"].s, w["bekannt"].s,
        w["wasser"].v == true, w["official"].v != false)
}

fun main(args: Array<String>) {
    val root = File(env("ONPATH_ROOT", "."))
    val mode = args.getOrNull(0) ?: "graph"
    val words = args.drop(1)
    val S = SuggestSettings.parse(File(root, "tools/suggest-poc/settings.json").readText())
    val files = DirFiles(File(env("SUGGEST_FILES", "$HOME/onpath-suggest-poc/suggest-file/cells")))
    val refDir = File(env("SUGGEST_REFERENCE", "$HOME/onpath-suggest-poc/reference"))
    val source: NetSource = if (mode == "reader") {
        val reader = NetReader(File(env("ONPATH_SEGMENTS", "$HOME/onpath-routing/segments")))
        val prof = File(env("PROFILES", "$HOME/.cache/onpath-suggest-poc/profiles-8791"))
        ReaderNetSource(reader) { ch -> NetReader.Profile(File(prof, ch.profile + ".brf"), HashMap(ch.kv).also { it["processUnusedTags"] = "1" }) }
    } else GraphNetSource(env("SUGGEST_SERVICE", "http://127.0.0.1:8791"))
    var good = 0; var total = 0
    val rows = ArrayList<String>()
    for (rf in refDir.listFiles { x -> x.name.endsWith(".json.gz") }!!.sortedBy { it.name }) {
        val name = rf.name.removeSuffix(".json.gz")
        if (words.isNotEmpty() && words.none { it in name }) continue
        val ref = gz(rf)
        val wish = wishOf(S, ref["wish"])
        repeat((System.getenv("PARITY_REPEAT") ?: "1").toInt() - 1) { TourSuggester.suggest(wish, source, files, S) }  // warm (JIT)
        val t0 = System.nanoTime()
        val r = TourSuggester.suggest(wish, source, files, S)
        val secs = (System.nanoTime() - t0) / 1e9
        val kt = r.candidates.associateBy { key(it.coords(r.net)) }
        val refC = ref["candidates"].list
        var same = 0; var scoreDiff = 0.0; var choiceDiff = 0
        val refOnly = HashMap<String, Int>(); val ktOnly = HashMap<String, Int>()
        val matched = HashSet<String>()
        for (c in refC) {
            val k = keyRef(c["coords"])
            val m = kt[k]
            if (m == null) {
                refOnly.merge(c["origin"].s, 1, Int::plus)
                if (System.getenv("PARITY_VERBOSE") != null && c["origin"].s == "offiziell") println("    ref only: ${c["official"]["name"].s} ${f(c["m"].d)} m")
                continue
            }
            matched.add(k)
            same++
            val d = Math.abs(m.score - c["score"].d)
            if (d > scoreDiff) scoreDiff = d
            if (m.chosen != (c["chosen"].v == true)) choiceDiff++
            if (System.getenv("PARITY_VERBOSE") != null && (d > 0.05 || m.chosen != (c["chosen"].v == true))) {
                println("    ${c["origin"].s} ref ${c["score"].d} kt ${m.score} axes ref ${c["axes"].v} kt ${m.axes.toList()} raw ref ${c["raw"].v} kt ${m.raw.toList()}")
            }
        }
        for ((k, c) in kt) if (k !in matched) {
            ktOnly.merge(c.origin, 1, Int::plus)
            if (System.getenv("PARITY_VERBOSE") != null && c.official != null) println("    kt only: ${c.official!!.name} ${f(c.m)} m")
        }
        val refShown = refC.filter { it["chosen"].v == true }.map { (it["name"].v as? String) ?: "" }
        val ktShown = r.shown.map { it.name }
        val sameChoice = refShown == ktShown
        val checks = CASES[name]
        var caseText = ""
        if (checks != null) {
            val s = view(r)
            val failed = (checks + EVERY).filter { (_, t) -> s.isEmpty() || !t(s, r.notes) }.map { it.first }
            good += checks.size + EVERY.size - failed.size; total += checks.size + EVERY.size
            caseText = if (failed.isEmpty()) "cases ok" else "cases FAILED: " + failed.joinToString("; ")
        }
        val line = String.format(Locale.ROOT, "%-24s pool ref %3d kt %3d same %3d (ref only %s, kt only %s) max Δscore %.1f, choice %s, %s %.2f s",
            name, refC.size, r.candidates.size, same, refOnly, ktOnly, scoreDiff,
            if (sameChoice) "same" else "DIFF ref $refShown kt $ktShown", caseText, secs)
        println(line)
        if (System.getenv("PARITY_VERBOSE") != null) println("    times ${r.times.mapValues { f(it.value) }} notes ${r.notes}")
        rows.add(line)
    }
    if (total > 0) println("cases.py: $good of $total statements")
}
