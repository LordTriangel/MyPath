package com.onpath.app.suggest

/**
 * One loop of the pool – ours or an official signed loop laid onto the network – with everything the judging, the
 * selection and the card need (the POC's candidate dict).
 */
class Candidate(
    /** links in walking order */
    val edges: IntArray,
    /** nodes in walking order, first = last = the loop's start */
    val nodes: IntArray,
    val m: Double,
    val up: Double,
    val hours: Double,
    val stemM: Double,
    val doubledM: Double,
    val cross: Int,
) {
    /** Wendepunkt, Wendepunkte, Anstieg, Kette, Rundweg, Linie, offiziell */
    var origin = ""
    /** what the generator aimed at (area / line / loop names) */
    var aim: List<String> = emptyList()
    var official: Official? = null
    /** an official loop that fits the wish and may be chosen (the POC's `show`) */
    var show = false
    var id = 0
    /** the four place axes, raw means (natur, ruhe, weg, besonderes) */
    val raw = DoubleArray(4)
    /** natur, ruhe, weg, besonderes, form – each 0..1 */
    val axes = DoubleArray(5)
    var score = 0.0
    /** the Form parts below 0.995 by name (Zeit, Höhenmeter, doppelt, kreuzt sich) */
    var formParts: LinkedHashMap<String, Double> = LinkedHashMap()
    lateinit var numbers: Numbers
    /** the hard rule that drops it, null = offered */
    var out: String? = null
    var chosen = false
    var name = ""
    var bearing = ""
    var reasons: List<String> = emptyList()
    var hints: List<String> = emptyList()
    /** one generated line on what the loop passes (never an operator's text) */
    var line = ""
    var why = ""

    /** [loopKm] = how much of the whole ride (from the wished start) is on the signed loop */
    class Official(val name: String, val badge: String, val osm: Long, val loopKm: Double)

    /** The plain facts of a loop for the card (score.py `numbers`), absolute. */
    class Numbers(
        val km: Double, val hours: Double, val ascent: Int, val forestPct: Int, val deepPct: Int, val townPct: Int,
        val streetPct: Int, val roadLongestM: Int, val signedPct: Int, val waterKm: Double, val waterPct: Int,
        val railKm: Double, val groundPct: IntArray, val ferryM: Int, val selfCross: Int, val doubledPct: Int,
        val stemM: Int,
        /** (name, km) */
        val reserves: List<Pair<String, Double>>,
        val known: List<Pair<String, Double>>,
        val lakes: List<Pair<String, Double>>,
        val rimKm: Double,
        /** (what, name) */
        val sights: List<Pair<String, String>>,
    )

    /** The loop's line as lon / lat pairs (start, every shape point, every node). */
    fun coords(net: WayNet): DoubleArray {
        val out = DoubleList()
        out.add(net.lon(nodes[0]), net.lat(nodes[0]))
        for (i in edges.indices) {
            val g = Facts.geomOf(net, edges[i], nodes[i])
            for (k in 1 until g.size / 2) out.add(g[2 * k], g[2 * k + 1])
        }
        return out.toArray()
    }
}
