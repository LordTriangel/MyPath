package com.onpath.app.ui.routes

import com.onpath.app.debug.launchSafe
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlannedRoute
import com.onpath.app.ui.CoroutineBackground
import com.onpath.app.ui.NameProposal
import com.onpath.app.ui.plan.PlanDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** [SavePorts] and the one suspending member: the place name proposed for a plan (the app: the search index / plan link title). */
interface SaveHost : SavePorts {
    /** The place name proposed for a plan ("Algund → Meran", a plan link's title); null = none. */
    suspend fun proposeName(points: List<GeoPoint>, route: PlannedRoute?): String?
}

/**
 * Save (C18) as the app runs it: [SaveState] with the coroutines – the work on [scope] (the view model's), the blocking calls
 * on [io], the place name from [SaveHost.proposeName] waited for at most [nameWaitMs]. Name and constructor are the old ones.
 */
class SaveSlice(
    plan: PlanDocument,
    routes: RoutesState,
    recent: RecentPlans,
    store: SaveStore,
    host: SaveHost,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    nameWaitMs: Long = RecentPlans.NAME_WAIT_MS,
) : SaveState(plan, routes, RecentAsPorts(recent), store, host, CoroutineBackground(scope, io),
    NameProposal { what, pts, r, maxMs, always, got ->
        scope.launchSafe("app", what) {
            try { got(withTimeoutOrNull(maxMs) { host.proposeName(pts, r) }) } finally { always() }
        }
    }, nameWaitMs)

/** [RecentPlans] as the [Recent] port of [SaveState]. */
private class RecentAsPorts(private val recent: RecentPlans) : Recent {
    override fun keep(plan: LeavingPlan, why: String): Long? =
        recent.keep(RecentPlans.Leaving(plan.points, plan.unsaved, plan.savedId, plan.route, plan.activity, plan.sac, plan.stops, plan.pins,
            plan.dated, plan.days, plan.difficulty), why)

    override fun keepReplaced(old: com.onpath.app.data.Tour, segments: List<List<com.onpath.app.gpx.TrackPoint>>) = recent.keepReplaced(old, segments)
}
