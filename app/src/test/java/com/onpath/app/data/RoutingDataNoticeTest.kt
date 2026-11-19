package com.onpath.app.data

import com.onpath.app.routing.BRouterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The plan's routing-data notice (shared with the web planner), in English and German: a missing tile or a router message about
 * missing data becomes the plain notice (loading now / loads when online); every other error passes through. (Test clean-up G09,
 * 2026-11-19: explicit expectations instead of the comparison with a frozen copy of the code from before the move to core.)
 */
class RoutingDataNoticeTest {
    private fun inBoth(block: () -> Unit) {
        val before = Locale.getDefault()
        try {
            for (l in listOf(Locale.US, Locale.GERMANY)) { Locale.setDefault(l); block() }
        } finally { Locale.setDefault(before) }
    }

    /** Every input of the old comparison: 10 errors x missing x loading x 2 locales; `true` = the notice, `false` = the error as it is. */
    private val errors: List<Pair<String?, Boolean>> get() = listOf(
        null to false, "" to false, "No route found." to false,
        "No routing data for this area." to true,
        "Leg 1→2: ${BRouterEngine.NO_DATA}" to true,
        "missing E10_N45.rd5 in /storage/x/segments" to true,
        "Keine Routingdaten" to false,
        RoutingDataNotice.LOADING to false, RoutingDataNotice.NOT_YET to false,
        "Route too long" to false)

    @Test fun missingDataOrTheRoutersWordForItIsTheNoticeEverythingElsePassesThrough() = inBoth {
        for (loading in listOf(true, false)) {
            val notice = if (loading) RoutingDataNotice.LOADING else RoutingDataNotice.NOT_YET
            assertEquals(notice, RoutingDataNotice.text(loading))
            for ((e, router) in errors) {
                // a tile under the route is not on the phone: the notice, whatever the router said
                assertEquals("$e/missing/$loading", notice, RoutingDataNotice.forRoute(e, missing = true, loading = loading))
                // the router's own sentence (English, the app's language, a file name, inside a longer message): the notice; else the error as it is
                assertEquals("$e/$loading", if (router) notice else e, RoutingDataNotice.forRoute(e, missing = false, loading = loading))
            }
        }
    }

    @Test fun onlyTheTwoNoticeTextsAreNotices() = inBoth {
        for ((e, _) in errors) assertEquals("$e", e == RoutingDataNotice.LOADING || e == RoutingDataNotice.NOT_YET, RoutingDataNotice.isNotice(e))
        assertTrue(RoutingDataNotice.isNotice(RoutingDataNotice.LOADING))
        assertTrue(RoutingDataNotice.isNotice(RoutingDataNotice.NOT_YET))
        assertFalse(RoutingDataNotice.isNotice(null))
    }

    @Test fun kindAndKeys() = inBoth {
        assertEquals(RoutingDataNotice.Kind.LOADING, RoutingDataNotice.kindForRoute(null, missing = true, loading = true))
        assertEquals(RoutingDataNotice.Kind.NOT_YET, RoutingDataNotice.kindForRoute("x.rd5", missing = false, loading = false))
        assertNull(RoutingDataNotice.kindForRoute("No route found.", missing = false, loading = true))
        assertEquals("maps_routing_loading", RoutingDataNotice.Kind.LOADING.res.key)
        assertEquals("maps_routing_not_yet", RoutingDataNotice.Kind.NOT_YET.res.key)
    }
}
