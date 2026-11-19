package com.onpath.app.ui

import com.onpath.app.domain.AppMode
import com.onpath.strings.*

/**
 * Routing data the user waits for is part of the map wait window (form A, [MapWait]): its bytes are in the loading step
 * („lädt Karte + Routendaten“), the routing file's rewrite in the join step – never a 4th row (owner 2026-11-19).
 */
object RoutingLoads {
    /**
     * Routing data arrived: the plan is computed again at once, without a question (planning, not re-routing a tour) –
     * in Plan, not on tour, when there is a route (≥ 2 points).
     */
    fun recalcOnArrival(mode: AppMode, onTour: Boolean, points: Int): Boolean = mode == AppMode.PLAN && !onTour && points >= 2

    val FAILED_TEXT: String get() = Txt.s(Res.string.plan_routing_load_failed)
}
