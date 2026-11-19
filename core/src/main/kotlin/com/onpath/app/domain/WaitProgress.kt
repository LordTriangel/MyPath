package com.onpath.app.domain

import com.onpath.app.cells.CellState
import com.onpath.strings.*
import kotlin.math.exp

/*
 * One progress design, two forms (owner 2026-11-19: „A, aber nie mehr als 3 Häkchen; B: einfach nur Prozent, muss aber
 * passen für die gesamte Aktion“). The pure math of both, Android-free (the browser planner compiles it too):
 *  - A, the wait window: ONE bar 0–100 over all steps ([OneBar]) – each step owns its share of time ([StepShares],
 *    learned on this phone), never back, never standing –, at most 3 step rows ([WaitSteps]), the time left ([TimeLeft]).
 *  - B, the sign beside: ONE percent over the whole action ([ActionPercent]: every load of it together), never back.
 */

/** The steps of a map load, in their order. */
enum class LoadStep { PREPARE, DOWNLOAD, JOIN }

/**
 * The share of time each step of a map load takes (sum 1). Start values prepare 30 / download 50 / join 20; then learned
 * from the loads on this phone ([learn]), kept inside [MIN]..[MAX] so one odd load never takes a step's room away.
 */
data class StepShares(val prepare: Double, val download: Double, val join: Double) {
    fun of(step: LoadStep): Double = when (step) {
        LoadStep.PREPARE -> prepare
        LoadStep.DOWNLOAD -> download
        LoadStep.JOIN -> join
    }

    /** The share of every step before [step] (where its part of the bar begins). */
    fun before(step: LoadStep): Double = LoadStep.entries.takeWhile { it != step }.sumOf { of(it) }

    /**
     * The shares after a load whose steps took [ms] (only the steps that really ran – a step skipped, e.g. the server
     * had the map ready, says nothing about its time): the measured split of the steps that ran, inside the share
     * they had together, blended in by [WEIGHT]. Bounded, sums to 1.
     */
    fun learn(ms: Map<LoadStep, Long>): StepShares {
        val ran = ms.filter { (_, v) -> v > 0 }
        val sum = ran.values.sum()
        if (ran.size < 2 || sum < MIN_LEARN_MS) return this
        val room = ran.keys.sumOf { of(it) }
        fun next(s: LoadStep) = ran[s]?.let { of(s) * (1 - WEIGHT) + room * it / sum * WEIGHT } ?: of(s)
        return bounded(next(LoadStep.PREPARE), next(LoadStep.DOWNLOAD), next(LoadStep.JOIN))
    }

    /** "v1:0.300,0.500,0.200" for storage ([decode]). */
    fun encode(): String = "v1:" + listOf(prepare, download, join).joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) }

    companion object {
        val DEFAULT = StepShares(0.30, 0.50, 0.20)
        const val MIN = 0.05
        const val MAX = 0.80
        /** How much one load moves the learned shares. */
        const val WEIGHT = 0.3
        /** A load faster than this teaches nothing (all from caches). */
        const val MIN_LEARN_MS = 2_000L

        /**
         * Made to sum to 1 with each share inside [MIN]..[MAX]: the farthest out is set to its bound, the rest share
         * what is left in their proportion – again until all fit (the defaults when something is not a number).
         */
        fun bounded(p: Double, d: Double, j: Double): StepShares {
            if (listOf(p, d, j).any { it.isNaN() || it.isInfinite() }) return DEFAULT
            val v = doubleArrayOf(p, d, j).map { it.coerceAtLeast(0.0) }.toDoubleArray()
            if (v.sum() <= 0.0) return DEFAULT
            val fixed = BooleanArray(3)
            repeat(3) {
                val free = v.indices.filter { !fixed[it] }
                val room = 1.0 - v.indices.filter { fixed[it] }.sumOf { v[it] }
                val sum = free.sumOf { v[it] }
                for (i in free) v[i] = if (sum > 0) v[i] / sum * room else room / free.size
                val out = free.maxByOrNull { i -> maxOf(MIN - v[i], v[i] - MAX) } ?: return@repeat
                if (v[out] in MIN..MAX) return StepShares(v[0], v[1], v[2])
                v[out] = v[out].coerceIn(MIN, MAX); fixed[out] = true
            }
            return StepShares(v[0], v[1], v[2])
        }

        /** What [encode] wrote; [DEFAULT] for anything else (missing, damaged, another version) – never throws. */
        fun decode(s: String?): StepShares {
            val v = s?.takeIf { it.startsWith("v1:") }?.removePrefix("v1:")?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }
            if (v == null || v.size != 3) return DEFAULT
            return bounded(v[0], v[1], v[2])
        }
    }
}

/**
 * The route's wait (form A, owner 2026-11-19 „Route berechnet ist auch A“): the legs and „prüfen“ as the steps – each leg
 * the same share, the check its learned share ([check], start [DEFAULT_CHECK], kept inside [MIN_CHECK]..[MAX_CHECK]).
 */
object RouteShares {
    const val DEFAULT_CHECK = 0.10
    const val MIN_CHECK = 0.03
    const val MAX_CHECK = 0.40

    /** The check's share after a run whose legs took [legsMs] and the check [checkMs]. */
    fun learn(check: Double, legsMs: Long, checkMs: Long): Double {
        if (legsMs + checkMs < StepShares.MIN_LEARN_MS || checkMs <= 0) return bounded(check)
        return bounded(check * (1 - StepShares.WEIGHT) + checkMs.toDouble() / (legsMs + checkMs) * StepShares.WEIGHT)
    }

    fun bounded(check: Double): Double = if (check.isNaN()) DEFAULT_CHECK else check.coerceIn(MIN_CHECK, MAX_CHECK)

    /** "r1:0.1000" for storage; [decode] gives [DEFAULT_CHECK] for anything else. */
    fun encode(check: Double): String = "r1:" + "%.4f".format(java.util.Locale.ROOT, bounded(check))
    fun decode(s: String?): Double = s?.takeIf { it.startsWith("r1:") }?.removePrefix("r1:")?.toDoubleOrNull()?.let(::bounded) ?: DEFAULT_CHECK

    /**
     * The route's share 0..1 from [done] legs of [total] (only the legs the router works on), the running leg's search
     * [leg] 0..1 and whether the check runs ([checking]: every leg is routed, the result is worked out).
     */
    fun fraction(done: Int, total: Int, leg: Double?, checking: Boolean, check: Double): Double {
        val legs = 1 - bounded(check)
        if (checking || (total > 0 && done >= total)) return legs
        if (total <= 0) return 0.0
        return legs * (done.coerceIn(0, total) + (leg ?: 0.0).coerceIn(0.0, 1.0)) / total
    }
}

/** Where a step row of the wait window stands. */
enum class WaitStepState { DONE, NOW, LATER }

/**
 * One row under the bar: "✓ vorbereitet", "● lädt Karte + Routendaten", "○ einfügen" – or one leg of a route. [short]:
 * the label at a large font when [label] would not fit one line ("lädt" for "lädt Karte + Routendaten").
 */
data class WaitStep(val label: String, val state: WaitStepState, val short: String? = null)

/** Never more than 3 rows (owner 2026-11-19, „nie mehr als 3 Häkchen“). Pure. */
object WaitSteps {
    const val MAX = 3

    /** With more steps (a route's legs + the check) a window of 3 moves along: the last done, the running one, the next. */
    fun shown(steps: List<WaitStep>): List<WaitStep> {
        if (steps.size <= MAX) return steps
        val now = steps.indexOfFirst { it.state != WaitStepState.DONE }.let { if (it < 0) steps.lastIndex else it }
        val from = (now - 1).coerceIn(0, steps.size - MAX)
        return steps.subList(from, from + MAX)
    }

    /** The rows of [labels] with the one at [now] running (those before done, after later). */
    fun of(labels: List<Pair<String, String?>>, now: Int): List<WaitStep> = labels.mapIndexed { i, (l, s) ->
        WaitStep(l, when { i < now -> WaitStepState.DONE; i == now -> WaitStepState.NOW; else -> WaitStepState.LATER }, s)
    }
}

/**
 * The ONE bar of the wait window (owner 2026-11-18/19): 0–100 over all steps, never back, never standing. Fed about
 * every second with what is measured – [raw] 0..1 over the whole wait (each step its share × its own progress) – and
 * where the running step's part of the bar ends ([stepEnd], with what later steps already did). While the measurement
 * moves, the bar follows it. Between two measurements (a step that reports seldom or not at all) it creeps on by time
 * towards the running step's end: [stepLeftMs] (the step's own time left, when known) sets the pace, else
 * [DEFAULT_TAU_MS]. A step that takes much longer than its share (the number would stand at its end) borrows from the
 * rest of the bar, slowly ([STALL_MS] after the number last moved, at a pace that slows the longer the wait is) – never
 * to 100 before everything is there. One per wait; not thread-safe.
 */
class OneBar {
    private var shown = 0.0
    private var lastMs = -1L
    private var firstMs = -1L
    private var lastPct = -1
    private var pctAt = 0L

    /**
     * The bar 0..100 at [nowMs] (any steady clock). [finished]: everything is there – 100. [paused]: the wait stands
     * (offline, waiting for Wi-Fi, failed) – the bar stands too and says so in grey; no creep.
     */
    fun next(raw: Double, stepEnd: Double, stepLeftMs: Long?, nowMs: Long, finished: Boolean = false, paused: Boolean = false): Int {
        if (finished) { shown = 1.0; lastMs = nowMs; return 100 }
        if (firstMs < 0) { firstMs = nowMs; pctAt = nowMs }
        val cap = (stepEnd.coerceIn(0.0, 1.0) - MARGIN).coerceIn(0.0, END)
        val dt = if (lastMs < 0 || paused) 0L else (nowMs - lastMs).coerceAtLeast(0L)
        lastMs = nowMs
        // the measurement, inside the running step's part
        var next = maxOf(shown, raw.coerceIn(0.0, 1.0).coerceAtMost(cap))
        // the number stood for a while: it creeps on towards the step's end; the step overran its share (no room left
        // there): it borrows from the rest of the bar, slower and slower
        if (dt > 0 && (next * 100).toInt() <= lastPct && nowMs - pctAt >= STALL_MS) {
            val tau = (stepLeftMs ?: DEFAULT_TAU_MS).coerceIn(MIN_TAU_MS, MAX_TAU_MS).toDouble()
            if (next < cap) next += (cap - next) * (1 - exp(-dt / tau))
            if ((next * 100).toInt() <= lastPct) {
                val slow = maxOf(DEFAULT_TAU_MS, 2 * (nowMs - firstMs)).toDouble()
                next += (END - next) * (1 - exp(-dt / slow))
            }
        }
        shown = next.coerceAtMost(END)
        val pct = (shown * 100).toInt().coerceIn(0, 99)
        if (pct != lastPct) { lastPct = pct; pctAt = nowMs }
        return pct
    }

    /** The bar's share now, 0..1. */
    val share: Double get() = shown

    companion object {
        /** The bar stops this far before the running step's end until the step is really done. */
        const val MARGIN = 0.01
        /** Never further before everything is there. */
        const val END = 0.995
        /** The pace of the creep when the step's time left is unknown: 63 % of the way left in this time. */
        const val DEFAULT_TAU_MS = 30_000L
        const val MIN_TAU_MS = 3_000L
        const val MAX_TAU_MS = 30 * 60_000L
        /** The number stood this long: it borrows from the rest of the bar. */
        const val STALL_MS = 2_000L
    }
}

/** "noch etwa 20 Sekunden" (owner 2026-11-19) – as soon as it can be estimated. Pure. */
object TimeLeft {
    /** Not before this much of the wait is behind (a guess from 1 % would jump about). */
    const val MIN_SHARE = 0.03
    const val MIN_ELAPSED_MS = 3_000L

    /**
     * Seconds left for a wait at [share] 0..1 after [elapsedMs]: the pace so far for the rest; never less than the
     * running step's own measured time left [stepLeftS] (the server's estimate, the bytes at the measured rate). Null =
     * not estimable yet.
     */
    fun seconds(share: Double, elapsedMs: Long, stepLeftS: Int? = null): Int? {
        if (share >= 1.0) return 0
        val pace = if (share >= MIN_SHARE && elapsedMs >= MIN_ELAPSED_MS) (elapsedMs / 1000.0 * (1 - share) / share).toInt() else null
        return listOfNotNull(pace, stepLeftS?.coerceAtLeast(0)).maxOrNull()
    }

    /** "noch etwa 20 Sekunden" / "noch etwa 1 Minute" / "noch etwa 3 Minuten": seconds in fives, minutes rounded. */
    fun text(s: Int): String = when {
        s < 55 -> Txt.s(Res.string.wait_left_seconds, ((s + 4) / 5 * 5).coerceAtLeast(5))
        s < 90 -> Txt.s(Res.string.wait_left_minute)
        else -> Txt.s(Res.string.wait_left_minutes, (s + 30) / 60)
    }
}

/**
 * Where one map area stands, 0..1 over its own steps ([StepShares]): preparing = the server's own progress inside the
 * prepare share, downloading = its bytes inside the download share, joining = [joined] (the pieces that are in, 0..1)
 * inside the join share. On the phone = 1. Pure.
 */
object CellProgress {
    fun fraction(s: CellState, shares: StepShares, joined: Double = 0.0): Double = when (s) {
        CellState.None, CellState.Requested, is CellState.Queued, is CellState.Offline, is CellState.Failed -> 0.0
        is CellState.Building -> shares.prepare * s.progress.coerceIn(0.0, 1.0)
        is CellState.WaitingForWifi -> shares.prepare
        is CellState.Downloading -> shares.prepare + shares.download *
            (if (s.totalBytes > 0) (s.doneBytes.toDouble() / s.totalBytes).coerceIn(0.0, 1.0) else 0.0)
        CellState.Installing -> shares.prepare + shares.download + shares.join * joined.coerceIn(0.0, 1.0)
        CellState.Downloaded, is CellState.Ready -> 1.0
    }

    /** The step [s] is in (null = on the phone, or not loading). */
    fun step(s: CellState): LoadStep? = when (s) {
        CellState.None, CellState.Requested, is CellState.Queued, is CellState.Building, is CellState.Offline,
        is CellState.Failed -> LoadStep.PREPARE
        is CellState.WaitingForWifi, is CellState.Downloading -> LoadStep.DOWNLOAD
        CellState.Installing -> LoadStep.JOIN
        CellState.Downloaded, is CellState.Ready -> null
    }
}

/**
 * Form B's ONE number (owner 2026-11-19, „einfach nur Prozent, muss aber passen für die gesamte Aktion“): every load of
 * the action together – each item weighted by its size, an item that leaves counts as done – 0..100, never back. The
 * action ends when nothing loads ([update] with no items). Thread-safe (the sign and the notification share one).
 */
class ActionPercent {
    /** One load: [key] (stable), its weight (bytes; 0 = unknown, [UNKNOWN_WEIGHT]) and where it stands 0..1. */
    data class Item(val key: String, val weight: Long, val fraction: Double)

    private val items = LinkedHashMap<String, DoubleArray>() // key → [weight, fraction]
    private var held = 0

    /** The action's percent now; null when nothing loads (the action is over and forgotten). */
    @Synchronized
    fun update(active: List<Item>): Int? {
        if (active.isEmpty()) { items.clear(); held = 0; return null }
        val now = active.associateBy { it.key }
        for ((k, v) in items) if (k !in now) v[1] = 1.0
        for (i in active) {
            val v = items.getOrPut(i.key) { DoubleArray(2) }
            v[0] = (if (i.weight > 0) i.weight else UNKNOWN_WEIGHT).toDouble()
            v[1] = maxOf(v[1], i.fraction.coerceIn(0.0, 1.0))
        }
        val total = items.values.sumOf { it[0] }
        val pct = if (total <= 0) 0 else (items.values.sumOf { it[0] * it[1] } / total * 100).toInt().coerceIn(0, 99)
        held = maxOf(held, pct)
        return held
    }

    companion object {
        /** An item of unknown size (the server has not said it yet). */
        const val UNKNOWN_WEIGHT = 10L * 1_048_576
    }
}

/** When each step of one wait began – the measured times [StepShares.learn] learns from. Pure (time passed in). */
class StepClock {
    private val began = LinkedHashMap<LoadStep, Long>()
    private var startMs = -1L

    /** [step] runs at [nowMs]; the first call starts the clock. */
    fun at(step: LoadStep, nowMs: Long) {
        if (startMs < 0) startMs = nowMs
        if (step !in began && began.keys.none { it.ordinal > step.ordinal }) began[step] = nowMs
    }

    /** A step ran (the wait was real). */
    val started: Boolean get() = startMs >= 0

    /** Milliseconds since the first [at]; 0 before. */
    fun elapsed(nowMs: Long): Long = if (startMs < 0) 0 else nowMs - startMs

    /** How long each step ran until [endMs] (a step that never ran is missing). */
    fun durations(endMs: Long): Map<LoadStep, Long> {
        val e = began.entries.toList()
        return e.mapIndexed { i, (s, t) -> s to ((if (i + 1 < e.size) e[i + 1].value else endMs) - t).coerceAtLeast(0) }.toMap()
    }
}
