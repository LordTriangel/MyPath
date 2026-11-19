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
