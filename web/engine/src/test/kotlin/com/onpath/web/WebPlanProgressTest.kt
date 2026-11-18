package com.onpath.web

import com.onpath.app.data.Geo
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.LegCache
import com.onpath.app.routing.LongRoute
import com.onpath.app.routing.RouteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wait box's km (owner 2026-11-18): routed legs count their real length, the rest its estimate; nothing invented. */
class WebPlanProgressTest {
    private val a = GeoPoint(48.10, 11.50); private val b = GeoPoint(47.80, 11.60)
    private val c = GeoPoint(47.30, 11.40); private val d = GeoPoint(46.90, 11.40)
    private val wps = listOf(a, b, c, d)
    private val act = Activity.BIKE_TOURING
    private fun est(x: GeoPoint, y: GeoPoint) = Geo.distance(x, y) * LongRoute.DETOUR
    private fun leg(m: Double) = RouteResult(listOf(a, b), m, 0.0, 0.0, 0.0)
    private fun field(j: String, k: String) = Regex("\"$k\":(\\d+)").find(j)!!.groupValues[1].toLong()

    @Test fun kmOfTheLegsRoutedOutOfThePlansLength() {
        val cache = LegCache()
        cache.put(a, b, act, 2, leg(40_000.0)) // known before the run: real length, counts as done
        val p = WebPlanProgress(wps, act, 2, cache)
        val start = p.json(0, 2)
        val total0 = 40_000.0 + est(b, c) + est(c, d)
        assertEquals(40_000L, field(start, "doneM"))
        assertEquals(Math.round(total0), field(start, "totalM"))
        assertTrue(start, start.contains("\"text\":\"Calculating section 1 of 2…\""))
        // the Planner keeps the routed leg in the cache before it reports it: its real length from then on
        cache.put(b, c, act, 2, leg(70_000.0))
        val one = p.json(1, 2)
        assertEquals(110_000L, field(one, "doneM"))
        assertEquals(Math.round(110_000.0 + est(c, d)), field(one, "totalM"))
        // a failed leg (not in the cache): its estimate
        val all = p.json(2, 2)
        assertEquals(field(all, "totalM"), field(all, "doneM"))
    }

    @Test fun oneLegHasKmButNoSectionText() {
        val j = WebPlanProgress(listOf(a, d), act, 2, LegCache()).json(0, 1)
        assertTrue(j, j.startsWith("{\"text\":null,\"doneM\":0,"))
    }
}
