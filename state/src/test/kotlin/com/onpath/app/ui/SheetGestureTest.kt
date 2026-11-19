package com.onpath.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan sheet's and Navigate card's gestures (converted from ExpandGestureTest and SheetRiseTest – test-audit G11):
 * tap and drag open / close, the first route raises the sheet once.
 */
class SheetGestureTest {
    // ---- from ExpandGestureTest.kt ----
    // Tap and drag both open / close (owner 2026-11-06 23:42): Navigate's card and the plan sheet's handle.
    private val t = 60f

    /** Navigate's card: drag down opens, drag up closes, a short wobble changes nothing, going further the same way keeps the state. */
    @Test fun theCardFollowsADragPastTheThresholdOnly() {
        assertTrue("pulling the closed card down opens it", ExpandGesture.cardAfterDrag(false, 120f, t))
        assertFalse("pushing the open card up closes it", ExpandGesture.cardAfterDrag(true, -120f, t))
        assertFalse("a short wobble: closed stays closed", ExpandGesture.cardAfterDrag(false, 30f, t))
        assertTrue("a short wobble: open stays open", ExpandGesture.cardAfterDrag(true, -30f, t))
        assertFalse("no movement: closed stays closed", ExpandGesture.cardAfterDrag(false, 0f, t))
        assertTrue("pulling the open card further down keeps it open", ExpandGesture.cardAfterDrag(true, 200f, t))
        assertFalse("pushing the closed card up keeps it closed", ExpandGesture.cardAfterDrag(false, -200f, t))
    }

    @Test fun aTapToggles() {
        assertTrue(ExpandGesture.cardAfterTap(false))
        assertFalse(ExpandGesture.cardAfterTap(true))
    }

    @Test fun theHandleTapWalksTheSteps() {
        // rolled → one row → profile → points → back to profile (the established order, PlanSize.tapped); Material 3: a tap on the drag handle grows the sheet one step, the top step goes back to the middle (the old SheetRiseTest.aTapOnTheHandleStepsUpOneStep had the same four asserts)
        assertEquals(PlanSize.MINI, PlanSize.COLLAPSED.tapped())
        assertEquals(PlanSize.COMPACT, PlanSize.MINI.tapped())
        assertEquals(PlanSize.FULL, PlanSize.COMPACT.tapped())
        assertEquals(PlanSize.COMPACT, PlanSize.FULL.tapped())
    }

    @Test fun theHandleSaysBiggerExceptOnTheLargestStep() {
        assertTrue(ExpandGesture.handleGrows(PlanSize.COLLAPSED))
        assertTrue(ExpandGesture.handleGrows(PlanSize.MINI))
        assertTrue(ExpandGesture.handleGrows(PlanSize.COMPACT))
        assertFalse(ExpandGesture.handleGrows(PlanSize.FULL))
    }

    // ---- from SheetRiseTest.kt ----
    // Owner 2026-11-08 (father's feedback: nobody found that the plan sheet pulls up): the plan's first route raises the
    // sheet once – evening, on the Pixel: only so far that "✕ Neu planen · ⇅ Umkehren · ↻ Rundtour" is fully in view (the
    // summary step), not to the profile; never on a re-route, never during a tour, never down from a higher step.
    /** A plan's life: routes come one after the other; [SheetRise] decides, the caller remembers "risen". */
    private class Plan(var size: PlanSize = PlanSize.COLLAPSED, var risen: Boolean = false) {
        var rose = 0
        fun points(n: Int) { if (SheetRise.resets(n)) risen = false }
        fun routeReady(plan: Boolean = true, tour: Boolean = false) {
            if (SheetRise.rises(risen, size, plan, tour)) { size = SheetRise.TO; rose++ }
            risen = true
        }
    }

    @Test fun theFirstRouteRaisesTheSheetUntilThePlanRowIsInView() {
        val p = Plan()
        p.points(2); p.routeReady()
        assertEquals("the summary step: the plan row on top", PlanSize.MINI, p.size)
        assertEquals(1, p.rose)
        // not the profile step any more (owner 2026-11-08 evening: "zu weit")
        assertEquals(PlanSize.MINI, SheetRise.TO)
    }

    @Test fun aSheetAtTheSummaryStepStaysThere() {
        // the usual start: the empty plan's sheet stands at the summary step – it grows with the route's lines by itself
        val p = Plan(size = PlanSize.MINI)
        p.points(2); p.routeReady()
        assertEquals(PlanSize.MINI, p.size)
        assertEquals(0, p.rose)
    }

    @Test fun reRoutesOfTheSamePlanNeverRaiseItAgain() {
        val p = Plan()
        p.points(2); p.routeReady()
        p.size = PlanSize.COLLAPSED // the user rolled it up again
        p.points(3); p.routeReady() // a point added: a new route
        p.points(3); p.routeReady() // reverse / round trip / activity: again
        assertEquals(PlanSize.COLLAPSED, p.size)
        assertEquals(1, p.rose)
    }

    @Test fun aNewPlanRaisesItOnceMore() {
        val p = Plan()
        p.points(2); p.routeReady()
        p.size = PlanSize.COLLAPSED
        p.points(0) // ✕ Neu planen (or a saved route replaces the plan)
        p.points(2); p.routeReady()
        assertEquals(PlanSize.MINI, p.size)
        assertEquals(2, p.rose)
    }

    @Test fun aHigherSheetIsNeverPulledDown() {
        for (s in listOf(PlanSize.MINI, PlanSize.COMPACT, PlanSize.FULL)) {
            val p = Plan(size = s)
            p.points(2); p.routeReady()
            assertEquals("already up: stays $s", s, p.size)
            // and the plan counts as risen: a later re-route does not raise it either
            p.size = PlanSize.COLLAPSED; p.routeReady()
            assertEquals(PlanSize.COLLAPSED, p.size)
        }
    }

    @Test fun notDuringATourNorOutsidePlan() {
        assertFalse(SheetRise.rises(risen = false, size = PlanSize.COLLAPSED, plan = true, tour = true))
        assertFalse(SheetRise.rises(risen = false, size = PlanSize.COLLAPSED, plan = false, tour = false))
        assertTrue(SheetRise.rises(risen = false, size = PlanSize.COLLAPSED, plan = true, tour = false))
    }
}
