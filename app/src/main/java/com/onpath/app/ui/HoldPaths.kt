package com.onpath.app.ui

import com.onpath.app.debug.Problems
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.RouteCancel
import com.onpath.app.routing.RoutingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The measure of a hold beside a plan on the real paths (owner 2026-11-17 „Always real paths“, F-PLAN-15): the router's
 * lengths for appending and inserting ([PlaceAddMenu.pathDetours]) off the main thread, at most [timeoutMs] in all. A
 * timeout or any failure (no routing data, an engine error, a spot on an island) gives the straight-line numbers back
 * ([Outcome.onPaths] false, [Outcome.failure] says why) – a hold always gets its decision (core feature C1). One measure
 * at a time: [start] cancels the one before, [cancel] stops it (a plan change, Undo, leaving Plan); a cancelled measure
 * never calls its `done`. App only (the browser planner has no main thread to keep free).
 *
 * [scope] = where `done` runs (the ViewModel's: the main thread), [worker] = the thread the router is asked on.
 */
class HoldPaths(
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    /** The routed length a → b in metres (blocking, on [worker]); throws when there is none. Stops soon after [cancel]. */
    fun interface Router {
        fun length(a: GeoPoint, b: GeoPoint, cancel: RouteCancel): Double
    }

    /** What a measure found: [detours] on the paths, or the straight ones after [failure]; [ms] = how long it took. */
    class Outcome(val detours: PlaceAddMenu.Detours, val onPaths: Boolean, val ms: Long, val failure: String? = null) {
        /** For the hold's trace: "paths: insert +X m, append Y m (Z ms)" or "paths failed: … – straight lines". */
        fun describe(): String =
            if (onPaths) "paths: insert +${detours.insertM.toInt()} m" +
                (detours.earlyInsertM?.let { " (earlier legs +${it.toInt()} m)" } ?: "") +
                ", append ${detours.appendM.toInt()} m ($ms ms)"
            else "paths failed: $failure ($ms ms) – straight lines"
    }

    private var job: Job? = null
    private var cancel: RouteCancel? = null
    private var gen = 0

    /** A measure is running (its `done` not called yet). Main thread. */
    var running = false
        private set

    /**
     * Measures [s] beside the plan [points] (2+) on the paths; [straight] = its straight-line numbers ([PlaceAddMenu.detours],
     * the fallback and the arrival angle). [done] gets the outcome on [scope] – never for a measure cancelled or replaced.
     */
    fun start(points: List<GeoPoint>, s: GeoPoint, straight: PlaceAddMenu.Detours, router: Router, done: (Outcome) -> Unit) {
        cancel()
        val g = ++gen
        val c = RouteCancel().also { cancel = it }
        running = true
        val t0 = clock()
        job = scope.launch {
            // the work never throws into this job (a failed child would cancel it): its outcome is a Result
            val work = async(worker) {
                runCatching {
                    PlaceAddMenu.pathDetours(points, s, straight) { a, b ->
                        if (c.isCancelled) throw RoutingException("measure stopped", kind = RoutingException.Kind.CANCELLED)
                        val m = router.length(a, b, c)
                        if (!m.isFinite() || m < 0) throw RoutingException("no length ($m)")
                        m
                    }
                }
            }
            val result = try {
                withTimeoutOrNull(timeoutMs) { work.await() }
            } catch (e: CancellationException) {
                c.cancel(); throw e // cancelled: the router stops, nothing is applied
            }
            val ms = clock() - t0
            val outcome = when {
                result == null -> {
                    c.cancel(); work.cancel() // the router stops; its late result is never read
                    Problems.info("plan", "Hold beside the plan: paths not measured within $timeoutMs ms – straight lines")
                    Outcome(straight, false, ms, "timeout after $timeoutMs ms")
                }
                result.isSuccess -> Outcome(result.getOrThrow(), true, ms)
                else -> {
                    val e = result.exceptionOrNull()
                    // the kind only in the trace; the exception itself goes to the problem log
                    val why = if (e is RoutingException) e.kind.name.lowercase() else "engine error"
                    // expected: no routing data / no way near the spot / an island → info; anything else is a bug → warn
                    if (e is RoutingException) Problems.info("plan", "Hold beside the plan: paths not measured ($why) – straight lines", e)
                    else Problems.warn("plan", "Hold beside the plan: measuring the paths failed – straight lines", e)
                    Outcome(straight, false, ms, why)
                }
            }
            if (g != gen) return@launch // replaced or cancelled meanwhile: never a stale result
            running = false
            job = null; cancel = null
            done(outcome)
        }
    }

    /** Stops a running measure (its `done` is never called); true when one was running. Main thread. */
    fun cancel(): Boolean {
        val was = running
        gen++
        running = false
        cancel?.cancel(); cancel = null
        job?.cancel(); job = null
        return was
    }

    companion object {
        /** The whole measure (owner: ~1.5 s): after this the straight lines decide. */
        const val TIMEOUT_MS = 1_500L
    }
}
