package com.onpath.app.suggest.app

import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCards
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs [SuggestService] off the main thread for the screen: `suspend fun suggest(ask)` from the view model's scope.
 *
 *  - on [dispatcher] (default: [Dispatchers.Default] – CPU work, as the app's other heavy pure computations);
 *  - one run at a time ([Mutex]): two runs side by side would hold two networks (a 3 h bike wish ~45 MB each);
 *  - a newer wish cancels the running one: the old call ends with a CancellationException at the method's next phase
 *    (TourSuggester's `check`), then the new one starts. Cancelling the caller's coroutine stops the run the same way.
 *    "Cancels" means the caller's Job: call it from its own `launch { … }` per wish (the view model's usual pattern),
 *    never from a long-lived coroutine that must survive a newer wish.
 */
class SuggestRunner(
    /** one blocking run: the wish, the check point to call between phases and the phases' ends ([SuggestService.suggest]) */
    private val run: (SuggestAsk, () -> Unit, (String, Double) -> Unit) -> SuggestCards,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(service: SuggestService, dispatcher: CoroutineDispatcher = Dispatchers.Default) :
        this({ ask, check, lapped -> service.suggest(ask, check, lapped) }, dispatcher)

    private val one = Mutex()
    private var latest: Job? = null

    /** [lapped]: each phase of the method as it ends (key, seconds), on the runner's thread. */
    suspend fun suggest(ask: SuggestAsk, lapped: (String, Double) -> Unit = { _, _ -> }): SuggestCards {
        val me = currentCoroutineContext()[Job]
        val before = synchronized(this) { latest.also { latest = me } }
        if (before !== me) before?.cancel()
        try {
            return one.withLock {
                withContext(dispatcher) {
                    val ctx = coroutineContext
                    run(ask, { ctx.ensureActive() }, lapped)
                }
            }
        } finally {
            synchronized(this) { if (latest === me) latest = null }
        }
    }
}
