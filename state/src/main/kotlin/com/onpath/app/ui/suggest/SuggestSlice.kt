package com.onpath.app.ui.suggest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCard
import com.onpath.app.suggest.SuggestCards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The wish as the wish sheet edits it (sketch 5 B): where, the activity (+ T level), time from–to, metres up from–to,
 * the Ziel kinds. The ranges' ends: time [H_MIN]–[H_MAX] h, ascent 0–[UP_MAX] m – an end at the scale's end is open
 * („bis 400 m“, „ab 300 m“, „egal“), as in the planner (web/app/src/suggest.ts).
 */
data class SuggestWish(
    val at: GeoPoint,
    val activity: Activity,
    val sac: Int,
    val hours: ClosedFloatingPointRange<Float>,
    val ascent: ClosedFloatingPointRange<Float>,
    val ziel: Set<String> = emptySet(),
) {
    /** The method's question ([SuggestAsk]): an ascent end at the scale's end is open (null). */
    fun ask(): SuggestAsk {
        val lo = ascent.start.toDouble().takeIf { it > 0.0 }
        val hi = ascent.endInclusive.toDouble().takeIf { it < UP_MAX }
        return SuggestAsk(at.lat, at.lon, activity, sac, doubleArrayOf(hours.start.toDouble(), hours.endInclusive.toDouble()),
            if (lo == null && hi == null) null else arrayOf(lo, hi), ZIEL.filter { it in ziel })
    }

    companion object {
        const val H_MIN = 0.5f
        const val H_MAX = 8f
        const val H_STEP = 0.5f
        const val UP_MAX = 1500f
        const val UP_STEP = 50f
        /** The Ziel chips in their order (the method's names – `Wish.ziel`). */
        val ZIEL = listOf("gipfel", "wasser", "aussicht", "einkehr", "kultur")

        /** Pre-set from the activity (sketch 5: Wandern 2–3 h, ↑ bis 400 m; MTB bis 800, the other bikes bis 600). */
        fun of(at: GeoPoint, activity: Activity, sac: Int): SuggestWish = SuggestWish(at, activity, sac, 2f..3f, 0f..when (activity) {
            Activity.HIKE -> 400f
            Activity.MTB, Activity.EMTB -> 800f
            else -> 600f
        })
    }
}

/** What the suggestion panel in the plan sheet shows. */
enum class SuggestPhase {
    /** The method runs (or the cells' suggest files are fetched): „Runden werden gesucht …“ */
    WAIT,
    /** The routing data in reach loads first. */
    LOADING,
    /** The cards (none found: the notes say why). */
    CARDS,
    /** Nothing could be done: „Gerade keine Vorschläge möglich …“ */
    FAILED,
}

/**
 * What the slice needs from the app (Android, the view model). [AppSuggestHost] is the app's; the tests give a fake.
 */
interface SuggestHost {
    /**
     * One run of the method ([com.onpath.app.suggest.app.SuggestRunner.suggest]); a newer call cancels this one.
     * [lapped] hears each of its phases as it ends (key, seconds; any thread) – the wait window's steps.
     */
    suspend fun suggest(ask: SuggestAsk, lapped: (String, Double) -> Unit): SuggestCards
    /** Starts loading these rd5 parts (RoutingDataLoader.ensure); returns the ones still missing. */
    fun ensureRouting(parts: List<String>): List<String>
    /** Of these rd5 parts the ones not on the phone yet. */
    fun missingRouting(parts: List<String>): List<String>
    /** Fetches the suggest files of these zoom-10 cells (off the main thread); true = at least one arrived. */
    suspend fun fetchCells(cells: List<IntArray>): Boolean
    /** The camera shows [points] (following stops first). */
    fun fit(points: List<GeoPoint>)
    /** The loop as a normal plan (TrackToPlan, a round trip) with this activity; [done] true once the plan replaced the old one. */
    fun take(points: List<GeoPoint>, activity: Activity, sac: Int, done: (Boolean) -> Unit)
}

/**
 * Round-trip suggestions on the phone (docs/design/tour-suggestions.md §7, stage 4 UI – the planner's
 * web/app/src/suggest.ts is the model): „Rundtour von hier“ → the wish sheet ([wishOpen]) → OK → one run per wish
 * (routing data in reach loaded first; the cells' suggest files fetched when missing, then one run again) → the cards
 * in the plan sheet ([phase]) → a tap shows that card's loop on the map ([loop], only that one) → „Übernehmen“ = a normal
 * plan. Holds only light cards ([SuggestCard]: a line, numbers, words), never a run's network or place.
 *
 * On for everyone since 2026-11-18 (owner); the developer switch „Tourvorschläge“ ([on], Settings → Diagnostics →
 * Developer tools) turns it off.
 * Main thread; the work runs in [SuggestHost.suggest] (the runner: a background thread, one run at a time).
 */
class SuggestSlice(private val scope: CoroutineScope, var host: SuggestHost, on: Boolean = true,
                   private val onSwitch: (Boolean) -> Unit = {}) {
    /** The developer switch (on by default): the menus offer „Rundtour von hier“ only while it is on. */
    var on by mutableStateOf(on)
        private set

    /** The wish (the sheet edits it; kept for „Wunsch ändern“ while the cards show). */
    var wish by mutableStateOf<SuggestWish?>(null)
    /** The wish sheet is open. */
    var wishOpen by mutableStateOf(false)
        private set
    /** The panel in the plan sheet; null = none (the plan sheet as usual). */
    var phase by mutableStateOf<SuggestPhase?>(null)
        private set
    var cards by mutableStateOf<List<SuggestCard>>(emptyList())
        private set
    /** The run's notes („Die Karte wird hier gerade aktualisiert …“). */
    var notes by mutableStateOf<List<String>>(emptyList())
        private set
    /** The chosen card (its loop is on the map), -1 = none. */
    var chosen by mutableIntStateOf(-1)
        private set
    /** The chosen card's loop as points – the one line on the map (owner 2026-11-17: no faint others). */
    var loop by mutableStateOf<List<GeoPoint>?>(null)
        private set
    /** „Übernehmen“ is turning the loop into a plan. */
    var taking by mutableStateOf(false)
        private set

    /** The panel or the sheet shows: the plan sheet makes room for it. */
    val active: Boolean get() = phase != null || wishOpen

    /**
     * The search runs (owner 2026-11-19: form A, the wait window over the map with „Abbrechen“): the map takes no tap or
     * hold meanwhile – nothing cancels the search or moves its start but „Abbrechen“.
     */
    val searching: Boolean get() = phase == SuggestPhase.WAIT || phase == SuggestPhase.LOADING

    /** The method's phases that ended in this wish's runs so far (key, seconds), in order – the wait window's steps. */
    val laps: List<Pair<String, Double>> get() = lapLog.toList()
    private val lapLog = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Double>>()

    private var job: Job? = null

    fun switch(value: Boolean) {
        on = value
        onSwitch(value)
        if (!value) close()
        Problems.info("suggest", "Tour suggestions (test) ${if (value) "on" else "off"}")
    }

    /**
     * „Rundtour von hier“ at [at]: the wish sheet, pre-set from the plan's activity – or the last wish at the same spot.
     * Not while a search runs: its start stays (only „Abbrechen“ ends it).
     */
    fun open(at: GeoPoint, activity: Activity, sac: Int) {
        if (searching) { Problems.trace("suggest", "Round trip from here ignored: a search runs"); return }
        val last = wish
        wish = if (last != null && last.at == at) last else SuggestWish.of(at, activity, sac)
        wishOpen = true
        Problems.trace("suggest", "Wish sheet opened")
    }

    /** „Wunsch ändern“: the sheet again with the last wish (the cards stay under it until OK). */
    fun change() { if (wish != null) wishOpen = true }

    /** Abbrechen in the sheet: closes it; cards shown before stay. */
    fun cancelWish() {
        wishOpen = false
        if (phase == null) wish = null
    }

    /** OK in the sheet: one run for this wish (a running one stops). */
    fun ok() {
        val w = wish ?: return
        wishOpen = false
        run(w)
    }

    /** A tap on a card: its loop on the map, the camera fits it. */
    fun choose(i: Int) {
        val c = cards.getOrNull(i) ?: return
        chosen = i
        val pts = c.points()
        loop = pts
        host.fit(pts)
    }

    /** The camera fits the chosen loop again (the panel's height settled: the room above it is known only then). */
    fun refit() { loop?.let(host::fit) }

    /** „Übernehmen“: the chosen loop as a normal plan; the suggestions close once the plan is there. */
    fun take(i: Int) {
        val c = cards.getOrNull(i) ?: return
        val w = wish ?: return
        if (taking) return
        taking = true
        val mine = ++takeNo
        Problems.trace("suggest", "Card ${i + 1} taken: ${c.km} km")
        host.take(c.points(), w.activity, w.sac) { ok ->
            // closed meanwhile (✕ / Back, maybe a new search since): this answer is not for what shows now
            if (mine != takeNo) return@take
            taking = false
            if (ok) close()
        }
    }
    /** Counts every „Übernehmen“ and every [close]: a take's late answer knows whether it is still the one waited for. */
    private var takeNo = 0

    /** ✕ / Back / a new plan: everything goes, a running search stops. */
    fun close() {
        job?.cancel(); job = null
        takeNo++
        phase = null; wishOpen = false; taking = false
        cards = emptyList(); notes = emptyList(); chosen = -1; loop = null
        lapLog.clear()
    }

    /**
     * „Abbrechen“ in the wait window: the search stops (its coroutine is cancelled – the method ends at its next phase,
     * the result is dropped) and the map is as before; the wish stays for the next „Rundtour von hier“ at the same spot.
     */
    fun cancel() {
        if (!searching) return
        Problems.trace("suggest", "Search cancelled (${phase?.name?.lowercase()}, ${lapLog.size} phases done)")
        close()
    }

    private fun run(w: SuggestWish) {
        job?.cancel()
        phase = SuggestPhase.WAIT
        cards = emptyList(); notes = emptyList(); chosen = -1; loop = null
        lapLog.clear()
        val ask = w.ask()
        job = scope.launchSafe("suggest", "Tour suggestions run") {
            val r = try {
                answer(ask)
            } catch (e: CancellationException) {
                throw e // a newer wish or ✕
            } catch (e: Exception) {
                // robust first: a failed run is a line in the panel, never a crash
                Problems.error("suggest", "Tour suggestions failed", e)
                null
            }
            if (r == null || r.routingToLoad.isNotEmpty()) { phase = SuggestPhase.FAILED; return@launchSafe }
            cards = r.cards
            notes = r.notes
            phase = SuggestPhase.CARDS
            Problems.trace("suggest", "${r.cards.size} cards shown" + if (r.mapUpdating) " (map being updated here)" else "")
            if (r.cards.isNotEmpty()) choose(0)
        }
    }

    /** One wish: routing data first (wait for it), then the run; missing suggest files fetched and the run once again. */
    private suspend fun answer(ask: SuggestAsk): SuggestCards {
        val me = currentCoroutineContext()[Job]
        // a cancelled run may still end a phase on the runner's thread: only this search's laps count
        val lapped: (String, Double) -> Unit = { k, sec -> if (me?.isActive != false) lapLog.add(k to sec) }
        var r = host.suggest(ask, lapped)
        if (r.routingToLoad.isNotEmpty()) {
            phase = SuggestPhase.LOADING
            host.ensureRouting(r.routingToLoad)
            var waited = 0L
            while (host.missingRouting(r.routingToLoad).isNotEmpty()) {
                if (waited >= ROUTING_WAIT_MS) {
                    Problems.warn("suggest", "Routing data for the suggestions did not arrive in ${ROUTING_WAIT_MS / 1000} s")
                    return r
                }
                delay(POLL_MS); waited += POLL_MS
            }
            phase = SuggestPhase.WAIT
            r = host.suggest(ask, lapped)
        }
        if (r.cellsToFetch.isNotEmpty() && host.fetchCells(r.cellsToFetch)) r = host.suggest(ask, lapped)
        return r
    }

    companion object {
        /** How often the wait for routing data looks whether it arrived. */
        const val POLL_MS = 1_000L
        /** The longest wait for routing data before the panel says it failed (the download goes on). */
        const val ROUTING_WAIT_MS = 180_000L
    }
}
