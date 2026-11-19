package com.onpath.app.ui

import com.onpath.strings.*
import org.jetbrains.compose.resources.StringResource

/**
 * The topics of Settings (owner: a list of topics, each opens its own page), in list order: "Your settings" first,
 * then the smaller "App" group.
 */
enum class SettingsPage(private val titleRes: StringResource, val app: Boolean = false) {
    PACE(Res.string.settings_page_pace),
    POI(Res.string.settings_page_poi),
    MAP(Res.string.settings_page_map),
    NAVIGATION(Res.string.settings_page_navigation),
    OFFLINE_MAPS(Res.string.settings_page_offline_maps),
    BACKUP(Res.string.settings_page_backup),
    /** Plan sync with the web planner (A-007): shown when the app has the sync (always, not in older screenshots). */
    SYNC(Res.string.settings_page_sync),
    /** Phone setup (location, notifications, vibration, battery …): shown when the app passes the checks. */
    PHONE(Res.string.settings_page_phone, app = true),
    UPDATES(Res.string.settings_page_updates, app = true),
    PRIVACY(Res.string.settings_page_privacy, app = true),
    DIAGNOSTICS(Res.string.settings_page_diagnostics, app = true),
    ABOUT(Res.string.settings_page_about, app = true);

    /** Topic name in the app's language (strings_settings.xml). */
    val title: String get() = Txt.s(titleRes)
}
