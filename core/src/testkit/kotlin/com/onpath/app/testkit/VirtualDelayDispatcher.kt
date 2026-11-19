package com.onpath.app.testkit

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * A coroutine dispatcher with VIRTUAL time for plain JVM tests: nothing waits for real. [post] is where a dispatched block
 * goes (a held executor such as [ManualExecutor]'s `execute`, or `{ it.run() }` for inline running); `delay`,
 * `withTimeout` / `withTimeoutOrNull` and `onTimeout` register timers on [now] that fire only when [advance] reaches them
 * (in time order, then registration order). A timeout is a real [Delay.invokeOnTimeout], so `withTimeoutOrNull(60_000)` times
 * out exactly when the test advances 60 s – not before. A resumed `delay` goes through [post] again, so a held executor
 * runs it when the test lets it.
 */
@OptIn(InternalCoroutinesApi::class)
class VirtualDelayDispatcher(private val post: (Runnable) -> Unit) : CoroutineDispatcher(), Delay {
    /** Virtual milliseconds since the start. */
    var now = 0L
        private set

    private class Timer(val at: Long, val seq: Int, val run: Runnable) { var live = true }
    private val timers = ArrayList<Timer>()
    private var seq = 0

    /** Timers still waiting (a finished or cancelled one is not counted). */
    val pending: Int get() = timers.count { it.live }

    override fun dispatch(context: CoroutineContext, block: Runnable) = post(block)

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val t = Timer(now + timeMillis, seq++) { continuation.resume(Unit) }
        timers += t
        continuation.invokeOnCancellation { t.live = false }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val t = Timer(now + timeMillis, seq++, block)
        timers += t
        return DisposableHandle { t.live = false }
    }

    /** Moves the clock forward by [ms] and fires every timer that falls due, oldest first (a timer fired may register new ones inside the span). */
    fun advance(ms: Long) {
        val to = now + ms
        while (true) {
            val next = timers.filter { it.live }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
            if (next.at > to) break
            next.live = false; timers.remove(next); now = next.at
            next.run.run()
        }
        now = to
        timers.removeAll { !it.live }
    }
}
