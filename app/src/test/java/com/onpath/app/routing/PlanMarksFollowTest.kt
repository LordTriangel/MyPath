package com.onpath.app.routing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-point attributes – pinned, names and (owner 2026-11-17, multi-day tours) night marks – follow their points
 * through every edit of the plan ([PlanEdits.follow], [PlanDays.afterEdit]): insert, delete, move, reverse, round trip
 * on / off, start here, rotate a loop, a drag, the router's snapping. A deleted point's mark goes.
 */
class PlanMarksFollowTest {
    private val a = GeoPoint(46.60, 11.10)
    private val b = GeoPoint(46.62, 11.12)
    private val c = GeoPoint(46.64, 11.14)
    private val d = GeoPoint(46.66, 11.16)
    private val e = GeoPoint(46.68, 11.18)
    private val x = GeoPoint(46.63, 11.20)

    private val blue = NightMark.OVERNIGHT
    private val green = NightMark(Hotel("Post"))

    /** a → b (pinned, "Hütte", blue) → c (green, "Post") → d → e; 4 days. */
    private val plan = SharedPlan(listOf(a, b, c, d, e), Activity.HIKE, pinned = setOf(1), names = mapOf(1 to "Hütte", 2 to "Post"),
        days = 4, nights = mapOf(1 to blue, 2 to green))

    private fun edit(after: List<GeoPoint>) = PlanDays.afterEdit(plan, after)

    @Test fun insertShiftsTheMarksBehind() {
        val at = WaypointInsertion.decide(plan.points, x, null, 0.0, stopOnly = true).index
        val p = edit(plan.points.toMutableList().apply { add(at, x) })
        val bi = p.points.indexOf(b); val ci = p.points.indexOf(c)
        assertEquals(setOf(bi), p.pinned)
        assertEquals(mapOf(bi to "Hütte", ci to "Post"), p.names)
        assertEquals(mapOf(bi to blue, ci to green), p.nights)
        assertEquals(4, p.days)
    }

    @Test fun deletingAMarkedPointTakesItsMarkAndNStays() {
        val p = edit(PlanPoints.deleteAt(plan.points, 1))
        assertEquals(listOf(a, c, d, e), p.points)
        assertEquals(emptySet<Int>(), p.pinned)
        assertEquals(mapOf(1 to "Post"), p.names)
        assertEquals(mapOf(1 to green), p.nights)
        assertEquals(4, p.days) // scenario N: a grey proposal comes back
        assertEquals(2, PlanDays.proposalCount(p))
        // a point before the marks: they move up one
        val q = edit(PlanPoints.deleteAt(plan.points, 0))
        assertEquals(mapOf(0 to "Hütte", 1 to "Post"), q.names)
        assertEquals(mapOf(1 to green), q.nights) // b is the start now: no night on the start
    }

    @Test fun moveKeepsEachMarkOnItsPoint() {
        val p = edit(PlanPoints.move(plan.points, 1, 3))
        assertEquals(listOf(a, c, d, b, e), p.points)
        assertEquals(setOf(3), p.pinned)
        assertEquals(mapOf(3 to "Hütte", 1 to "Post"), p.names)
        assertEquals(mapOf(3 to blue, 1 to green), p.nights)
    }

    @Test fun reverse() {
        val p = edit(PlanEdits.reverse(plan.points))
        assertEquals(setOf(3), p.pinned)
        assertEquals(mapOf(3 to "Hütte", 2 to "Post"), p.names)
        assertEquals(mapOf(3 to blue, 2 to green), p.nights)
    }

    @Test fun roundTripOnAndOff() {
        val loop = edit(PlanEdits.roundTrip(plan.points))
        assertEquals(listOf(a, b, c, d, e, a), loop.points)
        assertEquals(mapOf(1 to blue, 2 to green), loop.nights) // the closing point is the start again: no mark
        assertEquals(mapOf(1 to "Hütte", 2 to "Post"), loop.names)
        val back = PlanDays.afterEdit(loop, PlanEdits.unRoundTrip(loop.points))
        assertEquals(plan, back)
        // reversing a round trip: the closing point stays the closing point
        val rev = PlanDays.afterEdit(loop, PlanEdits.reverse(loop.points))
        assertEquals(listOf(a, e, d, c, b, a), rev.points)
        assertEquals(mapOf(4 to blue, 3 to green), rev.nights)
    }

    @Test fun aMarkOnTheLastPointGoesWhenTheRouteGrowsOrShrinks() {
        // a point added behind the old destination: d and e stay unmarked, the marks stay
        val p = edit(plan.points + x)
        assertEquals(mapOf(1 to blue, 2 to green), p.nights)
        // start here at c: the points before go, c is the start – its hotel mark goes
        val s = edit(PlanEdits.startHere(plan.points, 2))
        assertEquals(listOf(c, d, e), s.points)
        assertEquals(emptyMap<Int, NightMark>(), s.nights)
        assertEquals(mapOf(0 to "Post"), s.names)
    }

    @Test fun rotateALoop() {
        val loop = PlanDays.afterEdit(plan, PlanEdits.roundTrip(plan.points))
        val r = PlanDays.afterEdit(loop, PlanEdits.rotateLoop(loop.points, 3))
        assertEquals(listOf(d, e, a, b, c, d), r.points)
        assertEquals(mapOf(3 to blue, 4 to green), r.nights)
        assertEquals(setOf(3), r.pinned)
    }

    @Test fun aDraggedPointKeepsItsNightButNotItsPinOrName() {
        val moved = GeoPoint(46.621, 11.125)
        val p = edit(PlanEdits.replace(plan.points, 1, moved))
        assertEquals(mapOf(1 to blue, 2 to green), p.nights) // scenario I: drag a blue bed
        assertEquals(emptySet<Int>(), p.pinned) // as in the app and the planner: a dragged point is a new place
        assertEquals(mapOf(2 to "Post"), p.names)
        // a dragged hotel point no longer sits at the hotel: an Übernachtung where it is dropped
        val h = edit(PlanEdits.replace(plan.points, 2, moved))
        assertEquals(mapOf(1 to blue, 2 to blue), h.nights)
    }

    @Test fun theRoutersSnappingKeepsEveryMark() {
        // free points move onto the way (several at once, same order): the marks stay on them
        val snapped = listOf(a, b, c, GeoPoint(46.6601, 11.1601), GeoPoint(46.6801, 11.1801))
        val p = PlanDays.afterEdit(plan.copy(nights = mapOf(1 to blue, 3 to blue)), snapped)
        assertEquals(mapOf(1 to blue, 3 to blue), p.nights)
    }

    @Test fun samePointTwiceFollowsByOrder() {
        // a → b → a → c (out and back over b, then on): the mark on the second a stays on the second a
        val w = listOf(a, b, a, c)
        val p = SharedPlan(w, Activity.HIKE, days = 3, nights = mapOf(2 to blue))
        val q = PlanDays.afterEdit(p, PlanPoints.move(w, 1, 2)) // a, a, b, c
        assertEquals(listOf(a, a, b, c), q.points)
        assertEquals(mapOf(1 to blue), q.nights)
    }

    @Test fun followForAnyAttribute() {
        assertEquals(mapOf(0 to "B"), PlanEdits.follow(listOf(a, b), listOf(b, a), mapOf(1 to "B")))
        assertEquals(setOf(2), PlanEdits.follow(listOf(a, b, c), listOf(a, x, b), setOf(1, 2)))
        assertEquals(emptyMap<Int, String>(), PlanEdits.follow(listOf(a, b), listOf(a, x), mapOf(1 to "B")))
        assertEquals(mapOf(1 to "B"), PlanEdits.follow(listOf(a, b), listOf(a, x), mapOf(1 to "B"), keepMoved = true))
    }
}
