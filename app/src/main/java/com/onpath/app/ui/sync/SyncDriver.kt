package com.onpath.app.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onpath.app.debug.launchSafe
import com.onpath.app.plansync.PlanSync
import com.onpath.app.plansync.SyncMark
import com.onpath.app.plansync.SyncPoll
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

/** The browser sync as its driver sees it ([PlanSync] in the app, a fake in tests). */
interface SyncEngine {
    val state: StateFlow<PlanSync.State>
    /** Routes from the browser not opened yet, by uuid. */
    val marks: StateFlow<Map<String, SyncMark>>
    /** Reads the stored marks into [marks] (blocking, never throws). */
    fun loadMarks()
    /** The mark of [uuid] goes (blocking). */
    fun markSeen(uuid: String)
    fun start()
    /** The server asked for quiet: its back-off wins over every trigger. */
    fun quiet(): Boolean
    /** A pass runs or waits. */
    fun busy(): Boolean
    fun request(why: String, manual: Boolean = false)
    /** The poll's pass; false = one runs or waits already. */
    fun poll(why: String): Boolean
}

/** What [SyncDriver] reads of the app's screens and hands back to the view model when a pass ended. */
interface SyncHost {
    /** (the app is on screen, a screen where routes matter is shown – [SyncPoll.routesScreen]), as it changes. */
    val screen: Flow<Pair<Boolean, Boolean>>
    /** What is shown, for the pass on open ([SyncPoll.shown]), as it changes. */
    val shown: Flow<SyncPoll.Shown>
    /** The pairing was ended in the browser: said in Settings. */
    fun ended()
    /** Zuhause / favourites came from the browser: read them again (the place menu and the empty search show them). */
    fun placesChanged()
    /** Routes came or changed: My routes read again, the open plan's saved route followed, [toRoute] routed. */
    fun toursChanged(toRoute: List<Long>)
    /** A plan from the browser was routed with newer routing data than the phone's: get these tiles now. */
    fun newerData(tiles: List<String>)
}

/**
 * When the browser sync runs and what follows a pass (code audit 2026-11-12, cleanup piece 4 – out of AppViewModel
 * after the [com.onpath.app.ui.tour.TourController] pattern; the pairing half is [com.onpath.app.ui.PairingFlow]):
 * the triggers while the app is open – a tour ended, about every 15 s on My routes / Browser sync, right away when one
 * of them opens ([start]; the rules are [SyncPoll]) –, pull to refresh with its spinner ([pull], [pulling]), the
 * "New / Changed" marks ([marks], [markSeen]) and a pass's report handed on ([onReport]): the list, kept places,
 * routing of the plans that came. Android-free: [PlanSync] sits behind [SyncEngine], the clock and the waiting come
 * from outside, so the triggers are testable on the JVM (SyncDriverTest).
 */
class SyncDriver(
    private val engine: SyncEngine,
    private val host: SyncHost,
    /** A tour records, as it changes – and right now ([isRecording]). */
    private val recording: Flow<Boolean>,
    private val isRecording: () -> Boolean,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Milliseconds that only go forward (SystemClock.elapsedRealtime). */
    private val clock: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val poll: SyncPoll = SyncPoll(),
) {
    /** Routes from the browser not opened yet (My routes → Planned: a "New" / "Changed" tag – RouteTag); read once at start. */
    val marks: StateFlow<Map<String, SyncMark>> get() = engine.marks

    /** Opened (load as plan / open the track): the route's "New from / Changed in the browser" mark goes. */
    fun markSeen(uuid: String) {
        if (uuid.isEmpty() || uuid !in marks.value) return
        scope.launchSafe("app", "syncMarkSeen", io) { engine.markSeen(uuid) }
    }

    /** Pull to refresh in My routes → Planned runs: the spinner shows until its pass reports ([onReport]). */
    var pulling by mutableStateOf(false)
        private set
    private var pullTimeout: Job? = null

    /**
     * My routes → Planned pulled down (owner 2026-11-13, only offered when paired): a pass now, the spinner until it
     * ends. During a tour or while the server asked for quiet nothing is asked and the spinner stops at once
     * ([SyncPoll.pullStarts]).
     */
    fun pull() {
        if (!SyncPoll.pullStarts(engine.state.value.paired, recording = isRecording(), quiet = engine.quiet())) return
        pulling = true
        poll.requested(clock())
        engine.request("pulled", manual = true)
        // never a spinner that stays: a pass that hangs in the network ends by itself, this is only the net under it
        pullTimeout?.cancel()
        pullTimeout = scope.launchSafe("app", "pullSync timeout") { sleep(PULL_SPIN_MAX_MS); pulling = false }
    }

    /** The sync starts and its triggers watch (once, when the view model is made). */
    fun start() {
        scope.launchSafe("app", "start-up work", io) { engine.loadMarks() } // marks.txt → marks (small file, never throws)
        engine.start()
        // a tour ended: what waited for it comes now
        scope.launchSafe("app", "start-up work") {
            var was = isRecording()
            recording.collect { now -> if (was && !now) engine.request("tour ended"); was = now }
        }
        // on screen, paired, no tour, My routes / Browser sync shown (not the Plan map): a pass about every 15 s (SyncPoll)
        scope.launchSafe("app", "start-up work") {
            combine(host.screen, recording, engine.state) { (visible, routesScreen), rec, st ->
                SyncPoll.wanted(visible, st.paired, recording = rec, routesScreen)
            }.distinctUntilChanged().collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    val wait = poll.waitMs(clock())
                    when {
                        wait > 0 -> sleep(wait)
                        engine.poll("poll") -> poll.requested(clock())
                        else -> sleep(SyncPoll.BUSY_RECHECK_MS) // a pass runs or waits
                    }
                }
            }
        }
        // My routes / its Planned tab / Settings → Browser sync opened: pull right away (owner 2026-10-30), debounced
        scope.launchSafe("app", "start-up work") {
            var before = SyncPoll.Shown.OTHER
            host.shown.collect { now ->
                val why = SyncPoll.opened(before, now)
                before = now
                val t = clock()
                if (why != null && poll.openDue(t, engine.state.value.paired, recording = isRecording(), busy = engine.busy())) {
                    poll.requested(t)
                    engine.request(why)
                }
            }
        }
    }

    /** A sync pass ended (main thread): the poll counts from here, the spinner stops, what came is handed to the view model. */
    fun onReport(r: PlanSync.Report) {
        val res = r.result
        poll.passEnded(clock(), SyncPoll.ok(r))
        if (r.manual) { pulling = false; pullTimeout?.cancel() } // pull to refresh: its pass ended (or was skipped)
        if (r.ended) host.ended()
        // Pair during a tour: only the handshake ran – Browser sync / ☰ show "Routes sync after the tour" (State.afterTour)
        // Zuhause / favourites from the browser: read them again (the place menu and the empty search show them)
        if (res?.placesChanged == true) host.placesChanged()
        if (res?.toursChanged == true) host.toursChanged(res.toRoute)
        // a plan from the browser was routed with newer routing data than the phone's: get it now, not after 35 days
        res?.newerData?.takeIf { it.isNotEmpty() }?.let { host.newerData(it) }
    }

    companion object {
        /** The longest the pull-to-refresh spinner shows without a report. */
        const val PULL_SPIN_MAX_MS = 30_000L
    }
}
