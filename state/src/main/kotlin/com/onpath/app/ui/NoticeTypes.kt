package com.onpath.app.ui

import com.onpath.strings.*
import org.jetbrains.compose.resources.StringResource

// The pure types of the messages (cut out of Notices.kt, rebuild phase 2 piece 1): what a result is, where it belongs, where
// it shows. The Composables (NoticeLine, BusyContent, ScreenMessageHost …) stay in :app, in Notices.kt.

/**
 * Work the user started that takes a moment: it shows as a spinner where it was started (the
 * button / the My routes row), never as a message.
 */
enum class Busy(private val res: StringResource?, private val english: String = "") {
    IMPORT(Res.string.plan_busy_import),
    EXPORT(Res.string.plan_busy_export),
    /** Diagnostics stays English (owner). */
    DIAGNOSTICS(null, "Creating the report…"),
    BACKUP(Res.string.plan_busy_backup);

    val text: String get() = res?.let { Txt.s(it) } ?: english
}

/** What a result is (vm.say). Questions and must-know boxes are [Message]s (MessageCard); route state has its lines. */
enum class NoticeKind {
    /** The result of something the user did, good to know: a grey line, no box. */
    INFO,
    /** Something the user asked for (or the app needs to do for them) did not work: red, ⚠, what failed + one way out. */
    ERROR,
}

/** Where the user acted – the screen the result belongs to. */
enum class NoticePlace {
    /** My routes (import, export, delete, rides, restore result). */
    ROUTES,
    /** Settings (any page: backup, offline maps, updates, diagnostics, developer tools). */
    SETTINGS,
    /** The map itself (a share / link into the app, a map button, a dialog over the map). */
    MAP,
    /** Nobody acted: the recording service, a backup in the background, a crash last time. */
    BACKGROUND,
}

/** Where a message shows. */
enum class NoticeSurface {
    /** A line at the top of My routes (grey INFO / red ERROR); it goes when My routes is left. */
    ROUTES_LINE,
    /** A line at the top of the open Settings page; it goes when the page changes or Settings closes. */
    SETTINGS_LINE,
    /**
     * A short quiet line on the map (Plan, the paper pill under the activity button – [AppViewModel.mapNote]); it goes by
     * itself after a few seconds ([MapNote.MS]). Only an INFO for the map while the map is what the user looks at.
     */
    MAP_LINE,
    /** The red error box (MessageCard HINWEIS_ERROR, [AppViewModel.errorCard]) – stays until OK. */
    ERROR_CARD,
    /** Nowhere on screen: nobody waits for it (the problem log has it). */
    LOG_ONLY,
}

/** A message for a line in a screen. */
data class Notice(val text: String, val kind: NoticeKind)

/**
 * No dark message bar (owner): a message goes where the user acted; only an error that has no such place left gets the
 * red error box; an INFO without a place is not shown (its result is visible anyway – or nobody needs it).
 */
object NoticeRouting {
    /** [mapShown] = the Plan map is what the user looks at (no My routes, Settings or search over it). */
    fun surface(kind: NoticeKind, place: NoticePlace, routesOpen: Boolean, settingsOpen: Boolean,
                mapShown: Boolean = false): NoticeSurface = when {
        place == NoticePlace.ROUTES && routesOpen -> NoticeSurface.ROUTES_LINE
        place == NoticePlace.SETTINGS && settingsOpen -> NoticeSurface.SETTINGS_LINE
        kind == NoticeKind.ERROR -> NoticeSurface.ERROR_CARD
        place == NoticePlace.MAP && mapShown && !routesOpen && !settingsOpen -> NoticeSurface.MAP_LINE
        else -> NoticeSurface.LOG_ONLY
    }
}
