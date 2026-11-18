package com.onpath.app.recording

import com.onpath.strings.*
import org.jetbrains.compose.resources.StringResource

enum class GpsStatus(private val res: StringResource) {
    NO_PERMISSION(Res.string.nav_gps_no_permission),
    OFF(Res.string.nav_gps_off),
    SEARCHING(Res.string.nav_gps_searching),
    WEAK(Res.string.nav_gps_weak),
    GOOD(Res.string.nav_gps_good);

    val label: String get() = Txt.s(res)
}
