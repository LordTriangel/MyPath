package com.onpath.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A held place's add entries in the browser = the app's PlaceAddMenu (owner 2026-11-07): Engine.placeMenu passes the
 * plan through; „Als Zwischenstopp“ goes between the points, never behind the goal; the planner has no tour.
 */
class WebPlaceMenuTest {
    private val ab = "46.6700,11.1600;46.6700,11.2600" // A → B, ~7.6 km east

    @Test fun emptyPlanOffersStartHereAndGoHere() =
        assertTrue(Engine.placeMenu("", 46.67, 11.16, false).startsWith("{\"main\":[\"START_HERE\",\"GO_HERE\"]"))

    @Test fun onePointSetsTheDestination() =
        assertTrue(Engine.placeMenu("46.67,11.16", 46.68, 11.20, false).startsWith("{\"main\":[\"SET_DESTINATION\"]"))

    @Test fun aPlaceOnTheWayIsAStopFirstAndGoesBetween() {
        val m = Engine.placeMenu(ab, 46.6702, 11.2100, false)
        // owner 2026-11-15: both always, the likelier first – nothing under „⋯ Mehr“
        assertTrue(m, m.startsWith("{\"main\":[\"ADD_STOP\",\"NEW_DESTINATION\"]"))
        assertTrue(m, m.contains("\"more\":[]"))
        assertEquals(m, 1, Regex("\"stop\":(\\d+)").find(m)!!.groupValues[1].toInt())
    }

    @Test fun aPlaceFarPastTheGoalIsTheNewGoal() {
        val m = Engine.placeMenu(ab, 46.6700, 11.6000, false)
        assertTrue(m, m.startsWith("{\"main\":[\"NEW_DESTINATION\",\"ADD_STOP\"]"))
    }

    // „Als neues Ziel“ in the planner (no tour) keeps the old goal as the last stop – PlaceAddMenu.keepsOldGoal (owner 2026-11-14)
    @Test fun theNewGoalKeepsTheOldOneInThePlanner() {
        val m = Engine.placeMenu(ab, 46.6700, 11.6000, false)
        assertTrue(m, m.contains("\"keepOld\":true"))
    }

    // ---- a click on the empty map with 2+ points (the app's holdOnMap, F-PLAN-15 with the band of 2026-11-16) ----
    private val ns = "46.6000,11.2000;46.7000,11.2000" // A → B, ~11 km north, on a meridian
    private fun action(j: String) = Regex("\"action\":\"(\\w+)\"").find(j)!!.groupValues[1]
    private fun index(j: String) = Regex("\"index\":(-?\\d+)").find(j)!!.groupValues[1].toInt()

    @Test fun onTheLineGoesIntoTheLegAtOnce() {
        val j = Engine.holdOnMap(ns, 46.6500, 11.2001, false, 24.0)
        assertEquals(j, "ADD", action(j)); assertEquals(j, 1, index(j))
    }

    // straight on past the goal: appending is half of inserting → the new goal, appended (PlaceAddMenuTest beyondTheGoal…)
    @Test fun straightOnPastTheGoalIsTheNewGoalAtOnce() {
        val j = Engine.holdOnMap(ns, 46.7300, 11.2000, false, 24.0)
        assertEquals(j, "ADD", action(j)); assertEquals(j, 2, index(j))
    }

    @Test fun besideTheMiddleIsAStopAtOnce() {
        val j = Engine.holdOnMap(ns, 46.6500, 11.2100, false, 24.0) // ~760 m east of the middle: insert ~100 m, append ~5.6 km
        assertEquals(j, "ADD", action(j)); assertEquals(j, 1, index(j))
    }

    // beside the goal, a little on: both detours similar → the menu, „Neues Ziel“ first (the cheaper)
    @Test fun besideTheGoalAsksTheCheaperFirst() {
        val j = Engine.holdOnMap(ns, 46.7050, 11.2100, false, 24.0)
        assertEquals(j, "ASK", action(j))
        assertTrue(j, j.contains("\"main\":[\"NEW_DESTINATION\",\"ADD_STOP\"]"))
        assertTrue(j, j.contains("\"stop\":1"))
    }

    @Test fun fewerThanTwoPointsIsTheSmartInsertion() {
        val j = Engine.holdOnMap("46.6,11.2", 46.7, 11.2, false, 24.0)
        assertEquals(j, "ADD", action(j)); assertEquals(j, 1, index(j))
    }

    // the place menu while planning orders by the same detours (AppViewModel.placeMenu → PlaceAddMenu.planningMenu)
    @Test fun placeMenuWhilePlanningOrdersByTheDetours() {
        assertTrue(Engine.placeMenu(ns, 46.7050, 11.2100, false).startsWith("{\"main\":[\"NEW_DESTINATION\",\"ADD_STOP\"]"))
        assertTrue(Engine.placeMenu(ns, 46.6500, 11.2100, false).startsWith("{\"main\":[\"ADD_STOP\",\"NEW_DESTINATION\"]"))
    }

    @Test fun theHoldMenuGoesBesideTheSpot() {
        // room right of the spot → right of it (gap 22), centred on the spot vertically
        assertEquals("222,180", Engine.placeBeside(200.0, 200.0, 150, 40, 12, 0, 1000, 800, 22, 0))
        // no room right → left of the spot
        assertEquals("828,180", Engine.placeBeside(1000.0, 200.0, 150, 40, 12, 0, 1100, 800, 22, 0))
    }

    // the locate distance (the app's LocateDistance): only while I am off the visible map
    @Test fun locateLabelOnlyOffTheMap() {
        assertEquals("", Engine.locateLabel(true, 46.67, 11.16, 46.6, 11.1, 46.7, 11.2)) // in view
        assertEquals("", Engine.locateLabel(false, 0.0, 0.0, 46.6, 11.1, 46.7, 11.2))   // no position
        assertTrue(Engine.locateLabel(true, 51.17, 6.93, 46.6, 11.1, 46.7, 11.2).endsWith("km")) // Hilden, map at Meran
    }
}
