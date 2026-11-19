package com.onpath.app.suggest

import com.onpath.strings.*

/**
 * The words of the tour suggestions – title parts, the reason line, the generated line on what a loop passes, the
 * hints, the run's notes – from the strings module the app and the planner share (`strings_suggest.xml`, English +
 * German; docs/i18n.md). Whole phrases with placeholders; numbers formatted first ([num]: "1,5" in German, "1.5" in
 * English). In German every text is the POC's (`score.py`, `suggest.py`) word for word – the port's parity compares
 * the card titles.
 *
 * Sights keep their German word as the core's internal kind (Named.SIGHTS, "Aussichtspunkt" … – the method compares
 * them); [sight] turns it into the shown word.
 */
object SuggestText {
    /** A card number: Python's `str(float)`, with the decimal comma in German. */
    fun num(x: Double): String = if (Txt.language() == "de") SuggestGeo.comma(x) else SuggestGeo.pyStr(x)

    private val SIGHT_KEYS = mapOf("Aussichtspunkt" to Res.string.suggest_sight_viewpoint, "Burg" to Res.string.suggest_sight_castle,
        "Ruine" to Res.string.suggest_sight_ruins, "Kapelle" to Res.string.suggest_sight_chapel, "Höhle" to Res.string.suggest_sight_cave,
        "Wasserfall" to Res.string.suggest_sight_waterfall)

    /** The shown word of a sight kind (its German word in the core). */
    fun sight(what: String): String = SIGHT_KEYS[what]?.let { Txt.s(it) } ?: what

    /** A sight's shown name: its own name, or the word of its kind when it has none. */
    private fun sightName(what: String, name: String) = if (name == what) sight(what) else name

    /** "Norden" … "Nordwesten" (SuggestGeo.DIRS order) in the text language. */
    fun direction(lon1: Double, lat1: Double, lon2: Double, lat2: Double): String {
        val i = SuggestGeo.DIRS.indexOf(SuggestGeo.direction(lon1, lat1, lon2, lat2))
        return Txt.s(arrayOf(Res.string.suggest_dir_n, Res.string.suggest_dir_ne, Res.string.suggest_dir_e, Res.string.suggest_dir_se,
            Res.string.suggest_dir_s, Res.string.suggest_dir_sw, Res.string.suggest_dir_w, Res.string.suggest_dir_nw)[i])
    }

    fun toward(direction: String): String = Txt.s(Res.string.suggest_toward, direction)

    /** A stand-out area or line without a name: "Wald bei Hilden", "Ufer im Norden" ([word] = shore / forest / fields /
     * brook / rail). */
    fun spot(word: String, town: String, direction: String): String =
        if (town.isNotEmpty()) Txt.s(Res.string.suggest_spot_near, word, town) else Txt.s(Res.string.suggest_spot_in, word, direction)

    fun shore() = Txt.s(Res.string.suggest_spot_shore)
    fun forest() = Txt.s(Res.string.suggest_spot_forest)
    fun fields() = Txt.s(Res.string.suggest_spot_fields)
    fun brook() = Txt.s(Res.string.suggest_spot_brook)
    fun rail() = Txt.s(Res.string.suggest_rail)

    private fun feminine(low: String) = listOf("sperre", "bucht", "aue", "lache").any { low.endsWith(it) }

    /** 'am Elbsee', 'an der Dhrontalsperre'. */
    fun am(name: String): String = Txt.s(if (feminine(name.lowercase())) Res.string.suggest_at_f else Res.string.suggest_at, name)

    /** '2,5 km am Elbsee'. */
    private fun kmAt(km: String, name: String): String =
        Txt.s(if (feminine(name.lowercase())) Res.string.suggest_km_at_f else Res.string.suggest_km_at, km, name)

    /** 'rund um den Lautersee', 'rund um die Dhünntalsperre', 'rund um das Pulvermaar'. */
    fun um(name: String): String {
        val low = name.lowercase()
        return Txt.s(if (feminine(low)) Res.string.suggest_round_f else if (low.endsWith("maar")) Res.string.suggest_round_n
                     else Res.string.suggest_round_m, name)
    }

    private fun isReserve(nm: String) = "schutzgebiet" in nm.lowercase() || nm.startsWith("Nationalpark") || nm.startsWith("Naturpark")

    /** a protected area's name with its kind when the name does not say it ("Schutzgebiet Ohligser Heide"). */
    private fun reserve(nm: String) = if (isReserve(nm)) nm else Txt.s(Res.string.suggest_reserve, nm)
    private fun reserveThe(nm: String) = if (isReserve(nm)) nm else Txt.s(Res.string.suggest_reserve_the, nm)

    /** "a und b", "a, b und c". */
    fun both(xs: List<String>): String =
        if (xs.size < 3) (if (xs.size == 2) Txt.s(Res.string.suggest_and, xs[0], xs[1]) else xs.joinToString(""))
        else Txt.s(Res.string.suggest_and, xs.dropLast(1).joinToString(", "), xs.last())

    /**
     * score.py `aim_bits`: the aims a loop reaches as words for its line – a summit with its height, a lake gone
     * round, a place to eat in the middle, a spring, a museum (castles, chapels, viewpoints, waterfalls are said with
     * the sights).
     */
    fun aimBits(aims: List<Aims.Reached>): List<String> {
        val out = ArrayList<String>()
        var food = false  // one place to eat: the one nearest the middle
        for (a in aims.sortedWith(compareBy<Aims.Reached>({ !it.goal }, { it.at }))) {
            val nm = a.name
            when {
                a.what == "peak" -> out.add(if (a.ele != 0) Txt.s(Res.string.suggest_aim_peak_height, nm, a.ele.toString()) else Txt.s(Res.string.suggest_aim_peak, nm))
                a.what == "lake" && a.round >= 0.7 -> out.add(um(nm))
                a.what in FOOD && a.at >= 0.2 && a.at <= 0.8 && !food -> {
                    var mid: Aims.Reached? = null
                    for (b in aims) if (b.ziel == "einkehr" && (mid == null || Math.abs(b.at - 0.5) < Math.abs(mid.at - 0.5))) mid = b
                    out.add(Txt.s(Res.string.suggest_aim_food, mid!!.name))
                    food = true
                }
                a.what == "spring" -> out.add(if ("quelle" in nm.lowercase() || "brunn" in nm.lowercase()) nm else Txt.s(Res.string.suggest_aim_spring, nm))
                a.what == "museum" -> out.add(if ("museum" in nm.lowercase()) nm else Txt.s(Res.string.suggest_aim_museum, nm))
            }
        }
        return out.distinct().take(3)
    }

    private val FOOD = setOf("hut", "restaurant", "cafe")

    /** score.py `explain` reasons: what a loop offers, at most five. */
    fun reasons(n: Candidate.Numbers): List<String> {
        val out = ArrayList<String>(aimBits(n.aims))
        for ((nm, k) in n.reserves) out.add(Txt.s(Res.string.suggest_km_in, num(k), reserve(nm)))
        for ((nm, k) in n.lakes) out.add(kmAt(num(k), nm))
        for ((nm, k) in n.known) out.add(Txt.s(Res.string.suggest_km_on, num(k), nm))
        if (n.waterKm >= 1 && n.lakes.isEmpty()) out.add(Txt.s(Res.string.suggest_km_water, num(n.waterKm)))
        if (n.railKm >= 1) out.add(Txt.s(Res.string.suggest_km_rail, num(n.railKm)))
        for ((w, nm) in n.sights.take(1)) out.add(if (nm != w) Txt.s(Res.string.suggest_sight_named, sight(w), nm) else sight(w))
        if (n.ferryM > 0) out.add(Txt.s(Res.string.suggest_ferry))
        return out.take(5)
    }

    private val OWN_WORD = listOf("burg", "schloss", "ruine", "kapelle", "höhle", "wasserfall", "turm", "blick", "kirche", "aussicht", "fels")

    /** score.py `line`: one line on what the loop really passes – from the card's numbers, nothing invented. */
    fun line(n: Candidate.Numbers, known: List<Pair<String, Double>> = n.known): String {
        fun say(what: String, nm: String) =
            if (nm != what && OWN_WORD.any { it in nm.lowercase() }) nm
            else if (nm == what) sight(what) else Txt.s(Res.string.suggest_sight_named, sight(what), nm)
        val named = n.sights.filter { it.first != "Aussichtspunkt" && it.second != it.first }
        val namedWhat = named.map { it.first }.toSet()
        val things = named.map { say(it.first, it.second) } +
            n.sights.filter { it.second == it.first && it.first != "Aussichtspunkt" && it.first !in namedWhat }.map { sight(it.first) }
        val views = n.sights.filter { it.first == "Aussichtspunkt" }.map { it.second }
        val rounds = n.aims.filter { it.what == "lake" && it.round >= 0.7 }.map { it.name }.toSet()
        val bits = ArrayList<String>(aimBits(n.aims))
        if (n.reserves.isNotEmpty()) bits.add(Txt.s(Res.string.suggest_line_through, both(n.reserves.take(2).map { reserveThe(Score.short(it.first)) })))
        if (n.lakes.isNotEmpty() && n.lakes[0].first !in rounds) bits.add(am(n.lakes[0].first))
        if (things.isNotEmpty()) bits.add(Txt.s(Res.string.suggest_line_past, both(things.distinct().take(3))))
        if (n.rimKm >= 0.5) bits.add(Txt.s(Res.string.suggest_line_rim, num(n.rimKm)))
        if (views.isNotEmpty()) {
            bits.add(if (views[0] != "Aussichtspunkt") Txt.s(Res.string.suggest_line_view, views[0]) else Txt.s(Res.string.suggest_line_view_any))
        }
        for ((nm, k) in known.take(1)) if (k >= 0.25 * n.km) bits.add(Txt.s(Res.string.suggest_km_on, num(k), Score.shortRoute(nm)))
        if (n.waterKm >= 1 && n.lakes.isEmpty()) bits.add(Txt.s(Res.string.suggest_km_water, num(n.waterKm)))
        if (n.railKm >= 1) bits.add(Txt.s(Res.string.suggest_km_rail, num(n.railKm)))
        val text = if (bits.isNotEmpty()) bits.joinToString(", ")
        else Txt.s(Res.string.suggest_line_plain, n.forestPct.toString(), (n.groundPct[2] + n.groundPct[3]).toString())
        return text.take(1).uppercase() + text.drop(1)
    }

    /** score.py `explain` hints: what does not fit the wish quite, and what to know. */
    fun hints(c: Candidate, ch: Character): List<String> {
        val n = c.numbers; val h = c.hours
        fun edge(v: Double, lo: Double, hi: Double): Double = if (hi > lo) (v - lo) / (hi - lo) else 0.0
        val out = ArrayList<String>()
        if (c.up > ch.up[1]) out.add(Txt.s(Res.string.suggest_hint_ascent_over, c.up.toInt().toString(), ch.up[1].toInt().toString()))
        if (h < ch.hours[0]) out.add(Txt.s(Res.string.suggest_hint_shorter))
        else if (h > ch.hours[1]) out.add(Txt.s(Res.string.suggest_hint_longer))
        else if (edge(h, ch.hours[0], ch.hours[1]) > 0.9) out.add(Txt.s(Res.string.suggest_hint_time_edge))
        if (c.up < ch.up[0]) out.add(Txt.s(Res.string.suggest_hint_less_ascent))
        else { val e = edge(c.up, ch.up[0], ch.up[1]); if (0.9 < e && e <= 1.0) out.add(Txt.s(Res.string.suggest_hint_ascent_edge)) }
        if (n.roadLongestM >= 200) out.add(Txt.s(Res.string.suggest_hint_road, n.roadLongestM.toString()))
        if (n.doubledPct >= 8) out.add(Txt.s(Res.string.suggest_hint_doubled, n.doubledPct.toString()))
        if (n.selfCross != 0) out.add(Txt.s(Res.string.suggest_hint_crosses))
        if (n.townPct >= 30) out.add(Txt.s(Res.string.suggest_hint_town, n.townPct.toString()))
        return out
    }

    /** The hard rule's reason on foot (Score.finish). */
    fun outRoad(): String = Txt.s(Res.string.suggest_out_road)

    private val AXIS = arrayOf(Res.string.suggest_axis_natur, Res.string.suggest_axis_ruhe, Res.string.suggest_axis_weg,
        Res.string.suggest_axis_besonderes, Res.string.suggest_axis_form)
    private val PART = mapOf(Score.ZEIT to Res.string.suggest_form_time, Score.HOEHE to Res.string.suggest_form_ascent,
        Score.DOPPELT to Res.string.suggest_form_doubled, Score.KREUZT to Res.string.suggest_form_crosses)

    /** score.py `explain` why: "stark bei Natur und Ruhe · schwächer bei Form (Zeit)". */
    fun why(c: Candidate): String {
        val ranked = (0 until 5).sortedBy { -c.axes[it] }
        val weak = ranked.reversed().filter { c.axes[it] < 0.5 }.take(2)
        val strong = Txt.s(Res.string.suggest_why_strong, both(ranked.take(2).map { Txt.s(AXIS[it]) }))
        if (weak.isEmpty()) return strong
        val words = weak.map { a ->
            val w = Txt.s(AXIS[a])
            if (a == 4 && c.formParts.isNotEmpty()) Txt.s(Res.string.suggest_why_part, w, c.formParts.keys.joinToString(", ") { k -> PART[k]?.let { Txt.s(it) } ?: k })
            else w
        }
        return strong + " · " + Txt.s(Res.string.suggest_why_weak, both(words))
    }

    // --- the run's notes (suggest.py)
    fun noteMapUpdating() = Txt.s(Res.string.suggest_note_map_updating)
    fun noteStartMoved(metres: Int) = Txt.s(Res.string.suggest_note_start_moved, metres.toString())
    fun noteSteep(upTo: Int, least: Int) = Txt.s(Res.string.suggest_note_steep, upTo.toString(), least.toString())
    fun noteNone() = Txt.s(Res.string.suggest_note_none)
    fun noteLessAscent() = Txt.s(Res.string.suggest_note_less_ascent)
}
