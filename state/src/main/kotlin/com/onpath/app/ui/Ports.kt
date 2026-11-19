package com.onpath.app.ui

// The small interfaces shared by the slices (docs/design/umbau-phase-2.md §3.3): what a slice asks of the rest of the app.
// A slice never names a neighbour that is not a slice yet – it asks through a port the view model implements.

/**
 * Says [text] where it belongs (owner: no dark message bar): a line in My routes / the open Settings page where the user
 * acted ([place]); an error without such a place in the red error box; an INFO without a place only in the problem log.
 * Routing: [NoticeRouting]. Implemented by [NoticeBoard].
 */
fun interface Say {
    fun say(text: String, kind: NoticeKind, place: NoticePlace)
}

/**
 * The map's camera, as the slices ask for it (defined here, used from piece 3 on): the view model turns each call into the
 * matching UiEvent (Fit / CenterOn / PanTo) of :app.
 */
interface Camera {
    /** Show all [points] (north up or at the current bearing by the session's one orientation). */
    fun fit(points: List<com.onpath.app.routing.GeoPoint>)
    /** Fly to one place at [zoom]. */
    fun centerOn(point: com.onpath.app.routing.GeoPoint, zoom: Double)
    /** Move the map to [point] at once, same zoom. */
    fun panTo(point: com.onpath.app.routing.GeoPoint)
}

/** Where the phone is now (null = no fix); the slices ask it, the view model answers from the location tracker. */
fun interface Position {
    fun here(): com.onpath.app.routing.GeoPoint?
}

/** The two clocks the slices read: wall time (stored stamps) and elapsed real time (waits, never goes back). */
interface Clock {
    fun nowMs(): Long
    fun elapsedMs(): Long
}
