package com.onpath.app.navigation

import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos

/** When side and metres may be said off the route – every doubt is the plain sentence (owner 2026-11-21). */
class OffRouteSayTest {
    private val lat0 = 50.0
    private fun pt(x: Double, y: Double) = GeoPoint(lat0 + y / 111_320.0, 8.0 + x / (111_320.0 * cos(Math.toRadians(lat0))))

    /** Walk the points one a second (from [t0]) into [s]; returns the time of the last fix. */
    private fun walk(s: OffRouteSay, pts: List<GeoPoint>, t0: Long = 0, acc: Double = 8.0, bearing: Double? = null): Long {
        var t = t0
        for (p in pts) { s.onFix(p, acc, bearing, 1.4, t); t += 1000 }
        return t - 1000
    }

    private fun north(from: Double, to: Double) = (from.toInt()..to.toInt() step 4).map { pt(0.0, it.toDouble()) }
    private fun st(offM: Double? = 40.0, dist: Double = 40.0, bearing: Double = 90.0, along: Double? = null) =
        NavState(emptyList(), 1, null, null, RouteHint(dist, bearing, pt(40.0, 0.0)), offM, shownAlongM = along)

    @Test fun straightWalkGivesTheNumbers() {
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0))
        val n = s.numbers(st(), t, gpsLost = false)
        assertNotNull(n); assertEquals(0.0, n!!.headingDeg, 1.0); assertEquals(40.0, n.routeM, 0.1)
        assertEquals(RouteSide.RIGHT, RouteSide.of(n.headingDeg, n.bearingDeg))
    }

    /** North y=80..100, back to y=92: the 12 m point lies before the turn – the heading would be north, the truth south. */
    @Test fun aUTurnJustTakenHasNoHeading() {
        val s = OffRouteSay()
        val t = walk(s, north(80.0, 100.0) + listOf(pt(0.0, 98.0), pt(0.0, 96.0), pt(0.0, 94.0), pt(0.0, 92.0)))
        assertNull(s.numbers(st(), t, false))
    }

    @Test fun aCornerTakenFiveMetresAgoHasNoHeading() {
        val s = OffRouteSay()
        val t = walk(s, north(0.0, 20.0) + listOf(pt(2.5, 20.0), pt(5.0, 20.0)))
        assertNull(s.numbers(st(), t, false))
    }

    @Test fun aCornerLongPastGivesTheNewHeading() {
        val s = OffRouteSay()
        val t = walk(s, north(0.0, 20.0) + (1..8).map { pt(it * 4.0, 20.0) })
        assertEquals(90.0, s.numbers(st(bearing = 180.0), t, false)!!.headingDeg, 1.0)
    }

    @Test fun aHintFarFromTheMeasuredDistanceIsNotTheNearestPoint() {
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0))
        assertNull(s.numbers(st(offM = 3.0, dist = 200.0), t, false))
        assertNotNull(s.numbers(st(offM = 40.0, dist = 50.0), t, false))
    }

    @Test fun onTheRouteOrUnknownDistanceSaysNothing() {
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0))
        assertNull(s.numbers(st(along = 120.0), t, false))
        assertNull(s.numbers(st(offM = null), t, false))
        assertNull(s.numbers(st().copy(toRoute = null), t, false))
    }

    @Test fun aPoorFixOrAClosePointSaysNothing() {
        val poor = OffRouteSay(); val t1 = walk(poor, north(0.0, 40.0), acc = 20.0)
        assertNull(poor.numbers(st(), t1, false))
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0), acc = 12.0)
        assertNull("closer than 15 m", s.numbers(st(offM = 10.0, dist = 10.0), t, false))
        assertNotNull(s.numbers(st(offM = 16.0, dist = 16.0), t, false))
    }

    @Test fun aGpsBearingThatContradictsTheHeadingSaysNothing() {
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0), bearing = 100.0)
        assertNull(s.numbers(st(), t, false))
        val ok = OffRouteSay(); val t2 = walk(ok, north(0.0, 40.0), bearing = 20.0)
        assertNotNull(ok.numbers(st(), t2, false))
    }

    @Test fun standingStillHasNoHeading() {
        val s = OffRouteSay()
        for (i in 0..20) s.onFix(pt(0.0, i * 2.0), 8.0, null, 0.2, i * 1000L)
        assertNull(s.numbers(st(), 20_000, false))
    }

    @Test fun oldFixOrLostGpsSaysNothing() {
        val s = OffRouteSay(); val t = walk(s, north(0.0, 40.0))
        assertNotNull(s.numbers(st(), t + 3_000, false))
        assertNull(s.numbers(st(), t + 3_001, false))
        assertNull(s.numbers(st(), t, gpsLost = true))
    }

    @Test fun noHeadingAcrossAGpsGap() {
        val s = OffRouteSay()
        walk(s, north(0.0, 20.0))
        // 25 s without a fix, then two fixes 4 m apart: the old points must not give a heading
        s.onFix(pt(0.0, 26.0), 8.0, null, 1.4, 30_000)
        s.onFix(pt(0.0, 30.0), 8.0, null, 1.4, 31_000)
        assertNull(s.numbers(st(), 31_000, false))
    }
}
