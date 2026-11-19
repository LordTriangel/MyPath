package com.onpath.app.plansync

/** What Settings → Browser sync shows. */
data class SyncState(
    val paired: Boolean = false,
    /** The browser's name from its hello ("Chrome on Linux"); null = none yet. */
    val peer: String? = null,
    val lastOkMs: Long = 0,
    val running: Boolean = false,
    val problem: SyncProblem? = null,
    /** The six check digits of the pairing ("395 514"). */
    val check: String? = null,
    /** When the pairing was made (ms; 0 = unknown / not paired). */
    val sinceMs: Long = 0,
    /** When the last pass ended in this run of the app, well or not (ms; 0 = none yet). */
    val checkedMs: Long = 0,
    /** The pairing was ended in the browser and Settings → Browser sync was not opened since (☰ shows it amber). */
    val ended: Boolean = false,
    /**
     * Paired: is a browser at the other end ([BrowserLink], from the browser hellos)? [BrowserLink.UNPAIRED] = the
     * browser unpaired itself (☰ "Pair with browser" + grey "Unpaired in the browser"). Not paired: unused.
     */
    val browser: BrowserLink = BrowserLink.CONNECTED,
    /** A tour is recording and the routes wait for its end (grey "Routes sync after the tour"). */
    val afterTour: Boolean = false,
)

/** Why the last pass did not end well. */
sealed interface SyncProblem {
    data object Offline : SyncProblem
    data class Refused(val status: Int, val error: String?) : SyncProblem
    /** Items from a newer app (update OnPath). */
    data class NewerApp(val count: Int) : SyncProblem
    /** Routes from the browser could not be saved on the phone (tried again next time). */
    data class NotSaved(val count: Int) : SyncProblem
    /** The sync files could not be written (storage full). */
    data object Storage : SyncProblem
    /** [count] routes stayed unsent for [StuckOutbox.PASSES] passes in a row (review sync M8: was "synced"). */
    data class Stuck(val count: Int) : SyncProblem
}
