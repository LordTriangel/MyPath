package com.onpath.app.ui

import com.onpath.app.debug.launchSafe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext

/**
 * The app's [Background]: a coroutine on the view model's [scope] (failures reported under `app` / [Background.run]'s `what`),
 * the blocking [work] on [io]. A `run` called from inside a `then` is the next step of the same flow – it used to be the next
 * lines of one coroutine, so it starts undispatched (no extra hop through the scope's dispatcher); one called from anywhere
 * else is a new coroutine as before.
 */
class CoroutineBackground(private val scope: CoroutineScope, private val io: CoroutineDispatcher) : Background {
    /** > 0 while a `then` runs (always on the scope's thread). */
    private var inThen = 0

    override fun <T> run(what: String, work: () -> T, always: () -> Unit, then: (T) -> Unit) {
        val start = if (inThen > 0) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT
        scope.launchSafe("app", what, start = start) {
            try {
                val result = withContext(io) { work() }
                inThen++
                try { then(result) } finally { inThen-- }
            } finally { always() }
        }
    }
}
