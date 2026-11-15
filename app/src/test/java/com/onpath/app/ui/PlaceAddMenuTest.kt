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
 * the likelier one first (by the zone) and emphasised, each saying where the point goes and about how far.
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

    // ---- what a row says: where the point goes, about how far (owner 2026-11-15) ----

    @Test fun pointNamesNeverSayStop2() {
        assertEquals("Kirche", PlaceAddMenu.pointName(1, 3, "Kirche"))
        assertEquals("Start", PlaceAddMenu.pointName(0, 3, null))
        assertEquals("Destination", PlaceAddMenu.pointName(2, 3, " "))
        assertEquals("②", PlaceAddMenu.pointName(1, 3, null)) // as on the map's marker
        assertEquals("⑳", PlaceAddMenu.circled(20))
        assertEquals("21", PlaceAddMenu.circled(21))
    }

    @Test fun longNamesAreShortenedAtAWord() {
        assertEquals("Burgruine Altenahr", PlaceAddMenu.shorten("Burgruine Altenahr"))
        assertEquals("Gasthaus Zur Alten…", PlaceAddMenu.shorten("Gasthaus Zur Alten Mühle am Bach"))
        assertTrue(PlaceAddMenu.shorten("Gasthaus Zur Alten Mühle am Bach").length <= PlaceAddMenu.NAME_MAX)
        // one long word: cut hard
        assertEquals(PlaceAddMenu.NAME_MAX, PlaceAddMenu.shorten("Donaudampfschifffahrtsgesellschaft").length)
    }

    @Test fun theStopLineSaysBetweenWhich() {
        val names = listOf("Start", "Kirche", "Hütte")
        assertEquals(PlaceAddMenu.Line("between Kirche and Hütte", "about +0.6 km"), PlaceAddMenu.stopLine(names, 2, false, 600.0))
        assertEquals("before Kirche", PlaceAddMenu.stopLine(names, 1, nextAhead = true, extraM = 50.0).where) // on a tour, right ahead
        assertEquals(null, PlaceAddMenu.stopLine(names, 1, false, 50.0).km) // a few steps aside: no number
        assertEquals("before Start", PlaceAddMenu.stopLine(names, 0, false, null).where)
    }

    @Test fun theGoalLineSaysAfterWhilePlanningInsteadOnATour() {
        assertEquals(PlaceAddMenu.Line("after Hütte", "about +3.2 km"), PlaceAddMenu.goalLine("Hütte", touring = false, extraM = 3200.0))
        assertEquals(PlaceAddMenu.Line("instead of Hütte", "about −0.4 km"), PlaceAddMenu.goalLine("Hütte", touring = true, extraM = -400.0))
    }

    @Test fun kmOneDecimalUnderTenWholeAbove() {
        assertEquals("about +0.1 km", PlaceAddMenu.km(100.0))
        assertEquals("about +9.9 km", PlaceAddMenu.km(9_940.0))
        assertEquals("about +12 km", PlaceAddMenu.km(12_400.0))
        assertEquals(null, PlaceAddMenu.km(99.0))
        assertEquals(null, PlaceAddMenu.km(null))
    }

    @Test fun inGermanTheOwnersWords() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals(PlaceAddMenu.Line("zwischen Kirche und Hütte", "ca. +0,6 km"),
                PlaceAddMenu.stopLine(listOf("Start", "Kirche", "Hütte"), 2, false, 600.0))
            assertEquals("nach Hütte", PlaceAddMenu.goalLine("Hütte", false, null).where)
            assertEquals("statt Hütte", PlaceAddMenu.goalLine("Hütte", true, null).where)
            assertEquals("Ziel", PlaceAddMenu.pointName(2, 3, null))
            assertEquals("Einfügen", Txt.s(Res.string.plan_add_as_stop))
            assertEquals("Neues Ziel", Txt.s(Res.string.plan_make_destination))
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test fun theDetourFactorIsTheRoutesOwn() {
        val goal = at(here, 2000.0, 0.0)
        assertEquals(1.0, PlaceAddMenu.detourFactor(null, listOf(here, goal)), 1e-9)
        assertEquals(1.5, PlaceAddMenu.detourFactor(3000.0, listOf(here, goal)), 0.01)
        assertEquals(PlaceAddMenu.FACTOR_MAX, PlaceAddMenu.detourFactor(50_000.0, listOf(here, goal)), 1e-9) // an odd route: kept sane
        assertEquals(PlaceAddMenu.FACTOR_MIN, PlaceAddMenu.detourFactor(1000.0, listOf(here, goal)), 1e-9)
        // the straight detour of a place 800 m beside the middle of 2 km
        assertEquals(0.56, PlaceAddMenu.detourM(here, goal, at(here, 1000.0, 800.0)) / 1000, 0.02)
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
}
