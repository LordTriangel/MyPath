package com.onpath.app.ui

import com.onpath.app.cells.CellId
import com.onpath.app.cells.CellState
import com.onpath.app.data.ProgressMemory
import com.onpath.app.domain.LoadStep
import com.onpath.app.domain.RouteShares
import com.onpath.app.domain.StepClock
import com.onpath.app.ui.progress.RouteWait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's promise for the wait window (2026-11-18/19: never back, never standing), the cases a review of the first
 * build found broken (red on fcc6cdbf1, the cases marked so in the commit).
 */
class WaitPromiseTest {
    private val mb = 1_048_576L
    private val a = CellId(543, 361)
    private val b = CellId(544, 361)

    /** A recalc while a run is on (an edit, an activity change, routing data arriving): the bar and rows start afresh. */
    @Test fun routeWindow_aNewRunStartsAfresh() {
        val memory = ProgressMemory.InMemory()
        val rw = RouteWait(memory)
        for (t in 0 until 10) rw.update(true, if (t < 5) 0 to 1 else 1 to 1, if (t < 5) t / 5f else null, null, t * 1000L, gen = 1)
        // the plan was changed: run 2 of 4 legs, routing stays on
        val again = rw.update(true, 0 to 4, 0.05f, null, 10_000, gen = 2)!!
        assertTrue("starts afresh: ${again.percent}", again.percent < 20)
        assertEquals("the first leg runs", 0, again.steps.indexOfFirst { it.state == com.onpath.app.domain.WaitStepState.NOW })
        for (t in 11 until 40) rw.update(true, 0 to 4, 0.05f + t / 100f, null, t * 1000L, gen = 2)
        rw.update(false, null, null, null, 40_000, gen = 2)
        assertTrue("run 2 had no check: nothing wrong learned – ${memory.routeCheck}", memory.routeCheck <= RouteShares.DEFAULT_CHECK + 1e-9)
    }

    /** A check far longer than its share: whole percents keep coming, and the time line says it takes longer. */
    @Test fun routeWindow_aLongCheckKeepsMovingAndSaysSo() {
        val rw = RouteWait(ProgressMemory.InMemory())
        val uis = (0 until 300).map { t -> rw.update(true, if (t < 6) 0 to 1 else 1 to 1, if (t < 6) t / 6f else null, null, t * 1000L, gen = 1)!! }
        val shown = uis.map { it.percent }
        for (i in 1 until shown.size) assertTrue("never back: $shown", shown[i] >= shown[i - 1])
        assertStandsAtMost("route window, long check", shown.take(120), 30)
        assertTrue(shown.all { it <= 99 })
        assertEquals("taking a bit longer…", uis[200].window.line)
    }

    /** A join estimated at 20 s that takes 300 s without reporting: no 99 % for minutes, no frozen "5 seconds". */
    @Test fun waitWindow_aLongLastStepKeepsMovingAndSaysSo() {
        MapWait.joinBytesPerS = 20.0 * mb
        val w = MapWait()
        val uis = (0 until 330).map { t ->
            val s = if (t < 30) CellState.Downloading(t * mb, 30 * mb) else CellState.Installing
            w.update(listOf(a), mapOf(a to s), mapOf(b to 370 * mb), null, null, t * 1000L)!!
        }
        val shown = uis.map { it.percent }
        for (i in 1 until shown.size) assertTrue("never back: $shown", shown[i] >= shown[i - 1])
        assertTrue("not at 99 after 2 minutes of joining: ${shown[150]}", shown[150] < 99)
        assertStandsAtMost("long join", shown.take(200), 45)
        assertEquals("taking a bit longer…", uis[200].line)
    }

    /** Ten minutes offline count into no step: no "about 25 minutes left" afterwards, nothing learned from it. */
    @Test fun pausedTimeIsNoStepTime() {
        val c = StepClock()
        c.at(LoadStep.PREPARE, 0); c.at(LoadStep.PREPARE, 1_000); c.at(LoadStep.PREPARE, 601_000); c.at(LoadStep.DOWNLOAD, 602_000)
        assertTrue("the gap is no prepare time: ${c.durations()}", c.durations().getValue(LoadStep.PREPARE) < 5_000)
        val w = MapWait()
        w.update(listOf(a), mapOf(a to CellState.Downloading(2 * mb, 20 * mb)), emptyMap(), null, null, 0)
        w.update(listOf(a), mapOf(a to CellState.Downloading(4 * mb, 20 * mb)), emptyMap(), null, null, 2_000)
        for (t in 3..600) w.update(listOf(a), mapOf(a to CellState.Offline("x")), emptyMap(), null, null, t * 1000L)
        w.update(listOf(a), mapOf(a to CellState.Downloading(5 * mb, 20 * mb)), emptyMap(), null, null, 601_000)
        val back = w.update(listOf(a), mapOf(a to CellState.Downloading(6 * mb, 20 * mb)), emptyMap(), null, null, 603_000)!!
        assertTrue("time left after the offline time: ${back.secondsLeft}", (back.secondsLeft ?: 0) < 120)
    }

    /** Everything arrived: the window ends on 100 %, not on the 97–99 % it lingers with. */
    @Test fun theWindowEndsAt100() {
        val w = MapWait()
        w.update(listOf(a), mapOf(a to CellState.Downloading(5 * mb, 10 * mb)), emptyMap(), null, null, 0)
        w.update(listOf(a), mapOf(a to CellState.Installing), emptyMap(), null, null, 1_000)
        val last = w.update(listOf(a), mapOf(a to CellState.Ready("v")), emptyMap(), null, null, 2_000)
        assertNotNull("one last picture at 100 %", last)
        assertEquals(100, last!!.percent)
    }

    /** The window goes while its loads go on (A turns into B): the sign goes on from the window's percent, never below. */
    @Test fun handoverToTheSignNeverGoesBack() {
        val action = com.onpath.app.domain.ActionPercent()
        val waits = MapWaits({ com.onpath.app.domain.StepShares.DEFAULT }, learn = {}, action = action)
        var shown = 0
        for (t in 0 until 40) shown = waits.tick("modal", t * 1000L) { w ->
            w.update(listOf(a), mapOf(a to CellState.Downloading(t * mb, 40 * mb)), emptyMap(), null, null, t * 1000L) }!!.percent
        // the sign's loads: this area and a big one beside it – by bytes only 27 %
        fun items(f: Double) = listOf(com.onpath.app.domain.ActionPercent.Item("cell:a", 40 * mb, f),
            com.onpath.app.domain.ActionPercent.Item("cell:b", 100 * mb, 0.1))
        action.update(items(0.6))
        waits.hide(40_000)
        val sign = (0 until 10).map { i -> action.update(items(0.6 + i * 0.04))!! }
        assertTrue("the sign starts at the window's $shown: $sign", sign.first() >= shown)
        for (i in 1 until sign.size) assertTrue("and moves on, never back: $sign", sign[i] >= sign[i - 1])
        assertTrue(sign.last() > sign.first())
    }

    /** The first map's wait goes away for an offline blip and comes back for the same job: its bar where it was, not 0. */
    @Test fun theSameJobComesBackWhereItWas() {
        val waits = MapWaits({ com.onpath.app.domain.StepShares.DEFAULT }, learn = {}, action = com.onpath.app.domain.ActionPercent())
        val job = "first" to setOf(a)
        var before = 0
        for (t in 0 until 60) before = waits.tick(job, t * 1000L) { w ->
            w.update(listOf(a), mapOf(a to CellState.Queued(1, 120)), emptyMap(), null, null, t * 1000L) }!!.percent
        for (t in 60 until 70) waits.tick(null, t * 1000L) { null } // offline: the window is away, the card says so
        val after = waits.tick(job, 70_000) { w -> w.update(listOf(a), mapOf(a to CellState.Queued(1, 60)), emptyMap(), null, null, 70_000) }!!
        assertTrue("$before → ${after.percent}", after.percent >= before && before > 5)
    }

    /** A failing update: the last picture stays (the map still loads), nothing is learned. */
    @Test fun aFailingUpdateKeepsTheWindow() {
        var learned = false
        val waits = MapWaits({ com.onpath.app.domain.StepShares.DEFAULT }, learn = { learned = true }, action = com.onpath.app.domain.ActionPercent())
        val ok = waits.tick("modal", 0) { w -> w.update(listOf(a), mapOf(a to CellState.Downloading(mb, 10 * mb)), emptyMap(), null, null, 0) }
        val broken = waits.tick("modal", 1_000) { throw IllegalStateException("states unreadable") }
        assertEquals(ok, broken)
        assertTrue(!learned)
    }

    /** A wait that paused teaches nothing (its times are no measure of the steps). */
    @Test fun aPausedWaitTeachesNothing() {
        val w = MapWait()
        w.update(listOf(a), mapOf(a to CellState.Downloading(mb, 10 * mb)), emptyMap(), null, null, 0)
        w.update(listOf(a), mapOf(a to CellState.Offline("x")), emptyMap(), null, null, 1_000)
        w.update(listOf(a), mapOf(a to CellState.Downloading(5 * mb, 10 * mb)), emptyMap(), null, null, 2_000)
        assertTrue(!w.learnable)
    }

    private fun assertStandsAtMost(what: String, shown: List<Int>, maxS: Int) {
        var run = 0
        for (i in 1 until shown.size) {
            run = if (shown[i] == shown[i - 1] && shown[i] < 99) run + 1 else 0
            assertTrue("$what: ${shown[i]} % stands for more than $maxS s (second $i): $shown", run <= maxS)
        }
    }
}
