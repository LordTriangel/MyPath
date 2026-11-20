package com.onpath.app.ui.plan

import com.onpath.app.domain.HoldPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** What [PlaceAddSlice] asks of the view model: everything is in [PlaceAddPorts] (no suspending call). */
interface PlaceAddHost : PlaceAddPorts

/**
 * Adding points to the plan: the state and decisions of [PlaceAddState] (coroutine-free) on a [HoldPaths] built here from the
 * hold's thread and the view model's scope. Constructed with the view model (no work).
 */
class PlaceAddSlice(
    plan: PlanDocument,
    planEdit: PlanEditSlice,
    planRun: PlanRunSlice,
    host: PlaceAddHost,
    router: HoldRouter,
    /** The hold's own routing thread ([RoutingThreads.hold]). */
    holdWorker: CoroutineDispatcher,
    /** Where a measure's result is applied: the view model's scope (the main thread). */
    scope: CoroutineScope,
) : PlaceAddState(plan, planEdit, planRun, host, router, HoldPaths(scope, holdWorker))
