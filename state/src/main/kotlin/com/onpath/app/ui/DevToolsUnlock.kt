package com.onpath.app.ui

/**
 * Developer tools in Settings → Diagnostics (owner: one release APK): hidden until the version line in About is tapped
 * [TAPS] times (the Android convention); the unlock is remembered. Debug builds show them always.
 */
object DevToolsUnlock {
    const val TAPS = 7

    fun unlocked(taps: Int): Boolean = taps >= TAPS

    /**
     * Only after the unlock (review Arch H1): the releases testers install are debug builds, so "debug builds always"
     * showed Diagnostics → Developer tools (demo location, test styles) to every user.
     */
    fun shown(unlocked: Boolean): Boolean = unlocked
}
