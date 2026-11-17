package com.onpath.app.testkit

import java.util.concurrent.Executor

/**
 * An executor that runs nothing by itself: what is handed to it waits in order until the test says [runNext] /
 * [runAll]. A state holder's "main thread" and its "routing thread" become two of these, and the test decides the
 * order in which their work happens – a re-route result arriving before or after the app's FOLLOW_CHANGED is a line
 * in the test, not a matter of timing. Everything runs on the calling (test) thread.
 * No coroutine dispatcher is offered: nothing needs one yet, and plain hand-overs keep a test's order readable.
 */
class ManualExecutor(val name: String = "manual") : Executor {
    private val queue = ArrayDeque<Runnable>()

    /** Tasks waiting. */
    val size: Int get() = synchronized(queue) { queue.size }

    override fun execute(command: Runnable) { synchronized(queue) { queue.addLast(command) } }

    /** Runs the oldest waiting task; false = nothing waited. */
    fun runNext(): Boolean {
        val r = synchronized(queue) { queue.removeFirstOrNull() } ?: return false
        r.run()
        return true
    }

    /** Runs the waiting tasks and the ones they add, oldest first, until none is left; returns how many ran. */
    fun runAll(max: Int = 10_000): Int {
        var n = 0
        while (runNext()) check(++n <= max) { "ManualExecutor $name: more than $max tasks – a task that re-posts itself forever?" }
        return n
    }

    /** Drops what waits (a process that died with work queued). */
    fun dropAll(): Int = synchronized(queue) { queue.size.also { queue.clear() } }
}
