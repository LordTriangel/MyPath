package com.onpath.app.ui

/**
 * Where a box sits (one rule for every screen): 12 dp under the top element, below the status bar. Plan: under the
 * search pill + ☰ row (the plan sheet is drawn over the box, so it never covers the primary button); Navigate: under the
 * turn card / status column (measured) – the re-route question too; My routes: under the title + tabs, over the list;
 * Settings: under the title row. Pure, unit-tested.
 */
enum class MessageScreen { PLAN, NAVIGATE, ROUTES, SETTINGS }

object MessagePlacement {
    /** The gap between the top element and the box (and the box's side margin). */
    const val GAP_DP = 12
    /** Top of the small download chip's touch area below the status bar (right under ☰). */
    const val CHIP_TOP_DP = 68
    /** My routes: title row (4 + 56 + 4 dp) + Planned / Done tabs (48 dp). */
    const val ROUTES_HEADER_DP = 112
    /** Settings: the title row (4 + 56 + 4 dp). */
    const val SETTINGS_HEADER_DP = 64

    /** Plan: the bottom of the search pill + ☰ row. */
    val planTopRowDp: Int get() = TOP_BAR_BOTTOM_DP

    /** The box's top below the status bar. [navigateColumnDp]: Navigate's measured top column (turn + status cards). */
    fun topDp(screen: MessageScreen, navigateColumnDp: Int = 0, planTopDp: Int = planTopRowDp): Int = GAP_DP + when (screen) {
        MessageScreen.PLAN -> planTopDp
        MessageScreen.NAVIGATE -> navigateColumnDp.coerceAtLeast(0)
        MessageScreen.ROUTES -> ROUTES_HEADER_DP
        MessageScreen.SETTINGS -> SETTINGS_HEADER_DP
    }
}
