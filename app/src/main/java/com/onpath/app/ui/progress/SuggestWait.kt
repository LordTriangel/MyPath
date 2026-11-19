package com.onpath.app.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onpath.app.data.ProgressMemory
import com.onpath.app.debug.Problems
import com.onpath.app.domain.LoadStep
import com.onpath.app.domain.OneBar
import com.onpath.app.domain.StepShares
import com.onpath.app.domain.TimeLeft
import com.onpath.app.domain.WaitStep
import com.onpath.app.domain.WaitSteps
import com.onpath.app.ui.suggest.SuggestPhase
import com.onpath.app.ui.suggest.SuggestSlice
import com.onpath.strings.*

/** The round-trip search's wait window: the ONE bar, its three steps, the time left – or why it waits (routing data). */
data class SuggestWaitUi(val percent: Int, val steps: List<WaitStep>, val secondsLeft: Int?, val late: Boolean = false,
                         val loadingRouting: Boolean = false) {
    val window: WaitWindowUi get() = WaitWindowUi(WaitSign.ROUTE, Txt.s(Res.string.wait_title_suggest), percent, steps, when {
        loadingRouting -> Txt.s(Res.string.suggest_ui_loading_routing)
        late -> TimeLeft.LATE
        else -> secondsLeft?.let(TimeLeft::text)
    })
}

/**
 * The round-trip search is form A (owner 2026-11-19: „könnte auch als Overlay kommen mit der Option Abbrechen“): the
 * method's real phases as three steps – read the paths (network + facts), find loops (place + loops), rate (judging) –
 * each with its learned share of time ([ProgressMemory.suggestShares], in a [StepShares]' three places: read = prepare,
 * find = download, rate = join); a phase that ended inside a step moves the bar on within it. The ONE bar never goes
 * back and never stands ([OneBar]); a second run (after routing data or fresh suggest files) adds its phases after the
 * first's – the steps never go back either, the bar creeps on in the last step. Loading routing data first is step 1
 * with its own line. Pure (time passed in), unit-tested.
 */
class SuggestWait(private val memory: ProgressMemory) {
    private var bar = OneBar()
    private var startMs = -1L
    private var stepAt = -1
    private var stepFromMs = -1L

    /**
     * [searching]: the search is on ([SuggestSlice.searching]); [loading]: routing data loads first; [laps]: the method's
     * phases that ended so far ([SuggestSlice.laps]). [cards]: the search ended with its cards (its times teach the shares).
     */
    fun update(searching: Boolean, loading: Boolean, laps: List<Pair<String, Double>>, now: Long, cards: Boolean = false): SuggestWaitUi? {
        if (!searching) { finish(laps, cards); return null }
        if (startMs < 0) startMs = now
        val shares = memory.suggestShares
        val n = laps.size
        val step = stepOf(n)
        if (step != stepAt) { stepAt = step; stepFromMs = now }
        val before = (0 until step).sumOf { share(shares, it) }
        val mine = share(shares, step)
        val raw = before + mine * innerOf(n)
        // the step's own time left: the steps so far at their pace for this step's share (unknown in the first)
        val gone = now - startMs
        val stepLeftMs = if (step == 0 || before <= 0.0) null else {
            val expected = (stepFromMs - startMs).coerceAtLeast(0) * mine / before
            maxOf(expected - (now - stepFromMs), expected / 2).toLong()
        }
        val pct = bar.next(raw, before + mine, stepLeftMs, now, stepStart = before)
        val rows = listOf(Res.string.wait_step_suggest_read, Res.string.wait_step_suggest_search, Res.string.wait_step_suggest_judge)
            .map { Txt.s(it) to null }
        return SuggestWaitUi(pct, WaitSteps.of(rows, step), TimeLeft.seconds(bar.share, gone, null), bar.late, loading)
    }

    /** The search ended: a run that came to its cards teaches the steps' shares by its phases' times; then afresh. */
    private fun finish(laps: List<Pair<String, Double>>, cards: Boolean) {
        if (startMs < 0) return
        if (cards && laps.size >= PHASES) try {
            val t = laps.take(PHASES).toMap()
            fun ms(vararg k: String) = (k.sumOf { t[it] ?: 0.0 } * 1000).toLong()
            memory.suggestShares = memory.suggestShares.learn(mapOf(LoadStep.PREPARE to ms("network", "facts"),
                LoadStep.DOWNLOAD to ms("place", "loops"), LoadStep.JOIN to ms("judging")))
        } catch (e: Exception) { Problems.warn("suggest", "Round-trip wait: the steps' shares not learned", e) }
        bar = OneBar(); startMs = -1L; stepAt = -1; stepFromMs = -1L
    }

    companion object {
        /** The method's phases in one run (TourSuggester: network, facts, place, loops, judging). */
        const val PHASES = 5

        /** The step running after [laps] ended phases: read (0–1), find (2–3), rate (4 and a second run). */
        fun stepOf(laps: Int): Int = when {
            laps < 2 -> 0
            laps < 4 -> 1
            else -> 2
        }

        /** How far into its step the bar is after [laps] phases (network ≈ half the reading, place ≈ a third of finding). */
        fun innerOf(laps: Int): Double = when (laps) {
            1 -> 0.55
            3 -> 0.33
            else -> 0.0
        }

        private fun share(s: StepShares, step: Int) = s.of(LoadStep.entries[step])
    }
}

/**
 * The round-trip search's wait window while it runs ([SuggestSlice.searching]): over the map (a Dialog – the map takes no
 * tap or hold meanwhile), „Abbrechen“ the one way out ([SuggestSlice.cancel]). Ticks every half second.
 */
@Composable
fun SuggestWaitWindow(memory: ProgressMemory, s: SuggestSlice) {
    val wait = remember(memory) { SuggestWait(memory) }
    var ui by remember { mutableStateOf<SuggestWaitUi?>(null) }
    val searching = s.searching
    LaunchedEffect(searching) {
        while (true) {
            ui = wait.update(s.searching, s.phase == SuggestPhase.LOADING, s.laps, android.os.SystemClock.uptimeMillis(),
                cards = s.phase == SuggestPhase.CARDS)
            if (!s.searching) break
            kotlinx.coroutines.delay(TICK_MS)
        }
    }
    val shown = ui ?: return
    if (!searching) return
    WaitWindowDialog(shown.window, listOf(WaitButton(str(Res.string.common_cancel), s::cancel)))
}

/** How often the window moves (its bar creeps smoothly between the phases). */
private const val TICK_MS = 500L
