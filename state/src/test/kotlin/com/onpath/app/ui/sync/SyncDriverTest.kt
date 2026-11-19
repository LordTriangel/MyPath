package com.onpath.app.ui.sync

import com.onpath.app.plansync.SyncMark
import com.onpath.app.plansync.SyncPoll
import com.onpath.app.sync.SyncRound
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.onpath.app.plansync.SyncState
import com.onpath.app.plansync.SyncReport
import com.onpath.app.plansync.SyncPassResult

/**
 * When the browser sync runs while the app is open ([SyncDriver], cleanup piece 4): the triggers the view model wired
 * in its init block, now as behaviour – on the test thread (an unconfined scope), with a clock and a "sleep" the test
 * moves ([wake]). The rules themselves (intervals, debounce) are SyncPollTest's.
 */
class SyncDriverTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    private class FakeEngine : SyncEngine {
        override val state = MutableStateFlow(SyncState(paired = true))
        override val marks = MutableStateFlow<Map<String, SyncMark>>(emptyMap())
        val asked = ArrayList<String>()
        val seen = ArrayList<String>()
        var started = 0; var marksLoaded = 0
        var quietNow = false; var busyNow = false
        override fun loadMarks() { marksLoaded++ }
        override fun markSeen(uuid: String) { seen += uuid }
        override fun start() { started++ }
        override fun quiet() = quietNow
        override fun busy() = busyNow
        override fun request(why: String, manual: Boolean) { asked += if (manual) "$why (manual)" else why }
        override fun poll(why: String): Boolean = if (busyNow) false else { asked += why; true }
    }

    private class FakeHost : SyncHost {
        override val screen = MutableStateFlow(true to false)
        override val shown = MutableStateFlow(SyncPoll.Shown.OTHER)
        val log = ArrayList<String>()
        override fun ended() { log += "ended" }
        override fun placesChanged() { log += "places" }
        override fun toursChanged(toRoute: List<Long>) { log += "tours $toRoute" }
        override fun newerData(tiles: List<String>) { log += "data $tiles" }
        /** My routes opens on a tab / closes, as the view model's screens say it. */
        fun show(s: SyncPoll.Shown) {
            shown.value = s
            screen.value = true to (s != SyncPoll.Shown.OTHER)
        }
    }

    private val engine = FakeEngine()
    private val host = FakeHost()
    private val recording = MutableStateFlow(false)
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    @After fun end() = scope.cancel()

    private var now = 1_000_000L
    /** Who waits, for how long (ms). */
    private val sleepers = ArrayList<Pair<Long, CompletableDeferred<Unit>>>()
    private suspend fun sleep(ms: Long) {
        val d = ms to CompletableDeferred<Unit>()
        sleepers += d
        try { d.second.await() } finally { sleepers -= d }
    }
    private val waits: List<Long> get() = sleepers.map { it.first }
    /** The time the oldest sleeper waits for passes; returns it. */
    private fun wake(): Long {
        val (ms, d) = sleepers.first()
        now += ms
        d.complete(Unit)
        return ms
    }

    private val driver = SyncDriver(engine, host, recording, { recording.value }, scope, Dispatchers.Unconfined, clock = { now }, sleep = ::sleep)

    private fun done(manual: Boolean = false) =
        SyncReport(manual, SyncPassResult(SyncRound.Outcome.Done(pulled = 0, pushed = 0, unreadable = 0)))

    // ---------------- triggers ----------------

    @Test fun atStartTheSyncStartsAndOnThePlanMapNothingPolls() {
        driver.start()
        assertEquals(1, engine.started); assertEquals(1, engine.marksLoaded)
        assertTrue("the Plan map asks for nothing by itself", engine.asked.isEmpty())
        assertTrue(waits.isEmpty())
    }

    /** Owner 2026-10-30 / 2026-11-13: a pass right away when My routes opens, then about every 15 s while it is shown. */
    @Test fun myRoutesOpenedPullsAtOnceAndThenPollsEvery15Seconds() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        assertEquals(listOf("my routes opened"), engine.asked)
        assertEquals("the poll counts from that pass", listOf(SyncPoll.BASE_MS), waits)
        wake()
        assertEquals(listOf("my routes opened", "poll"), engine.asked)
        assertEquals(listOf(SyncPoll.BASE_MS), waits)
        wake()
        assertEquals(3, engine.asked.size)
    }

    @Test fun leavingMyRoutesStopsThePoll() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_DONE)
        assertEquals(1, waits.size)
        host.show(SyncPoll.Shown.OTHER)
        assertTrue("nobody waits for the next poll", waits.isEmpty())
        assertEquals(listOf("my routes opened"), engine.asked)
    }

    @Test fun theAppInTheBackgroundPollsNothing() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        host.screen.value = false to true // My routes still open, the app left
        assertTrue(waits.isEmpty())
    }

    /** One pass at a time: the poll looks again a little later while one runs or waits. */
    @Test fun aPollWhileAPassRunsLooksAgainLater() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        engine.busyNow = true
        wake()
        assertEquals(listOf("my routes opened"), engine.asked)
        assertEquals(listOf(SyncPoll.BUSY_RECHECK_MS), waits)
        engine.busyNow = false
        wake()
        assertEquals(listOf("my routes opened", "poll"), engine.asked)
    }

    /** A pass that did not reach the server doubles the interval; one that did resets it. */
    @Test fun aFailedPassBacksOffAndAGoodOneResets() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        driver.onReport(SyncReport(manual = false, result = null, error = true))
        wake() // the 15 s it slept already: not due yet, 30 s count from the failed pass
        assertEquals(listOf("my routes opened"), engine.asked)
        assertEquals(listOf(SyncPoll.BASE_MS), waits)
        wake()
        assertEquals(listOf("my routes opened", "poll"), engine.asked)
        assertEquals(listOf(2 * SyncPoll.BASE_MS), waits)
        driver.onReport(done())
        wake()
        assertEquals(listOf(SyncPoll.BASE_MS), waits)
    }

    /** Never during a recording – and what waited comes when the tour ends. */
    @Test fun duringATourNothingIsAskedAndItsEndAsksOnce() {
        recording.value = true
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        assertTrue(engine.asked.isEmpty()); assertTrue(waits.isEmpty())
        driver.pull()
        assertFalse("no spinner that waits for the end of the tour", driver.pulling)
        assertTrue(engine.asked.isEmpty())
        recording.value = false
        assertEquals("tour ended", engine.asked.first())
        assertEquals("…and the poll takes up while My routes is shown", 1, waits.size)
    }

    @Test fun unpairedNothingIsAsked() {
        engine.state.value = SyncState(paired = false)
        driver.start()
        host.show(SyncPoll.Shown.SYNC_PAGE)
        driver.pull()
        assertTrue(engine.asked.isEmpty()); assertTrue(waits.isEmpty()); assertFalse(driver.pulling)
        // paired now (the code was scanned on this page): the poll starts
        engine.state.value = SyncState(paired = true)
        assertEquals(listOf("poll"), engine.asked)
    }

    /** The pass on open is debounced: the tab chosen right after opening asks for no second one; later it does. */
    @Test fun openingTwiceInARowAsksOnce() {
        driver.start()
        host.show(SyncPoll.Shown.ROUTES_DONE)
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        assertEquals(listOf("my routes opened"), engine.asked)
        host.show(SyncPoll.Shown.ROUTES_DONE)
        now += SyncPoll.OPEN_DEBOUNCE_MS
        host.show(SyncPoll.Shown.ROUTES_PLANNED)
        assertEquals(listOf("my routes opened", "planned tab"), engine.asked)
        engine.busyNow = true
        now += SyncPoll.OPEN_DEBOUNCE_MS
        host.show(SyncPoll.Shown.SYNC_PAGE)
        assertEquals("a pass runs already: none asked on top", 2, engine.asked.size)
    }

    // ---------------- pull to refresh ----------------

    @Test fun pullAsksForAPassAndSpinsUntilItsReport() {
        driver.start()
        driver.pull()
        assertTrue(driver.pulling)
        assertEquals(listOf("pulled (manual)"), engine.asked)
        driver.onReport(done(manual = false)) // another pass ended meanwhile: not this one's
        assertTrue(driver.pulling)
        driver.onReport(done(manual = true))
        assertFalse(driver.pulling)
        assertTrue("its net is gone too", waits.isEmpty())
    }

    /** Never a spinner that stays: a pass that hangs in the network ends it after [SyncDriver.PULL_SPIN_MAX_MS]. */
    @Test fun thePullSpinnerEndsByItselfWhenNoReportComes() {
        driver.start()
        driver.pull()
        driver.pull() // pulled again while it spins: one net
        assertEquals(listOf(SyncDriver.PULL_SPIN_MAX_MS), waits)
        wake()
        assertFalse(driver.pulling)
    }

    @Test fun whileTheServerAskedForQuietAPullAsksNothing() {
        driver.start()
        engine.quietNow = true
        driver.pull()
        assertFalse(driver.pulling); assertTrue(engine.asked.isEmpty())
    }

    // ---------------- a pass's report ----------------

    @Test fun whatAPassBroughtIsHandedToTheViewModel() {
        driver.onReport(done())
        assertTrue("nothing came: nothing read again", host.log.isEmpty())
        driver.onReport(SyncReport(false, SyncPassResult(SyncRound.Outcome.Done(2, 0, 0), toRoute = listOf(7L, 9L),
            toursChanged = true, newerData = listOf("E10_N45"), placesChanged = true)))
        assertEquals(listOf("places", "tours [7, 9]", "data [E10_N45]"), host.log)
    }

    /** Zuhause / favourites from the browser alone: the kept places are read again, My routes is not. */
    @Test fun keptPlacesFromTheBrowserAreReadAgainWithoutTheList() {
        driver.onReport(SyncReport(false, SyncPassResult(SyncRound.Outcome.Done(1, 0, 0), placesChanged = true)))
        assertEquals(listOf("places"), host.log)
    }

    @Test fun thePairingEndedInTheBrowserIsSaid() {
        driver.onReport(SyncReport(manual = false, result = null, ended = true))
        assertEquals(listOf("ended"), host.log)
    }

    // ---------------- marks ----------------

    @Test fun openingAMarkedRouteTakesItsMarkAndOnlyThen() {
        engine.marks.value = mapOf("u-1" to SyncMark.NEW)
        driver.markSeen("")
        driver.markSeen("u-2")
        assertTrue(engine.seen.isEmpty())
        driver.markSeen("u-1")
        assertEquals(listOf("u-1"), engine.seen)
        assertEquals(engine.marks.value, driver.marks.value)
    }
}
