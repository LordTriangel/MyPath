package com.onpath.app.ui

import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.onpath.app.domain.plan.RouteBack

/**
 * Owner rule: during a tour, deleting the GOAL of a 2-point plan (only the start is left) asks a persistent question
 * "Route back to the start?". Yes = the plan becomes position → start and the guidance follows it; No = plain
 * recording (what Plan shows = what Navigate follows). Undo works after either answer. The pure rules (RouteBack); the steps
 * are flows of the real slices (test clean-up G05, 2026-11-19; was a hand-written model of the view model here):
 *  - deletingTheGoalAsks (nothing changes while the question is open) = PlanEditFlowTest.deletingTheGoalDuringATourAsksRouteBackAfter…,
 *    TourEditsFlowTest.answeringNoToRouteBackEndsTheGuidanceAfterTheClear ("the line stays until it is answered")
 *  - yesRoutesFromThePositionBack… = TourEditsFlowTest.answeringYesWithOnePointLeftMakesThePlanPositionToStartAndFollowsIt
 *  - undoAfterYes… (one Undo, then Redo) = TourEditsFlowTest.undoAfterYesToRouteBackBringsTheOriginalPlanBackAndRedoTheWayBack
 *  - noIsPlainRecordingAndUndoBringsTheGuidanceBack = TourEditsFlowTest.answeringNoToRouteBack… + undoAfterNoToRouteBackBringsThePlanAndTheGuidanceBack
 *  - yesWithoutAPosition = TourEditsFlowTest.answeringYesWithoutAPositionSaysSoAndEndsTheGuidance + undoAfterYesWithoutAPositionBringsTheGuidanceBack
 *  - undoWhileAskingClosesTheQuestion = PlanEditFlowTest.anUndoThatIsNotNothingAheadClosesAnOpenQuestion
 */
class RouteBackTest {
    private val start = GeoPoint(46.670, 11.160)
    private val goal = GeoPoint(46.700, 11.200)
    private val here = GeoPoint(46.690, 11.190)

    @Test fun onlyTheGoalOfATwoPointPlanDuringAFollowedTour() {
        // the start deleted: the goal is left – no question
        assertFalse(RouteBack.asks(listOf(start, goal), listOf(goal), true, true, false, here))
        // three points → two: still a route
        assertFalse(RouteBack.asks(listOf(start, here, goal), listOf(start, here), true, true, false, here))
        // no tour, nothing followed, a followed GPX track
        assertFalse(RouteBack.asks(listOf(start, goal), listOf(start), false, true, false, here))
        assertFalse(RouteBack.asks(listOf(start, goal), listOf(start), true, false, false, here))
        assertFalse(RouteBack.asks(listOf(start, goal), listOf(start), true, true, true, here))
        // standing at the start: no way back to ask about
        assertFalse(RouteBack.asks(listOf(start, goal), listOf(start), true, true, false, GeoPoint(46.6702, 11.1601)))
        // far from the start: asked (the sims' position was far away)
        assertTrue(RouteBack.asks(listOf(start, goal), listOf(start), true, true, false, here))
        // position unknown: asked (Yes then says so and records plainly)
        assertTrue(RouteBack.asks(listOf(start, goal), listOf(start), true, true, false, null))
    }

    @Test fun planIsNullWhenThePlanChangedMeanwhile() {
        assertNull(RouteBack.plan(listOf(start, goal), here))
        assertNull(RouteBack.plan(emptyList(), here))
        assertEquals(listOf(here, start), RouteBack.plan(listOf(start), here))
    }
}
