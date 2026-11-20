package com.onpath.app.live

/**
 * What the app may show of the live sharing (docs/design/live-tracking.md §2) – published by the live lane after every
 * action, read through [LiveShareClient.live]. A value, so the screen never touches the
 * uploader (which belongs to the lane's one thread).
 */
data class LiveSnapshot(
    /** A tour is shared right now (the sign on Navigate). */
    val sharing: Boolean = false,
    /** Sharing, but the uploads do not get through at the moment – the way is kept and goes up later. */
    val waiting: Boolean = false,
    /** The link to send (again); null when nothing is shared. */
    val link: String? = null,
    /** The tour that is shared; null when none. */
    val tourId: Long? = null,
    /** When the last upload went through (wall clock, ms); null = none yet. */
    val lastOkMs: Long? = null,
    /** The server ended the sharing (expired, deleted elsewhere) – the app says so once. */
    val endedByServer: Boolean = false,
) {
    companion object {
        val OFF = LiveSnapshot()
    }
}
