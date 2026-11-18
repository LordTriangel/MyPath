package com.onpath.web

import com.onpath.app.data.Geo
import com.onpath.app.domain.PlanStatus
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.LegCache
import com.onpath.app.routing.LongRoute

/**
 * The routing wait box's progress by km (owner 2026-11-18, a long bike tour München–Venedig: „Calculating section 2 of
 * 2…“ alone says nothing for minutes): the km of the legs already routed out of the plan's whole length. Honest: a leg
 * counts when it is routed (the [com.onpath.app.routing.Planner] reports it), nothing in between. A leg known – from the
 * [legs] cache before the run, or routed in this run (the Planner keeps it there before it reports) – counts its real
 * length; a leg still to route its estimate, the straight line × [LongRoute.DETOUR] (the long-route question's estimate).
 * The legs the Planner routes are the ones not in the cache when the run starts, in their order.
 */
class WebPlanProgress(private val wps: List<GeoPoint>, private val act: Activity, private val sac: Int, private val legs: LegCache) {
    /** The legs to route in this run (not in the cache now), in order. */
    private val todo: List<Int> = (0 until wps.size - 1).filter { !legs.has(wps[it], wps[it + 1], act, sac) }
    private val estimate = DoubleArray(maxOf(0, wps.size - 1)) { Geo.distance(wps[it], wps[it + 1]) * LongRoute.DETOUR }

    /**
     * The Planner's report: [done] of [total] legs to route are done. JSON for the page: `text` = the app's „Calculating
     * section 2 of 5…“ (null for one leg), `doneM` / `totalM` = metres routed / the plan's length (known + estimated).
     */
    fun json(done: Int, total: Int): String {
        val routed = if (total == todo.size) todo.take(done).toSet() else emptySet()
        var doneM = 0.0; var totalM = 0.0
        for (i in estimate.indices) {
            val known = if (i in todo && i !in routed) null else legs.get(wps[i], wps[i + 1], act, sac)?.distanceM
            val m = known ?: estimate[i]
            totalM += m
            if (i !in todo || i in routed) doneM += m
        }
        // a run the cache does not explain (should not happen): the share of the legs to route
        if (total != todo.size && total > 0) doneM = totalM * done / total
        val text = if (PlanStatus.hasLegs(done to total)) Engine.str(PlanStatus.calculating(done to total)) else "null"
        return "{\"text\":$text,\"doneM\":${Math.round(doneM)},\"totalM\":${Math.round(totalM)}}"
    }
}
