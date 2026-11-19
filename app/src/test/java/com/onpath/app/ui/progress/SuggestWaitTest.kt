package com.onpath.app.ui.progress

import com.onpath.app.data.ProgressMemory
import com.onpath.app.domain.WaitStepState
import com.onpath.app.domain.WaitSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The round-trip search's wait window (form A, owner 2026-11-19): the method's phases as three steps – Wege lesen ·
 * Runden suchen · bewerten –, ONE bar that never goes back and never stands (also over a second run), the shares
 * learned from a run that came to its cards only.
 */
class SuggestWaitTest {
    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)

    private val phases = listOf("network", "facts", "place", "loops", "judging")

    /** [laps] ended phases, each 1 s. */
    private fun laps(n: Int) = (0 until n).map { phases[it % phases.size] to 1.0 }

    @Test fun thePhasesAreThreeSteps() {
        val w = SuggestWait(ProgressMemory.InMemory())
        val first = w.update(true, false, laps(0), 0)!!
        assertEquals(listOf("Wege lesen", "Runden suchen", "bewerten"), first.steps.map { it.label })
        assertEquals(listOf(WaitStepState.NOW, WaitStepState.LATER, WaitStepState.LATER), first.steps.map { it.state })
        assertEquals("Rundtouren werden gesucht", first.window.title)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 2), (0..6).map { SuggestWait.stepOf(it) })
        val judging = w.update(true, false, laps(4), 3_000)!!
        assertEquals(listOf(WaitStepState.DONE, WaitStepState.DONE, WaitStepState.NOW), judging.steps.map { it.state })
        assertTrue("never more than 3 rows", WaitSteps.shown(judging.steps).size <= WaitSteps.MAX)
    }

    /** Over a whole search – a first run, then a second one after fetched suggest files – the bar never goes back. */
    @Test fun theBarNeverGoesBackNorStands() {
        val w = SuggestWait(ProgressMemory.InMemory())
        // a phase ends every 3 s; the second run's phases (6..10) come after a fetch
        val shown = (0 until 120).map { t -> w.update(true, false, laps(minOf(t / 6, 10)), t * 500L)!!.percent }
        for (i in 1 until shown.size) assertTrue("never back: $shown", shown[i] >= shown[i - 1])
        assertTrue("moves: $shown", shown.last() > shown[10])
        assertTrue(shown.all { it <= 99 })
        // never standing long: within any 20 ticks (10 s) the number moves – up to the last whole percents an overrun
        // keeps for itself (OneBar: never 100 before the cards are there)
        for (i in 0 until shown.size - 20) if (shown[i] < 98) assertTrue("stands at ${shown[i]} from tick $i: $shown", shown[i + 20] > shown[i])
        assertTrue("the long second run says so", w.update(true, false, laps(10), 120 * 500L)!!.late)
    }

    /**
     * A long phase (the loops, 15 s after three quick ones): the time left falls or stays, it never counts UP while the
     * search works (a review of the release hardening: an estimate from the measured share alone did, 10 → 25 s).
     */
    @Test fun theTimeLeftNeverCountsUpInALongPhase() {
        val w = SuggestWait(ProgressMemory.InMemory())
        val done = listOf(3.0, 2.0, 1.0).mapIndexed { i, s -> phases[i] to s }
        val ends = listOf(3_000L, 5_000L, 6_000L)
        val left = (0..42).mapNotNull { t ->
            val now = t * 500L
            val ui = w.update(true, false, done.take(ends.count { it <= now }), now)!!
            ui.secondsLeft.takeIf { now >= 6_000 && !ui.late }
        }
        assertTrue("a time is said in the long phase", left.size > 10)
        assertTrue("never more: $left", left.zipWithNext().all { (a, b) -> b <= a })
    }

    /** Loading routing data first: step 1 with its own line, the bar creeps; then the phases as usual. */
    @Test fun routingDataFirstSaysSo() {
        val w = SuggestWait(ProgressMemory.InMemory())
        val ui = (0 until 20).map { t -> w.update(true, true, emptyList(), t * 500L)!! }
        assertEquals("Wegedaten werden geladen …", ui.last().window.line)
        assertEquals(WaitStepState.NOW, ui.last().steps[0].state)
        val after = w.update(true, false, laps(1), 10_500)!!
        assertTrue(after.percent >= ui.last().percent)
    }

    /** A run that came to its cards teaches the shares (bounded); a cancelled search or a failed one teaches nothing. */
    @Test fun onlyAFinishedRunTeaches() {
        val memory = ProgressMemory.InMemory()
        val w = SuggestWait(memory)
        val slowJudging = listOf("network" to 1.0, "facts" to 1.0, "place" to 1.0, "loops" to 1.0, "judging" to 6.0)
        w.update(true, false, slowJudging.take(2), 0)
        assertNull(w.update(false, false, slowJudging.take(2), 1_000)) // cancelled
        assertEquals(ProgressMemory.SUGGEST_DEFAULT, memory.suggestShares)
        w.update(true, false, slowJudging, 0)
        assertNull(w.update(false, false, slowJudging, 10_000, cards = true))
        assertTrue("rating took longer than its share: ${memory.suggestShares}", memory.suggestShares.join > ProgressMemory.SUGGEST_DEFAULT.join)
        assertEquals(1.0, memory.suggestShares.prepare + memory.suggestShares.download + memory.suggestShares.join, 1e-9)
    }
}
