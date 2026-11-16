package com.onpath.app.suggest


/**
 * The wish of a tour suggestion (docs/design/tour-suggestions.md §2), complete: start, activity (+ T level for hikes),
 * the two ranges (hours, metres up – [ascentHi] null = "what is usual"), the few controls. Build with [of], which
 * fills and clamps like the POC's `character.wish`.
 */
class Wish(
    val lon: Double,
    val lat: Double,
    val activity: String,
    val tLevel: Int,
    val hours: DoubleArray,
    val pace: Double,
    /** null = no ascent wished (from flat up to what is usual) */
    val ascent: DoubleArray?,
    /** the upper end of [ascent]; null = not given ("ab 300") */
    val ascentHi: Double?,
    val natur: String,
    val ruhe: String,
    val bekannt: String,
    val wasser: Boolean,
    /** official signed loops near the start join the list (the page's default) */
    val official: Boolean = true,
) {
    companion object {
        /**
         * character.py `wish`: [activity] hike / bike / gravel / mtb (else hike); [hours] one number (widened by the
         * family's band) or [from, to]; [ascent] null or [from, to] with either end null; levels niedrig / mittel /
         * hoch (else mittel).
         */
        fun of(S: SuggestSettings, lon: Double, lat: Double, activity: String? = null, tLevel: Int? = null,
               hours: DoubleArray = doubleArrayOf(2.0), pace: Double? = null, ascent: Array<Double?>? = null,
               natur: String? = null, ruhe: String? = null, bekannt: String? = null, wasser: Boolean = false,
               official: Boolean = true): Wish {
            val act = if (activity != null && activity != "_about" && S["activities"].has(activity)) activity else "hike"
            val A = S["activities"][act]
            val p = pace ?: A["pace"].d
            val levels = S["controls"]["level"]
            fun word(v: String?) = if (v != null && levels.has(v)) v else "mittel"
            val h = if (hours.size >= 2) {
                val a = hours[0].coerceIn(0.25, 10.0); val b = hours[1].coerceIn(0.25, 10.0)
                doubleArrayOf(minOf(a, b), maxOf(a, b))
            } else {
                val band = S["family"][A["family"].s]["band_km"].d / p
                doubleArrayOf(maxOf(0.25, hours[0] - band), minOf(10.0, hours[0] + band))
            }
            var up: DoubleArray? = null
            var upHi: Double? = null
            if (ascent != null && ascent.size == 2 && (ascent[0] != null || ascent[1] != null)) {
                val lo = ascent[0]?.let { maxOf(0.0, it) } ?: 0.0
                val hi = ascent[1]?.let { maxOf(0.0, it) }
                if (hi != null) { up = doubleArrayOf(minOf(lo, hi), maxOf(lo, hi)); upHi = maxOf(lo, hi) } else up = doubleArrayOf(lo, lo)
            }
            return Wish(lon, lat, act, (tLevel ?: 2).coerceIn(1, 5), h, p, up, upHi, word(natur), word(ruhe), word(bekannt),
                wasser, official)
        }
    }
}

/**
 * Level 3 – what this person wants (character.py): one scale gemütlich … wild from the activity and the T level; the
 * five axis weights, how much of Natur is depth, the worth of a signed route, the welcome of each ground kind, the
 * two ranges in time and metres up, BRouter's profile and parameters for the network.
 */
class Character(val wish: Wish, S: SuggestSettings) {
    val A = S["activities"][wish.activity]
    val familyName: String = A["family"].s
    val family = S["family"][familyName]
    val bike = familyName == "wheel"
    val profile: String = A["profile"].s
    val pace = wish.pace
    val wild: Double = if (A["wild"].isList) A["wild"][wish.tLevel - 1].d else A["wild"].d
    /** natur, ruhe, weg, form, besonderes – normalised to sum 1 */
    val axes = LinkedHashMap<String, Double>()
    val depth: Double
    val signed: Double
    /** welcome of ground 0 paved · 1 firm · 2 natural · 3 rough */
    val ground: DoubleArray
    val water = wish.wasser
    val hours: DoubleArray = wish.hours
    val minM = hours[0] * pace * 1000.0
    val maxM = hours[1] * pace * 1000.0
    val targetM = 0.5 * (minM + maxM)
    val usualUp: Double
    /** the wished ascent range [from, to] */
    val up: DoubleArray
    /** BRouter parameters of the POC's network (character.py `kv`) */
    val kv = LinkedHashMap<String, String>()
    val freeStemM = family["free_stem_m"].d
    val ascentMPerH = family["ascent_m_per_h"].d

    private fun mix(a: Double, b: Double, t: Double = wild) = a + (b - a) * t.coerceIn(0.0, 1.0)

    init {
        val C = S["character"]
        val K = S["controls"]
        val g = C["gemütlich"]; val w = C["wild"]
        for (k in g["axes"].keys) axes[k] = mix(g["axes"][k].d, w["axes"][k].d)
        axes["natur"] = axes.getValue("natur") * K["level"][wish.natur].d
        axes["ruhe"] = axes.getValue("ruhe") * K["level"][wish.ruhe].d
        if (water) axes["besonderes"] = axes.getValue("besonderes") * K["water"].d
        var total = 0.0
        for (v in axes.values) total += v
        for (k in axes.keys.toList()) axes[k] = axes.getValue(k) / total
        depth = mix(g["depth"].d, w["depth"].d)
        signed = minOf(1.0, mix(g["signed"].d, w["signed"].d) * K["level"][wish.bekannt].d)
        val gr = family["ground"]
        ground = DoubleArray(4) { mix(gr["gemütlich"][it].d, gr["wild"][it].d) }
        val upH = family["up_m_per_h"]
        usualUp = SuggestGeo.pyRound(mix(upH[0].d, upH[1].d) * hours[1], -1)
        val lo = wish.ascent?.get(0) ?: 0.0
        val hi = if (wish.ascent == null) null else wish.ascentHi
        up = doubleArrayOf(lo, hi ?: maxOf(usualUp, lo + usualUp))
        if (bike) kv["consider_traffic"] = "1" else {
            kv["SAC_scale_limit"] = wish.tLevel.toString()
            kv["SAC_scale_preferred"] = minOf(wish.tLevel, 3).toString()
        }
        kv["mainroad_penalty"] = "3.0"
    }

    fun axis(name: String): Double = axes.getValue(name)

    /** Hours for a walk of [metres] with [up] metres of ascent (the activity's pace on the flat). */
    fun time(metres: Double, up: Double): Double = metres / 1000.0 / pace + up / ascentMPerH

    fun word(): String = if (wild < 0.25) "gemütlich" else if (wild < 0.5) "naturnah" else if (wild < 0.75) "abgelegen" else "wild"

    /** How much way network is loaded around the start (suggest.py: the family's reach, at least 1.5 km). */
    fun radius(S: SuggestSettings): Double = minOf(family["reach_max_m"].d, maxOf(1500.0, S["search"]["radius"].d * maxM))
}
