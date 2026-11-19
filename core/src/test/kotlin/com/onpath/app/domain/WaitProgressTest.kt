package com.onpath.app.domain

import com.onpath.app.cells.CellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure math of "one progress design, two forms" (owner 2026-11-19): the learned shares of the steps, the ONE bar
 * (never back, never standing), at most 3 step rows, the time left, form B's one number over the whole action.
 */
class WaitProgressTest {

    // ---------------------------------------------------------------- learned shares

    @Test fun sharesStartAt30_50_20() {
        val d = StepShares.DEFAULT
        assertEquals(0.30, d.prepare, 1e-9); assertEquals(0.50, d.download, 1e-9); assertEquals(0.20, d.join, 1e-9)
        assertEquals(0.80, d.before(LoadStep.JOIN), 1e-9)
    }

    /** A phone that joins slowly: the join's share grows load by load, the sum stays 1, nothing leaves its bounds. */
    @Test fun sharesLearnFromTheMeasuredTimes() {
        var s = StepShares.DEFAULT
        repeat(10) { s = s.learn(mapOf(LoadStep.PREPARE to 30_000L, LoadStep.DOWNLOAD to 20_000L, LoadStep.JOIN to 50_000L)) }
        assertTrue("join learned: ${s.join}", s.join in 0.45..0.51)
        assertEquals(1.0, s.prepare + s.download + s.join, 1e-9)
        // an extreme load: bounded
        repeat(50) { s = s.learn(mapOf(LoadStep.PREPARE to 1L, LoadStep.DOWNLOAD to 1L, LoadStep.JOIN to 1_000_000L)) }
        assertTrue(s.join <= StepShares.MAX + 1e-9 && s.prepare >= StepShares.MIN - 1e-9 && s.download >= StepShares.MIN - 1e-9)
    }

    /** A step that did not run (the server had the map ready) says nothing about its time; a load from caches teaches nothing. */
    @Test fun sharesLearnOnlyFromStepsThatRan() {
        val s = StepShares.DEFAULT.learn(mapOf(LoadStep.DOWNLOAD to 10_000L, LoadStep.JOIN to 10_000L))
        assertEquals(0.30, s.prepare, 1e-6)
        assertTrue(s.join > 0.20 && s.download < 0.50)
        assertEquals(StepShares.DEFAULT, StepShares.DEFAULT.learn(mapOf(LoadStep.DOWNLOAD to 300L, LoadStep.JOIN to 200L)))
    }

    /** Stored and read back; anything damaged gives the start values – never throws. */
    @Test fun sharesPersistAndFallBack() {
        val s = StepShares.DEFAULT.learn(mapOf(LoadStep.PREPARE to 5_000L, LoadStep.DOWNLOAD to 5_000L, LoadStep.JOIN to 20_000L))
        val back = StepShares.decode(s.encode())
        assertEquals(s.join, back.join, 1e-4)
        for (bad in listOf(null, "", "v1:", "v1:a,b,c", "v1:0.3,0.5", "v2:0.3,0.5,0.2", "v1:NaN,0.5,0.2", "garbage"))
            assertEquals(bad, StepShares.DEFAULT, StepShares.decode(bad))
        // out of bounds on disk: bounded on reading
        val odd = StepShares.decode("v1:0.98,0.01,0.01")
        assertTrue(odd.prepare <= StepShares.MAX + 1e-9 && odd.join >= StepShares.MIN - 1e-9)
        assertEquals(RouteShares.DEFAULT_CHECK, RouteShares.decode("x"), 1e-9)
        assertEquals(0.2, RouteShares.decode(RouteShares.encode(0.2)), 1e-4)
        assertEquals(RouteShares.MAX_CHECK, RouteShares.decode("r1:0.9"), 1e-9)
    }

    // ---------------------------------------------------------------- 3 rows at most

    /** A route of 5 legs + the check: never more than 3 rows – the last done, the running one, the next. */
    @Test fun threeRowsMoveAlongFiveLegs() {
        val labels = (1..5).map { "leg $it" to null } + ("check" to null)
        fun shown(now: Int) = WaitSteps.shown(WaitSteps.of(labels, now)).map { it.label }
        assertEquals(listOf("leg 1", "leg 2", "leg 3"), shown(0))
        assertEquals(listOf("leg 1", "leg 2", "leg 3"), shown(1))
        assertEquals(listOf("leg 2", "leg 3", "leg 4"), shown(2))
        assertEquals(listOf("leg 4", "leg 5", "check"), shown(4))
        assertEquals(listOf("leg 4", "leg 5", "check"), shown(5))
        for (n in 0..5) assertTrue(shown(n).size <= WaitSteps.MAX)
        val rows = WaitSteps.shown(WaitSteps.of(labels, 2))
        assertEquals(listOf(WaitStepState.DONE, WaitStepState.NOW, WaitStepState.LATER), rows.map { it.state })
    }

    // ---------------------------------------------------------------- the ONE bar

    /** A step that reports nothing for a minute: the bar creeps on inside the step, never into the next one's part. */
    @Test fun oneBarNeverStandsInsideAStep() {
        val bar = OneBar()
        val shown = (0..60).map { t -> bar.next(raw = 0.30, stepEnd = 0.80, stepLeftMs = 40_000, nowMs = t * 1000L) }
        var run = 0
        for (i in 1 until shown.size) { run = if (shown[i] == shown[i - 1]) run + 1 else 0; assertTrue("stands at ${shown[i]}: $shown", run <= 5) }
        assertTrue("inside the step: ${shown.last()}", shown.last() in 31..79)
    }

    /** A measurement that goes back (a retried download, a new estimate) never moves the bar back; 100 only at the end. */
    @Test fun oneBarNeverBack() {
        val bar = OneBar()
        val a = bar.next(0.6, 0.8, null, 0)
        val b = bar.next(0.35, 0.8, null, 1000)
        assertTrue("$a → $b", b >= a)
        assertTrue(bar.next(1.0, 1.0, null, 2000) <= 99)
        assertEquals(100, bar.next(1.0, 1.0, null, 3000, finished = true))
    }

    /** Offline / Wi-Fi: the bar stands (grey) – no creep while nothing can happen. */
    @Test fun oneBarStandsWhilePaused() {
        val bar = OneBar()
        val a = bar.next(0.4, 0.8, null, 0)
        val b = bar.next(0.4, 0.8, null, 60_000, paused = true)
        assertEquals(a, b)
    }

    // ---------------------------------------------------------------- time left

    @Test fun timeLeftOnceEstimable() {
        assertNull(TimeLeft.seconds(0.01, 10_000))
        assertNull(TimeLeft.seconds(0.5, 1_000))
        assertEquals(10, TimeLeft.seconds(0.5, 10_000))
        assertEquals("the step's own estimate when longer", 120, TimeLeft.seconds(0.5, 10_000, stepLeftS = 120))
        assertEquals("about 20 seconds left", TimeLeft.text(18))
        assertEquals("about 1 minute left", TimeLeft.text(70))
        assertEquals("about 3 minutes left", TimeLeft.text(185))
    }

    // ---------------------------------------------------------------- form B: one number over the action

    /** Three areas of a multi-day tour and the routing data: one number over all, never back, finished ones count in full. */
    @Test fun oneNumberOverSeveralLoads() {
        val a = ActionPercent()
        val mb = 1_048_576L
        val seen = ArrayList<Int>()
        fun step(vararg items: ActionPercent.Item) = a.update(items.toList())!!.also { seen += it }
        step(ActionPercent.Item("a", 10 * mb, 0.0), ActionPercent.Item("b", 10 * mb, 0.0), ActionPercent.Item("c", 10 * mb, 0.0))
        step(ActionPercent.Item("a", 10 * mb, 0.6), ActionPercent.Item("b", 10 * mb, 0.1), ActionPercent.Item("c", 10 * mb, 0.0))
        // a leaves (joined): full; b retried from the start: not back
        step(ActionPercent.Item("b", 10 * mb, 0.0), ActionPercent.Item("c", 10 * mb, 0.3), ActionPercent.Item("routing", 5 * mb, 0.0))
        step(ActionPercent.Item("c", 10 * mb, 0.9), ActionPercent.Item("routing", 5 * mb, 0.8))
        for (i in 1 until seen.size) assertTrue("never back: $seen", seen[i] >= seen[i - 1])
        assertTrue(seen.last() in 80..99)
        assertNull("nothing loads: the action is over", a.update(emptyList()))
        assertEquals("a new action starts at its own 0", 0, a.update(listOf(ActionPercent.Item("x", mb, 0.0))))
    }

    /** One area by its steps: the server's own progress, the bytes, the pieces in – each inside its share. */
    @Test fun cellProgressBySteps() {
        val s = StepShares.DEFAULT
        assertEquals(0.0, CellProgress.fraction(CellState.Requested, s), 1e-9)
        assertEquals(0.15, CellProgress.fraction(CellState.Building("base", 0.5, 60), s), 1e-9)
        assertEquals(0.55, CellProgress.fraction(CellState.Downloading(50, 100), s), 1e-9)
        assertEquals(0.90, CellProgress.fraction(CellState.Installing, s, joined = 0.5), 1e-9)
        assertEquals(1.0, CellProgress.fraction(CellState.Ready("v"), s), 1e-9)
        assertEquals(LoadStep.JOIN, CellProgress.step(CellState.Installing))
    }

    // ---------------------------------------------------------------- the route

    @Test fun routeLegsThenTheCheck() {
        assertEquals(0.0, RouteShares.fraction(0, 4, null, false, 0.1), 1e-9)
        assertEquals(0.9 * 1.5 / 4, RouteShares.fraction(1, 4, 0.5, false, 0.1), 1e-9)
        assertEquals(0.9, RouteShares.fraction(4, 4, null, false, 0.1), 1e-9)
        assertEquals(0.9, RouteShares.fraction(2, 4, 0.3, true, 0.1), 1e-9)
        assertTrue(RouteShares.learn(0.1, 10_000, 10_000) > 0.1)
        assertEquals(0.1, RouteShares.learn(0.1, 100, 100), 1e-9)
    }
}
