package com.onpath.app.ui.routes

import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the flows of [RoutesSlice] cannot see: the view model's own wiring of it (rebuild phase 2 piece 13). The slice is
 * tested against fakes; these pins say the app's real adapters hand the Android stores and the view model exactly what the
 * old code did – the arguments of the store, each host call, the forwarders the screens call, the order of declaration.
 */
class RoutesWiringTest {
    private val vm = sourceFile("ui/AppViewModel.kt").readText()

    private fun between(start: String, end: String): String {
        assertTrue("anchor found: $start", vm.contains(start))
        return vm.substringAfter(start).substringBefore(end)
    }

    private val store get() = between("private val toursStore = object : com.onpath.app.ui.routes.ToursStore {", "    private val saveHost")
    private val host get() = between("private val routesHost = object : com.onpath.app.ui.routes.RoutesHost {", "    private val routesFlow")

    /** The one store object serves Save and Routes: its new calls read the same TourDb / the same times file as before. */
    @Test fun theToursStoreReadsThePlanPointsAndTheTimesFileAsTheViewModelDid() {
        assertTrue(store.contains("override fun planPoints(id: Long) = db.planPoints(id)"))
        assertTrue(store.contains("com.onpath.app.time.TourTimesStore(File(getApplication<Application>().filesDir, com.onpath.app.time.TourTimesStore.FILE)).all()"))
        assertTrue("Save's calls are still the same ones (SaveFlowTest, TourDbMirrorTest)", store.contains("override fun setFavourite(id: Long, favourite: Boolean) = db.setFavourite(id, favourite)"))
        assertTrue("Save and Routes share it", vm.contains("SaveSlice(plan, routes, recent, toursStore, saveHost,"))
        assertTrue(vm.contains("RoutesSlice(plan, routes, save, tour, planRoute, toursStore, mapCamera, routesHost,"))
    }

    @Test fun theHostHandsEachCallToTheViewModelMemberTheOldBodyUsed() {
        for (line in listOf(
            "override val touring: Boolean get() = recState.value != null",
            "override val recordingTourId: Long? get() = recState.value?.tourId",
            "override val mode: AppMode get() = this@AppViewModel.mode",
            "override fun enterMode(m: AppMode) = enter(m)",
            "override fun syncMarkSeen(t: Tour) = this@AppViewModel.syncMarkSeen(t)",
            "override fun rememberActivity(a: Activity) { prefs.activity = a }",
            "override fun rememberSac(level: Int) { prefs.sac = level }",
            "override fun loadedOnTheWay(on: Boolean) { this@AppViewModel.loadedOnTheWay = on }",
            "override fun routeEvent(e: RouteRun.Event) { this@AppViewModel.routeEvent(e) }",
            "override fun change(block: MutableList<GeoPoint>.() -> Unit) = this@AppViewModel.change(block)",
            "override fun rememberPlaceName(p: GeoPoint, name: String) = this@AppViewModel.rememberPlaceName(p, name)",
            "override fun saved(name: String?, id: Long, points: List<GeoPoint>?) = setSaved(name, id, points)",
            "override fun opened(t: Tour, pts: List<GeoPoint>) = multiDay.opened(t, pts)",
            "override fun syncRoutingCheck(t: Tour, pts: List<GeoPoint>) = this@AppViewModel.syncRoutingCheck(t, pts)",
            "this@AppViewModel.standIn = StandIn(pts, act, level, preview)",
            "override fun withMaps(t: Tour) = multiDay.withMaps(t)",
            "override fun kickThumbs() = this@AppViewModel.kickThumbs()",
            "override fun clearHighlight() { highlight = null }",
            "override fun followShownNow() = this@AppViewModel.followShownNow()",
            "override fun ensureRoutingData(parts: List<String>) = mapData.routingData.ensure(parts)",
            "override fun routingParts(track: List<GeoPoint>, act: Activity) = com.onpath.app.data.RoutingTiles.forLine(track, act)",
            "override fun tourChanged(id: Long) = Backups.tourChanged(getApplication(), id)",
            "override fun syncForget(uuid: String) { sync.files.marks.remove(uuid) }",
            "override fun syncChanged(why: String) = sync.changed(why)",
            "override fun say(text: String, kind: NoticeKind, place: NoticePlace) = notices.say(text, kind, place)",
        )) assertTrue("the host says: $line", host.contains(line))
    }

    /** The screens, the tests and the other slices' hosts call these names: each is a one-line forward to the slice. */
    @Test fun theOldNamesForwardToTheSlice() {
        for (line in listOf(
            "fun refreshTours(): Unit = routesFlow.refreshTours()",
            "routesFlow.openTour(t, duringTour, trackThen, lineOf, planned)",
            "private fun showTrack(s: ShownTrack): Unit = routesFlow.showTrack(s)",
            "fun closeShown(): Unit = routesFlow.closeShown()",
            "val planFromTrackBusy: Boolean get() = routesFlow.planFromTrackBusy",
            "fun cancelPlanFromTrack(): Unit = routesFlow.cancelPlanFromTrack()",
            "fun planFromShown(): Unit = routesFlow.planFromShown()",
            "routesFlow.planFromLine(track, parts, act, level, done)",
            "fun deleteTour(t: Tour): Unit = routesFlow.deleteTour(t)",
            "private fun unlessUnsaved(what: String, go: () -> Unit): Unit = routesFlow.unlessUnsaved(what, go)",
            "get() = routesFlow.shown",
            "private set(v) { routesFlow.shown = v }",
        )) assertTrue("forwarder: $line", vm.contains(line))
        // the slice's launches are the view model's scope and its two dispatchers, the elapsed clock the SystemClock
        assertTrue(vm.contains("viewModelScope, Dispatchers.IO, Dispatchers.Main, elapsedMs = { android.os.SystemClock.elapsedRealtime() })"))
    }

    /** Init order: the slice is built after what it takes (save, tour, planRoute, mapCamera, toursStore) and before any init that reads `shown`. */
    @Test fun theSliceIsDeclaredAfterItsNeighboursAndAboveTheInitBlocksThatReadIt() {
        fun at(s: String) = vm.indexOf(s).also { assertTrue("found: $s", it >= 0) }
        val flow = at("private val routesFlow = com.onpath.app.ui.routes.RoutesSlice(")
        for (n in listOf("private val toursStore = ", "private val save = com.onpath.app.ui.routes.SaveSlice(", "val tour = com.onpath.app.ui.tour.TourController(",
            "private val planRoute = com.onpath.app.ui.plan.PlanRouteSlice(", "private val mapCamera = ", "private val plan = com.onpath.app.ui.plan.PlanDocument("))
            assertTrue("$n above the Routes slice", at(n) < flow)
        assertTrue("above the init that routes the saved plan and refreshes the list", flow < at("    init {\n        recalc()"))
    }

    /** The moved bodies do not exist twice: the view model holds none of the rules (one place: the slice). */
    @Test fun theViewModelKeepsNoCopyOfTheMovedBodies() {
        for (gone in listOf("TrackToPlan.fit(", "routes.toBin(", "StoredLine.preview(", "RideView.lines(", "SportType.openChoice(",
            "planFromTrackCancel", "msg_tour_delete_failed", "plan_from_track_failed", "plan_from_track_wait"))
            assertTrue("not in the view model: $gone", !vm.contains(gone))
    }
}
