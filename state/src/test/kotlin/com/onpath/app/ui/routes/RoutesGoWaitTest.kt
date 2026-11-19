package com.onpath.app.ui.routes

import androidx.compose.runtime.snapshots.Snapshot
import com.onpath.app.domain.routes.RoutesGo
import com.onpath.app.routing.PlannedLeg
import com.onpath.app.routing.PlannedRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WAIT of "Los geht's" in My routes ([RoutesGoSlice.goFromRoutes], [RoutesGo.step]) as flows: the plan is loaded, then the
 * start waits while the line is calculated – WAIT → START when it arrives, → DROP when the plan changes or a tour starts
 * meanwhile, a second go cancels the first, and after [RoutesGo.MAX_WAIT_MS] nothing starts. Same harness as [RoutesFlowTest]
 * ([RoutesFlowBase]) but WITHOUT `OwnComposeSnapshot`: the fake host's route / routing / touring are Compose state and the
 * test writes them in `Snapshot.withMutableSnapshot { }`, which applies to the global snapshot and so wakes the `snapshotFlow`
 * of the wait. Main is a virtual-time dispatcher ([com.onpath.app.testkit.VirtualDelayDispatcher]), so the 60 s timeout is
 * reached by `advance`, not by waiting.
 */
class RoutesGoWaitTest : RoutesFlowBase() {
    override val virtualTime = true

    private fun route3() = PlannedRoute(listOf(PlannedLeg(listOf(a, b, c), false)), 4_000.0, 0.0, 0.0, 3_000.0, null)
    private fun App.virtual() = main as com.onpath.app.testkit.VirtualDelayDispatcher
    /** The line is calculated (or not): written to the global snapshot so the waiting flow sees it. */
    private fun App.lineIs(routing: Boolean, route: PlannedRoute?) = Snapshot.withMutableSnapshot { routingNow = routing; routeNow = route }
    private fun App.startsInto(into: MutableList<String>, name: String): () -> Unit = { into += name }
    private fun traces() = problems.traced("routes")

    @Test fun theStartWaitsWhileTheLineIsCalculatedAndStartsOnceWhenItArrives() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        app.go.goFromRoutes(planned("Alm"), start = app.startsInto(started, "go")); app.settle()
        assertEquals("the plan is loaded", 3, app.plan.points.size)
        assertTrue("waiting: nothing started, nothing said", started.isEmpty() && traces().none { it.contains("no tour started") })
        app.lineIs(routing = false, route = route3()); app.settle()
        assertEquals(listOf("go"), started)
        assertTrue(traces().any { it.contains("the line of \"Alm\" is there – the normal start") })
        app.lineIs(routing = true, route = null); app.lineIs(routing = false, route = route3()); app.settle()
        assertEquals("started exactly once", listOf("go"), started)
    }

    @Test fun aLineThatNeverComesEndsTheWaitWithoutAStart() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        app.go.goFromRoutes(planned("Alm"), start = app.startsInto(started, "go")); app.settle()
        app.lineIs(routing = false, route = null); app.settle()          // the run ended without a line (no map, failed)
        assertTrue(started.isEmpty())
        assertTrue(traces().any { it.contains("no tour started for \"Alm\"") })
    }

    @Test fun aPlanChangedWhileWaitingStartsNothingEvenWhenTheLineThenArrives() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        app.go.goFromRoutes(planned("Alm"), start = app.startsInto(started, "go")); app.settle()
        Snapshot.withMutableSnapshot { app.plan.edit { add(c) } }; app.settle()
        assertTrue(traces().any { it.contains("no tour started for \"Alm\"") })
        app.lineIs(routing = false, route = route3()); app.settle()
        assertTrue(started.isEmpty())
    }

    @Test fun aTourStartedWhileWaitingStartsNothing() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        app.go.goFromRoutes(planned("Alm"), start = app.startsInto(started, "go")); app.settle()
        Snapshot.withMutableSnapshot { app.touring = true }; app.settle()
        app.lineIs(routing = false, route = route3()); app.settle()
        assertTrue(started.isEmpty())
        assertTrue(traces().any { it.contains("no tour started for \"Alm\"") })
    }

    @Test fun aSecondGoWhileWaitingCancelsTheFirstAndOnlyTheSecondCallersStartRuns() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        val t = planned("Alm")
        app.go.goFromRoutes(t, start = app.startsInto(started, "first")); app.settle()
        app.go.goFromRoutes(t, start = app.startsInto(started, "second")); app.settle()
        assertTrue("both waiting", started.isEmpty())
        app.lineIs(routing = false, route = route3()); app.settle()
        assertEquals("exactly one start, the second caller's", listOf("second"), started)
    }

    @Test fun afterSixtySecondsWithoutALineNothingStartsAndThePlanStaysLoadedForTheSheetsOwnStart() {
        val app = App().also { it.routingNow = true }
        val started = ArrayList<String>()
        app.go.goFromRoutes(planned("Alm"), start = app.startsInto(started, "go")); app.settle()
        app.virtual().advance(RoutesGo.MAX_WAIT_MS - 1); app.settle()
        assertTrue("still waiting just before the limit", traces().none { it.contains("no tour started") })
        app.virtual().advance(1); app.settle()
        assertTrue(traces().any { it.contains("no tour started for \"Alm\" (changed meanwhile, no line, or waited too long) – the plan sheet's Los geht's stays") })
        app.lineIs(routing = false, route = route3()); app.settle()
        assertTrue("a line after the limit starts nothing", started.isEmpty())
        assertEquals("the plan stays loaded, nothing is said to the user", 3, app.plan.points.size)
        assertTrue(app.notes.isEmpty())
        assertEquals(null, app.save.problem)
    }
}
