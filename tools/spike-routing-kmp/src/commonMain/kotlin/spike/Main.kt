// The spike's routes on the Kotlin port – the same main for the JVM and for linuxX64, the same output format as the
// Java reference (ref/btools/router/RefMain.java).
// Usage: <segmentsDir> <profilesDir> <routes.txt> <outDump> <timesFile> <reps>
package spike

import btools.platform.File
import btools.platform.nanoTime
import btools.platform.readFileLines
import btools.platform.writeFileText
import btools.router.OsmNodeNamed
import btools.router.OsmTrack
import btools.router.RoutingContext
import btools.router.RoutingEngine

fun main(args: Array<String>) {
    val seg = File(args[0])
    val profiles = args[1]
    val reps = args[5].toInt()
    val dump = StringBuilder()
    val times = StringBuilder()
    val collects = StringBuilder("# routes whose search threw nodes away under memory pressure: name, how often\n")
    val tAll = nanoTime()
    for (raw in readFileLines(args[2])) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val p = line.split("|")
        val name = p[0]
        val profile = p[1]
        times.append(name)
        for (r in 0 until reps) {
            val rc = RoutingContext()
            rc.localFunction = File(profiles, "$profile.brf").getAbsolutePath()
            val kv = LinkedHashMap<String, String>()
            for (e in p[3].split(",")) {
                val x = e.split("=")
                if (x[0] == "memoryclass") rc.memoryclass = x[1].toInt() // the harness's own key: BRouter's memory budget
                else kv[x[0]] = x[1]
            }
            rc.keyValues = kv
            val wps = ArrayList<OsmNodeNamed>()
            val pts = p[2].split(";")
            for (i in pts.indices) {
                val ll = pts[i].split(",")
                val n = OsmNodeNamed()
                n.name = if (i == 0) "from" else if (i == pts.size - 1) "to" else "via$i"
                n.ilon = ((ll[0].toDouble() + 180.0) * 1_000_000.0 + 0.5).toInt()
                n.ilat = ((ll[1].toDouble() + 90.0) * 1_000_000.0 + 0.5).toInt()
                wps.add(n)
            }
            val t0 = nanoTime()
            val re = RoutingEngine(null, null, seg, wps, rc)
            re.quite = true
            re.doRun(0)
            val ms = (nanoTime() - t0) / 1_000_000L
            times.append(' ').append(ms)
            if (r == 0 && re.collectCount > 0) collects.append(name).append(' ').append(re.collectCount).append('\n')
            if (r == 0) dump(dump, name, profile, re)
        }
        times.append('\n')
    }
    times.append("total_ms ").append((nanoTime() - tAll) / 1_000_000L).append('\n')
    for (l in readFileLines("/proc/self/status")) {
        if (l.startsWith("VmHWM") || l.startsWith("VmRSS")) times.append(l.replace(Regex("\\s+"), " ")).append('\n')
    }
    writeFileText(args[3], dump.toString())
    writeFileText(args[4], times.toString())
    writeFileText(args[4] + ".collects", collects.toString())
    print(times)
}

private fun hex(f: Float): String = f.toRawBits().toUInt().toString(16)

private fun dump(sb: StringBuilder, name: String, profile: String, re: RoutingEngine) {
    sb.append("R ").append(name).append(' ').append(profile).append('\n')
    if (re.getErrorMessage() != null) { sb.append("E ").append(re.getErrorMessage()).append('\n'); return }
    val t: OsmTrack? = re.getFoundTrack()
    if (t == null || t.nodes.isEmpty()) { sb.append("E <no error message and no track>\n"); return }
    val hints = t.voiceHints?.list?.size ?: 0
    sb.append("T cost=").append(t.cost).append(" dist=").append(t.distance).append(" ascend=").append(t.ascend)
        .append(" plain=").append(t.plainAscend).append(" energy=").append(t.energy).append(" secs=").append(t.getTotalSeconds())
        .append(" nodes=").append(t.nodes.size).append(" hints=").append(hints).append('\n')
    for (n in t.nodes) {
        sb.append("N ").append(n.getILon()).append(' ').append(n.getILat()).append(' ').append(n.getSElev().toInt()).append(' ')
            .append(n.cost).append(' ').append(hex(n.getTime())).append(' ').append(hex(n.getEnergy())).append(' ')
        val m = n.message
        if (m == null) sb.append('-')
        else sb.append(((m.wayKeyValues + "|" + m.nodeKeyValues).hashCode()).toUInt().toString(16)).append(' ').append(m.linkdist)
            .append(' ').append(hex(m.costfactor))
        sb.append('\n')
    }
    t.voiceHints?.list?.let { list ->
        for (h in list) {
            sb.append("H ").append(h.indexInTrack).append(' ').append(h.cmd).append(' ').append(hex(h.angle)).append(' ')
                .append(h.roundaboutExit).append(' ').append(h.ilon).append(' ').append(h.ilat).append('\n')
        }
    }
}
