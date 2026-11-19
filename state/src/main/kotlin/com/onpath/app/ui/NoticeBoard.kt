package com.onpath.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onpath.app.debug.Problems

/**
 * The messages the screens show (cut out of `AppViewModel`, rebuild phase 2 piece 1): the red error box, the lines at the
 * top of My routes / Settings, the quiet line on the Plan map, the spinner. [say] decides where a result goes
 * ([NoticeRouting]); what it needs to know of the screens – which one is open – comes as lambdas, read when it is said.
 * Declared in the view model before `routesOpen` / `showSettings`, whose setters clear the lines.
 */
class NoticeBoard(
    private val routesOpen: () -> Boolean,
    private val settingsOpen: () -> Boolean,
    /** The Plan map is what the user looks at (Plan mode, no search over it). */
    private val mapShown: () -> Boolean,
) : Say {
    /** The red error box (MessageCard HINWEIS_ERROR: ⚠, red edge, OK) on the screen shown; null = none. Never cleared by itself. */
    var errorCard by mutableStateOf<String?>(null)
    /** The line at the top of My routes; goes when My routes is left. */
    var routesNote by mutableStateOf<Notice?>(null)
    /** The line at the top of the open Settings page; goes when the page changes or Settings closes. */
    var settingsNote by mutableStateOf<Notice?>(null)

    /** The quiet line on the Plan map ([NoticeSurface.MAP_LINE], goes after [MapNote.MS]); [mapNoteTick] restarts its time. */
    var mapNote by mutableStateOf<Notice?>(null)
    var mapNoteTick by mutableStateOf(0)
        private set

    /** Work the user started that takes a moment: a spinner where it was started. */
    var busy by mutableStateOf<Busy?>(null)

    /**
     * Says [text] where it belongs (owner: no dark message bar): a line in My routes / the open Settings page where the
     * user acted ([place]); an error without such a place in the red [errorCard] box (stays until OK); an INFO without a
     * place only in the problem log. Routing: [NoticeRouting].
     */
    override fun say(text: String, kind: NoticeKind, place: NoticePlace) {
        when (NoticeRouting.surface(kind, place, routesOpen(), settingsOpen(), mapShown = mapShown())) {
            NoticeSurface.ROUTES_LINE -> routesNote = Notice(text, kind)
            NoticeSurface.SETTINGS_LINE -> settingsNote = Notice(text, kind)
            NoticeSurface.MAP_LINE -> { mapNote = Notice(text, kind); mapNoteTick++ }
            NoticeSurface.ERROR_CARD -> errorCard = text
            NoticeSurface.LOG_ONLY -> Problems.trace("ui", "${kind.name} for ${place.name.lowercase()} not shown (place closed): $text")
        }
    }
    fun sayError(text: String, place: NoticePlace) = say(text, NoticeKind.ERROR, place)
    fun sayInfo(text: String, place: NoticePlace) = say(text, NoticeKind.INFO, place)
}
