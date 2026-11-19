package com.onpath.app.data

import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Routing data of an installed area: newer on the server, and a route change noticed only when the way changed (from MapUpdatesTest – test-audit G10). */
class RoutingDataCheckTest {
    @Test fun routingDataNewer() {
        assertTrue(RoutingDataCheck.isNewer(2000, 1000))
        assertFalse(RoutingDataCheck.isNewer(1000, 2000))
        assertFalse(RoutingDataCheck.isNewer(0, 1000))      // server date unknown
        assertFalse(RoutingDataCheck.isNewer(2000, null))   // no file: the first download is a different flow
    }

    @Test fun routeChangeIsNoticedOnlyWhenTheWayChanged() {
        val line = (0..50).map { GeoPoint(46.67 + it * 0.0002, 11.16) }
        assertFalse(RoutingDataCheck.differs(line, line))
        assertFalse(RoutingDataCheck.differs(line, line.map { GeoPoint(it.lat, it.lon + 0.00005) })) // ~4 m aside
        val detour = line.mapIndexed { i, p -> if (i in 20..30) GeoPoint(p.lat, p.lon + 0.002) else p }    // ~150 m
        assertTrue(RoutingDataCheck.differs(line, detour))
    }
}
