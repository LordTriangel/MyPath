package com.onpath.app.time

import com.onpath.app.gpx.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** The look back at a done tour (owner 2026-11-15, round 3): what the stored points give, and nothing they do not. */
class TourReviewTest {
    private val t0 = 1_763_200_000_000L
    /** Degrees of longitude per metre at 47° N. */
    private val lonPerM = 1.0 / (111_320.0 * cos(Math.toRadians(47.0)))

    /** [n] fixes walking east, one per [stepS] seconds, [stepM] metres apart; [ele] by index (null = no height). */
    private fun walk(startMs: Long, fromM: Double, n: Int, stepS: Int = 10, stepM: Double = 10.0,
                     ele: (Int) -> Double? = { 500.0 }): List<TrackPoint> =
        (0 until n).map { i -> TrackPoint(47.0, 11.0 + (fromM + i * stepM) * lonPerM, ele(i), startMs + i * stepS * 1000L) }

    /** [n] fixes standing at [atM] for [n] × 10 s. */
    private fun stand(startMs: Long, atM: Double, n: Int) =
        (0 until n).map { i -> TrackPoint(47.0, 11.0 + atM * lonPerM, 500.0, startMs + i * 10_000L) }

    @Test fun aSteadyWalkGivesTimesSpeedAndTheFastestKm() {
        // 3 km at 1 m/s, then a bit over 1 km at 2 m/s (the metres here are ~0.1 % short of Geo's)
        val slow = walk(t0, 0.0, 301)
        val fast = walk(t0 + 3_000_000L + 5_000L, 3010.0, 103, stepS = 5)
        val r = TourReview.of(listOf(slow + fast), times = null, durationS = 0.0, distanceM = 4000.0)
        assertEquals(3515.0, r.totalS!!, 1.0)
        assertEquals(0, r.pauseCount)
        assertEquals(r.totalS!!, r.movingS!!, 1.0)
        assertEquals(0.0, r.pauseS!!, 1.0)
        assertEquals(4000.0 / 1000 / (r.movingS!! / 3600), r.avgKmh!!, 0.01)
        val km = r.fastestKm!!
        assertEquals(4000.0, km.atM, 0.1)
        assertEquals(500.0, km.value, 10.0) // 1000 m at 2 m/s
    }

    @Test fun aStopInTheMiddleIsAPauseAtItsPlace() {
        val a = walk(t0, 0.0, 101)                                   // 1000 m, 1000 s
        val s = stand(t0 + 1_010_000L, 1000.0, 90)                     // 15 min standing
        val b = walk(t0 + 1_910_000L, 1000.0, 101)
        val r = TourReview.of(listOf(a + s + b), times = null, durationS = 0.0, distanceM = 2000.0)
        assertEquals(1, r.pauseCount)
        val p = r.longestPause!!
        assertTrue("about 15 min: ${p.value}", p.value in 800.0..1000.0)
        assertEquals(1000.0, p.atM, 30.0)
        assertTrue(r.movingS!! < r.totalS!! - 700)
    }

    @Test fun anhaltenBetweenTwoPartsIsAPause() {
        val a = walk(t0, 0.0, 101)
        val b = walk(t0 + 1_000_000L + 25 * 60_000L, 1000.0, 101)    // 25 min later, a new part
        val r = TourReview.of(listOf(a, b), times = null, durationS = 0.0, distanceM = 2000.0)
        assertEquals(1, r.pauseCount)
        assertEquals(25 * 60.0, r.longestPause!!.value, 1.0)
        assertEquals(1000.0, r.longestPause!!.atM, 5.0)
    }

    @Test fun theRecordingsOwnTimesWinAndThePlanLineFollowsItsRule() {
        val pts = walk(t0, 0.0, 301)
        val times = TourTimes(movingS = 2400.0, breakS = 300.0, plannedS = 3000.0, neededS = 2640.0, startMs = t0, endMs = t0 + 3_600_000L)
        val r = TourReview.of(listOf(pts), times, durationS = 0.0, distanceM = 3000.0)
        assertEquals(3600.0, r.totalS!!, 0.1)
        assertEquals(2400.0, r.movingS!!, 0.1)
        assertEquals(1200.0, r.pauseS!!, 0.1) // on the way − moving: stops and Anhalten
        assertEquals(12, r.planPct)             // 3000 planned, 2640 needed
        // slower than planned: negative; under 10 min on the route: no line (TourTimes.planLine's rule)
        assertEquals(-10, TourReview.of(listOf(pts), times.copy(neededS = 3300.0), 0.0, 3000.0).planPct)
        assertNull(TourReview.of(listOf(pts), times.copy(plannedS = 500.0), 0.0, 3000.0).planPct)
        assertNull(TourReview.of(listOf(pts), times.copy(plannedS = null), 0.0, 3000.0).planPct)
    }

    /**
     * My routes' row (owner 2026-11-16) shows the overview's „Ø Tempo“ from the stored times alone: the same number as
     * the review whenever the times know start and end – else nothing (never a number computed another way).
     */
    @Test fun theRowsAverageIsTheReviewsFromTheStoredTimes() {
        val pts = walk(t0, 0.0, 301)
        val times = TourTimes(movingS = 2400.0, breakS = 300.0, startMs = t0, endMs = t0 + 3_600_000L)
        val review = TourReview.of(listOf(pts), times, durationS = 0.0, distanceM = 3000.0)
        assertEquals(4.5, review.avgKmh!!, 0.001) // 3 km in 40 min moving
        assertEquals(review.avgKmh!!, TourReview.averageFromTimes(times, 3000.0)!!, 1e-9)
        // moving longer than on the way: both cap it at on the way
        val over = times.copy(movingS = 5000.0)
        assertEquals(TourReview.of(listOf(pts), over, 0.0, 3000.0).avgKmh!!, TourReview.averageFromTimes(over, 3000.0)!!, 1e-9)
        // start / end not known (tours before 2026-11-01): the review needs the points – the row says nothing
        assertNull(TourReview.averageFromTimes(times.copy(startMs = null), 3000.0))
        assertNull(TourReview.averageFromTimes(times.copy(endMs = t0), 3000.0))
        assertNull(TourReview.averageFromTimes(null, 3000.0))
        // under a minute of moving time, no length: no average, as in the review
        assertNull(TourReview.averageFromTimes(times.copy(movingS = 50.0), 3000.0))
        assertNull(TourReview.of(listOf(pts), times.copy(movingS = 50.0), 0.0, 3000.0).avgKmh)
        assertNull(TourReview.averageFromTimes(times, 0.0))
    }

    @Test fun noTimesAtAllSaysNothingAboutMovingOrPauses() {
        val pts = walk(t0, 0.0, 301).map { it.copy(timeMs = null) }
        val r = TourReview.of(listOf(pts), times = null, durationS = 5400.0, distanceM = 3000.0)
        assertEquals(5400.0, r.totalS!!, 0.1) // the tour's stored duration
        assertNull(r.movingS); assertNull(r.pauseS); assertNull(r.pauseCount); assertNull(r.avgKmh)
        assertNull(r.fastestKm); assertNull(r.longestPause); assertNull(r.upMh)
        assertTrue(r.profile.isNotEmpty()) // heights without times still draw the profile
    }

    @Test fun onePointOrNothingNeverThrows() {
        val one = TourReview.of(listOf(listOf(TrackPoint(47.0, 11.0, null, t0))), null, 0.0, 0.0)
        assertNull(one.totalS); assertNull(one.highest); assertTrue(one.profile.isEmpty()); assertNull(one.fastestKm)
        val none = TourReview.of(emptyList(), null, 120.0, 0.0)
        assertEquals(120.0, none.totalS!!, 0.1)
        assertNull(none.pauseCount)
        TourReview.of(listOf(emptyList(), emptyList()), null, 0.0, 0.0)
    }

    @Test fun standingTheWholeTimeIsAllPause() {
        val r = TourReview.of(listOf(stand(t0, 0.0, 180)), null, 0.0, 0.0)
        assertEquals(1, r.pauseCount)
        assertNull(r.movingS) // nothing left moving
        assertNull(r.avgKmh)
    }

    @Test fun highestPointAndClimbRates() {
        // 2 km up 300 m in 40 min, 2 km down 300 m in 20 min
        val up = walk(t0, 0.0, 201, stepS = 12, ele = { i -> 500.0 + i * 1.5 })
        val down = walk(t0 + 2_412_000L, 2010.0, 200, stepS = 6, ele = { i -> 800.0 - (i + 1) * 1.5 })
        val r = TourReview.of(listOf(up + down), null, 0.0, 4000.0)
        assertEquals(800.0, r.highest!!.value, 0.1)
        assertEquals(2000.0, r.highest!!.atM, 15.0)
        assertEquals(450.0, r.upMh!!, 25.0)   // 300 m in 40 min
        assertEquals(900.0, r.downMh!!, 50.0) // 300 m in 20 min
        assertTrue(r.profile.size <= TourReview.PROFILE_MAX + 1)
        assertTrue(r.profile.any { it.ele == 800.0 })
    }

    @Test fun aFlatWalkHasNoClimbRates() {
        val r = TourReview.of(listOf(walk(t0, 0.0, 301)), null, 0.0, 3000.0)
        assertNull(r.upMh); assertNull(r.downMh)
    }

    @Test fun rankAmongComparableToursFromThreeOn() {
        assertNull(TourReview.rankOf(5000.0, listOf(5000.0, 9000.0)))
        assertEquals(TourReview.Rank(2, 3), TourReview.rankOf(5000.0, listOf(5000.0, 9000.0, 3000.0)))
        assertEquals(TourReview.Rank(1, 4), TourReview.rankOf(9000.0, listOf(5000.0, 9000.0, 3000.0, 9000.0)))
    }

    @Test fun profileIsThinnedButKeepsItsEnds() {
        val pts = walk(t0, 0.0, 1001, ele = { i -> 400.0 + (i % 50) })
        val p = TourReview.profileOf(TourReview.alongTrack(listOf(pts)))
        assertTrue(p.size in 200..TourReview.PROFILE_MAX + 2)
        assertEquals(0.0, p.first().atM, 0.1)
        assertEquals(10_000.0, p.last().atM, 30.0)
        assertNotNull(p.maxByOrNull { it.ele })
    }
}
