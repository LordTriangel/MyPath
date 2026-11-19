package com.onpath.app.ui

/**
 * Where [OverviewPill] sits (owner 2026-11-06: it must cover nothing): on the row of the round activity button (top
 * left under the search band, 48 dp at 12 dp from the edge), right of it – or at the left edge when that button is not
 * shown; the map controls on the right start lower ([ControlsPlacement]), so the row is free to the right edge. Below
 * the background-download strip while that shows. Pure, unit-tested.
 */
object OverviewPillPlace {
    const val HEIGHT_DP = 48
    const val EDGE_DP = 12
    /** The activity button: 48 dp at [EDGE_DP], plus a gap. */
    const val BESIDE_ACTIVITY_DP = EDGE_DP + 48 + 10

    fun startDp(activityButtonShown: Boolean): Int = if (activityButtonShown) BESIDE_ACTIVITY_DP else EDGE_DP

    /** [rowTopDp]: the activity button's top; [stripBottomDp]: the download strip's bottom while it shows, else null. */
    fun topDp(rowTopDp: Int, stripBottomDp: Int?): Int =
        if (stripBottomDp != null) maxOf(rowTopDp, stripBottomDp + MessagePlacement.GAP_DP) else rowTopDp
}
