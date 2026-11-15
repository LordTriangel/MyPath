package com.onpath.app.time

import com.onpath.app.data.Geo
import com.onpath.app.gpx.TrackPoint
import kotlin.math.roundToInt

/**
 * The look back at a done tour in its large overview (owner 2026-11-15, round 3 of the tour analysis): the numbers a
 * recorded track really gives – times, pauses, speeds, the plan comparison, three highlights and the small profile.
 * Every value is null when the data does not give it (no times, no heights, too short); the screen leaves that line out,
 * never a placeholder. Pure: computed off the main thread from the stored points ([of]), never throws on odd data.
 */
data class TourReview(
    /** On the way, start to end (stops and Anhalten included) – "Gesamtzeit". */
    val totalS: Double? = null,
    val movingS: Double? = null,
    /** The time not moving: on the way − moving (stops and Anhalten). */
    val pauseS: Double? = null,
    /** How many pauses the points show (stops of ≥ 2 min, Anhalten / long GPS gaps); null = not known. */
    val pauseCount: Int? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    /** Average speed while moving (km/h). */
    val avgKmh: Double? = null,
    /** Height gained per hour on the climbing parts / lost per hour on the descending parts (m/h). */
    val upMh: Double? = null,
    val downMh: Double? = null,
    /** Planned vs needed on the followed route (TourTimes.planLine's rule): + = faster than planned, in %. */
    val planPct: Int? = null,
    val plannedS: Double? = null,
    val neededS: Double? = null,
    /** The longest pause and where it began (m along the track). */
    val longestPause: AtTrack? = null,
    /** The highest point: [AtTrack.value] = height (m). */
    val highest: AtTrack? = null,
    /** The fastest whole km: [AtTrack.value] = seconds for it, [AtTrack.atM] = its end (k × 1000 m). */
    val fastestKm: AtTrack? = null,
    /** The height profile along the track (≤ [PROFILE_MAX] points); empty = no heights. */
    val profile: List<ProfilePoint> = emptyList(),
    /** Its place among the user's done tours of the same activity (and T level for a hike) by length; null = < [RANK_MIN]. */
    val rank: Rank? = null,
) {
    /** A value and where along the track it is (m from the start). */
    data class AtTrack(val value: Double, val atM: Double)
    data class ProfilePoint(val atM: Double, val ele: Double)
    /** [place]-longest of [of] tours. */
    data class Rank(val place: Int, val of: Int)

    companion object {
        const val PROFILE_MAX = 240
        /** A rank is shown from this many comparable tours on (fewer say nothing). */
        const val RANK_MIN = 3
        /** The climbing / descending speeds are measured on pieces of about this length … */
        const val PIECE_M = 100.0
        /** … a piece is up or down from this grade on (else flat) … */
        const val PIECE_GRADE = 0.04
        /** … standing (slower than this, m/s) or a long gap inside a piece: not counted. */
        const val PIECE_MIN_SPEED = 0.2
        const val PIECE_MAX_S = 15 * 60.0
        /** A climbing / descending speed needs this much time on such pieces … */
        const val RATE_MIN_S = 10 * 60.0
        /** … and this much height. */
        const val RATE_MIN_M = 50.0
        /** A gap between two recorded parts (Anhalten, GPS lost) is a pause from this long on – the stop rule's 2 min. */
        const val GAP_PAUSE_S = BreakDetector.MIN_S

        /**
         * The review of a done tour: [segments] = its stored points (rides left out as chosen), [times] = its TourTimes
         * (null = not known: computed from the points), [durationS] = the tour's stored duration (the last fallback),
         * [distanceM] = its stored length, [lengths] = the lengths of the user's comparable done tours, this one included.
         */
        fun of(segments: List<List<TrackPoint>>, times: TourTimes?, durationS: Double, distanceM: Double,
               lengths: List<Double> = emptyList()): TourReview {
            val segs = segments.filter { it.isNotEmpty() }
            val pauses = pausesIn(segs)
            val timed = segs.flatten().mapNotNull { it.timeMs }
            val start = times?.startMs ?: timed.minOrNull()
            val end = times?.endMs ?: timed.maxOrNull()
            val total = if (start != null && end != null && end > start) (end - start) / 1000.0
                else durationS.takeIf { it > 0 }
            // moving time: the recording's own, else on the way − the pauses in the points (no times at all: not known)
            val fromPoints = if (timed.isNotEmpty()) total?.let { it - pauses.sumOf { p -> p.value } } else null
            val moving = (times?.movingS ?: fromPoints)?.takeIf { it > 0 }
                ?.let { m -> total?.let { m.coerceAtMost(it) } ?: m }
            val pause = if (total != null && moving != null) (total - moving).coerceAtLeast(0.0) else times?.breakS
            val (up, down) = climbRates(segs)
            val along = alongTrack(segs)
            val p = times?.plannedS; val n = times?.neededS
            val planOk = p != null && n != null && p >= 600 && n > 0
            return TourReview(
                totalS = total, movingS = moving, pauseS = pause,
                pauseCount = pauses.size.takeIf { timed.isNotEmpty() },
                startMs = start?.takeIf { end != null && end > it }, endMs = end?.takeIf { start != null && it > start },
                avgKmh = moving?.takeIf { it >= 60 && distanceM > 0 }?.let { distanceM / 1000.0 / (it / 3600.0) },
                upMh = up, downMh = down,
                planPct = if (planOk) ((p!! - n!!) / p * 100).roundToInt() else null,
                plannedS = p.takeIf { planOk }, neededS = n.takeIf { planOk },
                longestPause = pauses.maxByOrNull { it.value },
                highest = along.filter { it.ele != null }.maxByOrNull { it.ele!! }?.let { AtTrack(it.ele!!, it.atM) },
                fastestKm = fastestKm(along),
                profile = profileOf(along),
                rank = rankOf(distanceM, lengths),
            )
        }

        /** A point with its distance along the track (gaps between segments not counted) and its time. */
        internal class Along(val atM: Double, val ele: Double?, val timeMs: Long?)

        internal fun alongTrack(segs: List<List<TrackPoint>>): List<Along> {
            val out = ArrayList<Along>()
            var at = 0.0
            for (seg in segs) {
                var prev: TrackPoint? = null
                for (p in seg) {
                    prev?.let { at += Geo.distance(it.lat, it.lon, p.lat, p.lon) }
                    out += Along(at, p.ele?.takeIf { e -> !e.isNaN() }, p.timeMs)
                    prev = p
                }
            }
            return out
        }

        /**
         * The pauses in the points: the stops [BreakDetector] finds in each recorded part (its rule: 2 min within a
         * few metres) and the gaps between parts of at least [GAP_PAUSE_S] (Anhalten, a GPS gap). Each with its length
         * (s) and where it began (m along the track).
         */
        internal fun pausesIn(segs: List<List<TrackPoint>>): List<AtTrack> {
            val out = ArrayList<AtTrack>()
            var at = 0.0
            var lastTime: Long? = null
            for (seg in segs) {
                val firstTime = seg.firstOrNull { it.timeMs != null }?.timeMs
                if (lastTime != null && firstTime != null && (firstTime - lastTime) / 1000.0 >= GAP_PAUSE_S)
                    out += AtTrack((firstTime - lastTime) / 1000.0, at)
                val b = BreakDetector()
                var prev: TrackPoint? = null
                var current: AtTrack? = null
                var prevAt = at
                for (p in seg) {
                    prev?.let { at += Geo.distance(it.lat, it.lon, p.lat, p.lon) }
                    val t = p.timeMs
                    if (t != null && firstTime != null) {
                        val wasIn = b.inBreak; val before = b.breakS()
                        b.onFix((t - firstTime) / 1000.0, p.lat, p.lon)
                        val d = b.breakS() - before
                        when {
                            !wasIn && b.inBreak -> { current?.let { out += it }; current = AtTrack(d, at) }
                            wasIn && b.inBreak -> current = current?.let { AtTrack(it.value + d, it.atM) } ?: AtTrack(d, at)
                            else -> {
                                // a stop ended here; a long gap without moving right before this fix is a pause of its own
                                current?.let { out += it }; current = null
                                if (d > 0) out += AtTrack(d, prevAt)
                            }
                        }
                        lastTime = t
                    }
                    prevAt = at
                    prev = p
                }
                current?.let { out += it }
            }
            return out.filter { it.value > 0 }
        }

        /**
         * Height per hour on the climbing and on the descending parts: the track cut into pieces of ~[PIECE_M], each up,
         * down or flat by its grade; standing and long gaps left out. Null for a side with too little to say.
         */
        internal fun climbRates(segs: List<List<TrackPoint>>): Pair<Double?, Double?> {
            var upM = 0.0; var upS = 0.0; var downM = 0.0; var downS = 0.0
            for (seg in segs) {
                val pts = seg.filter { it.timeMs != null && it.ele != null && !it.ele.isNaN() }
                var i = 0
                while (i < pts.size - 1) {
                    var j = i; var len = 0.0
                    while (j < pts.size - 1 && len < PIECE_M) { len += Geo.distance(pts[j].lat, pts[j].lon, pts[j + 1].lat, pts[j + 1].lon); j++ }
                    val s = (pts[j].timeMs!! - pts[i].timeMs!!) / 1000.0
                    val dh = pts[j].ele!! - pts[i].ele!!
                    if (len >= PIECE_M / 2 && s > 0 && s <= PIECE_MAX_S && len / s >= PIECE_MIN_SPEED) {
                        if (dh >= PIECE_GRADE * len) { upM += dh; upS += s }
                        else if (-dh >= PIECE_GRADE * len) { downM -= dh; downS += s }
                    }
                    i = j
                }
            }
            fun rate(m: Double, s: Double) = if (s >= RATE_MIN_S && m >= RATE_MIN_M) m / (s / 3600.0) else null
            return rate(upM, upS) to rate(downM, downS)
        }

        /** The fastest whole kilometre: the time between passing k−1 and k km (interpolated); null under 2 whole km. */
        internal fun fastestKm(along: List<Along>): AtTrack? {
            val timed = along.filter { it.timeMs != null }
            if (timed.size < 2 || timed.last().atM < 2000) return null
            fun timeAt(m: Double): Double? {
                val k = timed.indexOfFirst { it.atM >= m }
                if (k <= 0) return if (k == 0) timed[0].timeMs!!.toDouble() else null
                val a = timed[k - 1]; val b = timed[k]
                val f = if (b.atM > a.atM) (m - a.atM) / (b.atM - a.atM) else 0.0
                return a.timeMs!! + f * (b.timeMs!! - a.timeMs!!)
            }
            var best: AtTrack? = null
            var km = 1
            while (km * 1000.0 <= timed.last().atM) {
                val t0 = timeAt((km - 1) * 1000.0); val t1 = timeAt(km * 1000.0)
                if (t0 != null && t1 != null && t1 > t0) {
                    val s = (t1 - t0) / 1000.0
                    if (best == null || s < best.value) best = AtTrack(s, km * 1000.0)
                }
                km++
            }
            return best
        }

        internal fun profileOf(along: List<Along>): List<ProfilePoint> {
            val withEle = along.filter { it.ele != null }
            if (withEle.size < 2 || withEle.last().atM <= withEle.first().atM) return emptyList()
            val step = (withEle.size + PROFILE_MAX - 1) / PROFILE_MAX
            val picked = withEle.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
            if (picked.last() !== withEle.last()) picked += withEle.last()
            // the highest point always on the line (its dot sits on it)
            val top = withEle.maxBy { it.ele!! }
            if (picked.none { it === top }) { picked += top; picked.sortBy { it.atM } }
            return picked.map { ProfilePoint(it.atM, it.ele!!) }
        }

        /** This tour's place by length among [lengths] (this one included), from [RANK_MIN] tours on. */
        internal fun rankOf(distanceM: Double, lengths: List<Double>): Rank? {
            if (lengths.size < RANK_MIN) return null
            return Rank(lengths.count { it > distanceM } + 1, lengths.size)
        }
    }
}
