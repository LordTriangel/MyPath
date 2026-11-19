package com.onpath.app.plansync

import com.onpath.app.sync.SyncRound

/** What one pass did ([PlanSyncPass.run]). */
data class SyncPassResult(
    val outcome: SyncRound.Outcome?,
    /** The other side's name ("Chrome on Linux"), newest hello first; null = none yet. */
    val peer: String? = null,
    val added: Int = 0,
    val updated: Int = 0,
    val binned: Int = 0,
    val failed: Int = 0,
    /** Items from a newer app the phone cannot show (unknown kind or plan format). */
    val unreadable: Int = 0,
    /** Planned routes added / changed from outside: route them (AppViewModel, routing dispatcher). */
    val toRoute: List<Long> = emptyList(),
    /** True when the tours changed (refresh My routes). */
    val toursChanged: Boolean = false,
    val skippedForRecording: Boolean = false,
    /**
     * Routing data tiles ("E10_N45") a plan from outside was routed with in a newer version than the phone's file
     * (field 8): check them for newer data now (AppViewModel, Rd5PartStore.refreshIfNewer) – not after 35 days.
     */
    val newerData: List<String> = emptyList(),
    /**
     * Routes the other side sent under its own id that this phone already had unlinked or under a fresh id of its own
     * (a reinstalled phone, field bug 2026-11-12): linked to the route instead of added again.
     */
    val relinked: Int = 0,
    /** The phone's kept places (Zuhause, favourites) changed from the other side: show them again, back them up. */
    val placesChanged: Boolean = false,
)
