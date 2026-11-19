package com.onpath.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-06 (Pixel): on Reverse / Round trip the plan sheet went down and up – while the route was calculated the
 * profile, the difficulty and the lines vanished and a bar took room of its own. Now nothing appears or disappears
 * because of the calculation (a long one is the wait window over the map, progress/RouteWait.kt); screenshots
 * planRecalculating(Before).
 */
class PlanCalculatingTest {

    @Test fun whatIsThereStaysFaded() {
        assertTrue(PlanCalculating.FADED in 0.2f..0.6f)
    }

}
