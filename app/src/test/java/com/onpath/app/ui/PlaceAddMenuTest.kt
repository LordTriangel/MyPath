package com.onpath.app.ui

import com.onpath.app.data.Geo
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.WaypointInsertion
import com.onpath.app.ui.PlaceAddMenu.Item
import com.onpath.app.ui.PlaceAddMenu.Zone
import com.onpath.strings.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The place menu's add entries (owner 2026-11-07 11:20 / 11:25 / 11:30; 2026-11-15): always „Einfügen“ and „Neues Ziel“,
 * the likelier one first (by the zone) and emphasised; one line each, only the label.
 */
class PlaceAddMenuTest {

    /** Hilden centre – the owner's field case. */
    private val here = GeoPoint(51.1680, 6.9390)

    private fun at(from: GeoPoint, eastM: Double, northM: Double) =
        GeoPoint(from.lat + Geo.degLat(northM), from.lon + Geo.degLon(eastM, from.lat))

    // ---- the zones, real-world-like ----

    @Test fun aViaOnThePath30mAsideIsOnlyAStop() {
        val goal = at(here, 2000.0, 0.0)
        val via = at(here, 1000.0, 30.0)
        assertEquals(Zone.STOP, PlaceAddMenu.zone(listOf(here, goal), via))
    }

    /**
     * The field case (11:25): position in Hilden centre, goal 1 km to the WEST, the church 800 m to the NORTH-EAST. As a
     * stop it adds ~1.5 km to a 1 km rest (more than doubles it) – „Neues Ziel“ first, „Einfügen“ under it.
     */
    @Test fun churchNorthEastWithTheGoalWestIsOnlyTheGoal() {
        val goal = at(here, -1000.0, 0.0)
        val church = at(here, 565.0, 565.0)
        val zone = PlaceAddMenu.zone(listOf(here, goal), church)
        assertEquals(Zone.GOAL, zone)
        assertEquals(PlaceAddMenu.Menu(listOf(Item.NEW_DESTINATION, Item.ADD_STOP)), PlaceAddMenu.menu(2, true, zone))
    }

    /** The same church with a long rest (3 km west): the detour is under half of it – doubtful, both, the stop first. */
    @Test fun theSameChurchWithALongRestIsDoubtful() {
        val goal = at(here, -3000.0, 0.0)
        val church = at(here, 565.0, 565.0)
        assertEquals(Zone.STOP_FIRST, PlaceAddMenu.zone(listOf(here, goal), church))
    }

    @Test fun aPointJustPastTheGoalIsOnlyTheGoal() {
        val goal = at(here, 2000.0, 0.0)
        val past = at(here, 2150.0, 20.0)
        assertEquals(Zone.GOAL, PlaceAddMenu.zone(listOf(here, goal), past))
    }

    @Test fun pastTheGoalButWideOfItIsGoalFirst() {
        val goal = at(here, 2000.0, 0.0)
        val wide = at(here, 2000.0, 1300.0) // farther than the goal, ~33° off its direction
        assertEquals(Zone.GOAL_FIRST, PlaceAddMenu.zone(listOf(here, goal), wide))
    }

    @Test fun besideTheMiddleTheDetourDecides() {
        val goal = at(here, 2000.0, 0.0)
        assertEquals(Zone.STOP, PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 500.0)))       // +0.24 km = 12 %
        assertEquals(Zone.STOP_FIRST, PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 800.0))) // +0.56 km = 28 %
        assertEquals(Zone.GOAL_FIRST, PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 1200.0))) // +1.1 km = 56 %
        assertEquals(Zone.GOAL, PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 2000.0)))      // +2.5 km = 124 %
    }

    /** Out 3 km north and back to 1 km north: a place on the way out is a stop, not "past the goal". */
    @Test fun aPlaceOnTheWayOutOfAnOutAndBackIsAStop() {
        val turn = at(here, 0.0, 3000.0)
        val goal = at(here, 0.0, 1000.0)
        assertEquals(Zone.STOP, PlaceAddMenu.zone(listOf(here, turn, goal), at(here, 20.0, 2500.0)))
    }

    /** A few hundred metres to go: a place a few steps aside stays a stop, one nearby never becomes "only the goal". */
    @Test fun aShortRestKeepsTheFloors() {
        val goal = at(here, 80.0, 0.0)
        assertEquals(Zone.STOP, PlaceAddMenu.zone(listOf(here, goal), at(here, 40.0, 40.0)))
        assertNotEquals(Zone.GOAL, PlaceAddMenu.zone(listOf(here, goal), at(here, 40.0, -150.0)))
    }

    @Test fun aRoundTripIsNeverOnlyTheGoal() {
        val loop = listOf(here, at(here, 2000.0, 0.0), at(here, 2000.0, 2000.0), here)
        val far = at(here, -3000.0, -3000.0)
        assertEquals(Zone.GOAL_FIRST, PlaceAddMenu.zone(loop, far, loop = true))
    }

    @Test fun noRouteAheadNoZone() {
        assertEquals(null, PlaceAddMenu.zone(listOf(here), at(here, 100.0, 0.0)))
    }

    // ---- the menu ----

    /** Owner 2026-11-15 (after a hike): both rows always, the likelier first and emphasised – nothing under „⋯ Mehr“. */
    @Test fun theMenuPerZone() {
        assertEquals(PlaceAddMenu.Menu(listOf(Item.ADD_STOP, Item.NEW_DESTINATION)), PlaceAddMenu.menu(3, false, Zone.STOP))
        assertEquals(PlaceAddMenu.Menu(listOf(Item.ADD_STOP, Item.NEW_DESTINATION)), PlaceAddMenu.menu(3, false, Zone.STOP_FIRST))
        assertEquals(PlaceAddMenu.Menu(listOf(Item.NEW_DESTINATION, Item.ADD_STOP)), PlaceAddMenu.menu(3, true, Zone.GOAL_FIRST))
        assertEquals(PlaceAddMenu.Menu(listOf(Item.NEW_DESTINATION, Item.ADD_STOP)), PlaceAddMenu.menu(3, true, Zone.GOAL))
    }

    @Test fun bothAreAlwaysShownTheFirstEmphasised() {
        for (z in Zone.values()) {
            val m = PlaceAddMenu.menu(2, true, z)
            assertEquals("$z", setOf(Item.ADD_STOP, Item.NEW_DESTINATION), m.main.toSet())
            assertEquals("$z", m.main.first(), m.emphasised)
        }
        // one point or none: nothing to choose between, nothing emphasised
        assertEquals(null, PlaceAddMenu.menu(1, false, null).emphasised)
        assertEquals(null, PlaceAddMenu.menu(0, false, null).emphasised)
    }

    /** The owner's words (2026-11-15), one line each – where it goes shows on the map. */
    @Test fun inGermanTheOwnersWords() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("Einfügen", Txt.s(Res.string.plan_add_as_stop))
            assertEquals("Neues Ziel", Txt.s(Res.string.plan_make_destination))
        } finally {
            java.util.Locale.setDefault(before)
        }
        assertEquals("Insert", Txt.s(Res.string.plan_add_as_stop))
        assertEquals("New destination", Txt.s(Res.string.plan_make_destination))
    }

    @Test fun fewerThanTwoPointsKeepTheFirstPointWording() {
        assertEquals(listOf(Item.START_HERE, Item.GO_HERE), PlaceAddMenu.menu(0, false, null).main)
        assertEquals(listOf(Item.GO_HERE), PlaceAddMenu.menu(0, true, null).main) // never "Hier starten" during a tour
        assertEquals(listOf(Item.SET_DESTINATION), PlaceAddMenu.menu(1, true, null).main)
    }

    // ---- „Einfügen“ keeps the goal ----

    @Test fun aStopNeverReplacesOrPassesTheGoal() {
        val goal = at(here, 2000.0, 0.0)
        val past = at(here, 2500.0, 0.0)
        val d = WaypointInsertion.decide(listOf(here, goal), past, null, 30.0, stopOnly = true)
        assertNotEquals(WaypointInsertion.Kind.DESTINATION, d.kind)
        assertEquals(1, d.index)
        // without stopOnly the same place is appended (today's smart add)
        assertEquals(WaypointInsertion.Kind.DESTINATION, WaypointInsertion.decide(listOf(here, goal), past, null, 30.0).kind)
    }

    @Test fun aNewDestinationKeepsTheOldGoalWhilePlanningAndReplacesItOnTheWay() {
        // owner 2026-11-14: „Beim Planen anhängen, unterwegs ersetzen“
        org.junit.Assert.assertTrue(PlaceAddMenu.keepsOldGoal(touring = false))
        org.junit.Assert.assertFalse(PlaceAddMenu.keepsOldGoal(touring = true))
    }

    // ---- a hold on the empty map (owner 2026-11-15, planning on his Pixel: „ich werde überhaupt nie gefragt“) ----

    /**
     * The owner's case: two points 2 km apart, a hold 800 m beside the middle – as a stop it adds about a quarter, it could
     * as well be where the route goes on: doubtful. Before it was set silently; now it asks („Einfügen“ first).
     */
    @Test fun aHoldInADoubtfulSpotAsks() {
        val goal = at(here, 2000.0, 0.0)
        val zone = PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 800.0))
        assertEquals(Zone.STOP_FIRST, zone)
        assertTrue("a doubtful hold asks", PlaceAddMenu.asksOnHold(zone))
        assertEquals(Item.ADD_STOP, PlaceAddMenu.menu(2, false, zone).emphasised)
        // farther aside: still doubtful, „Neues Ziel“ first
        val far = PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 1200.0))
        assertEquals(Zone.GOAL_FIRST, far)
        assertTrue(PlaceAddMenu.asksOnHold(far))
        assertEquals(Item.NEW_DESTINATION, PlaceAddMenu.menu(2, false, far).emphasised)
    }

    /** Clear cases stay as they were: set at once where they fit best (WaypointInsertion), no menu. */
    @Test fun aHoldInAClearSpotIsSetAtOnce() {
        val goal = at(here, 2000.0, 0.0)
        org.junit.Assert.assertFalse(PlaceAddMenu.asksOnHold(PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 30.0)))) // on the way
        org.junit.Assert.assertFalse(PlaceAddMenu.asksOnHold(PlaceAddMenu.zone(listOf(here, goal), at(here, 2150.0, 20.0)))) // past the goal
        org.junit.Assert.assertFalse(PlaceAddMenu.asksOnHold(PlaceAddMenu.zone(listOf(here, goal), at(here, 1000.0, 2000.0)))) // far off: only a goal
        org.junit.Assert.assertFalse(PlaceAddMenu.asksOnHold(null)) // fewer than 2 points / no route ahead
    }

    /** A round trip is never dropped by one hold: a spot far off its loop asks („Neues Ziel“ first, never silently). */
    @Test fun aHoldFarOffARoundTripAsks() {
        val loop = listOf(here, at(here, 2000.0, 0.0), at(here, 2000.0, 2000.0), here)
        assertTrue(PlaceAddMenu.asksOnHold(PlaceAddMenu.zone(loop, at(here, -3000.0, -3000.0), loop = true)))
    }
}
