package com.onpath.app.ui

import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The view model's side of the add slice (rebuild phase 2 piece 11): the slice's flows are PlaceAddFlowTest (:state, with a
 * recording host and a scripted router); what a flow cannot see is what the view model answers when the slice calls its host
 * and how the old names forward. Every host member is pinned by its exact line, scoped to the `placeAdd` construction (the other
 * slices' hosts hold similar lines: the map's has `canEditMap`, the edit's has `myPosition` and `touring`), every forwarder by
 * its exact line, and the places where the view model's own members (the POI menu, the place card, the plan edit's host, the
 * view model's end) now go to the slice.
 */
class PlaceAddAdaptersTest {
    private val vm = sourceFile("ui/AppViewModel.kt").readText()
    private val head = "    private val placeAdd = PlaceAddSlice("
    private val block = vm.substringAfter(head).substringBefore("\n    private val routeRun get() = planRun.routeRun")

    private fun blockHas(line: String) = assertTrue("the placeAdd block must have: $line", block.contains(line))

    @Test fun theHostAnswersFromTheViewModelsOwnMembers() {
        assertTrue("the placeAdd block was found", vm.contains(head) && block.length > 2000 && block.contains("object : com.onpath.app.ui.plan.PlaceAddHost"))
        assertTrue("the block's end anchor was found", vm.substringAfter(head).contains("\n    private val routeRun get() = planRun.routeRun"))
        listOf(
            "override fun closeMapOverlays(why: String) = this@AppViewModel.closeMapOverlays(why)",
            "override fun openPointMenu(index: Int, via: String) = this@AppViewModel.openPointMenu(index, via = via)",
            "override fun closePlaceCard() = this@AppViewModel.closePlaceCard()",
            "override fun planMode(): Boolean = mode == AppMode.PLAN",
            "override fun canEditMap(): Boolean = this@AppViewModel.canEditMap",
            "override fun activity(): Activity = this@AppViewModel.activity",
            "override fun sac(): Int = this@AppViewModel.sac",
            "override fun routeLine(): List<GeoPoint>? = route?.points",
            "override fun touring(): Boolean = recState.value != null",
            "override fun myPosition(): GeoPoint? = this@AppViewModel.myPosition()",
            "override fun tourPassed(plan: List<GeoPoint>): Set<Int> = this@AppViewModel.tourPassed(plan)",
            "override fun followLine(): List<GeoPoint>? = this@AppViewModel.followLine",
            "override fun wayThereFrom(): GeoPoint? = this@AppViewModel.wayThereFrom()",
            "override fun gpsPoint(): GeoPoint? = tracker.location.value?.let { GeoPoint(it.latitude, it.longitude) }",
            "override fun searchMark(): com.onpath.app.search.Place? = this@AppViewModel.searchMark",
            "override fun takePin(lat: Double, lon: Double): com.onpath.app.search.Place? = this@AppViewModel.takePin(lat, lon)",
            "override fun isCandidate(poi: com.onpath.app.poi.Poi): Boolean = this@AppViewModel.isCandidate(poi)",
            "override fun rememberPlaceName(p: GeoPoint, name: String) = this@AppViewModel.rememberPlaceName(p, name)",
            "override fun rememberPoiKind(p: GeoPoint, kind: String?) = this@AppViewModel.rememberPoiKind(p, kind)",
            "override fun stopIndexAt(lat: Double, lon: Double): Int = waypoints.indexOfFirst { sameSpot(it, lat, lon) }",
            "override fun routeIndexOf(place: com.onpath.app.search.Place): Int? = this@AppViewModel.routeIndexOf(place)",
        ).forEach(::blockHas)
    }

    @Test fun theSlicesCollaboratorsAreTheViewModelsOwnAndTheRouterIsTheEngineStayingHere() {
        // the router port is the Planner on the engine and the leg cache (stay in :app); the hold's own thread; the main scope
        blockHas("        plan = plan, planEdit = planEdit, planRun = planRun,\n")
        blockHas("        router = com.onpath.app.ui.plan.HoldRouter { a, b, act, level, cancel -> routedLength(a, b, act, level, cancel) },\n")
        blockHas("        holdWorker = planRoute.threads.hold, scope = viewModelScope,\n")
        val routed = vm.substringAfter("    private fun routedLength(").substringBefore("\n    }\n")
        assertTrue(routed, routed.contains("legCache.lookup(a, b, act, level)?.let { return it.leg.distanceM }"))
        assertTrue(routed, routed.contains("val r = Planner(routingEngine(), cancel, legs = legCache).plan(listOf(a, b), act, level)"))
        assertTrue(routed, routed.contains("if (r.legs.any { it.straight }) throw com.onpath.app.routing.RoutingException(r.error ?: \"no route for the leg\")"))
        // built after the edit slice it edits through and before the slice that takes `change`
        assertTrue(vm.indexOf("private val planEdit:") < vm.indexOf(head))
        assertTrue(vm.indexOf(head) < vm.indexOf("internal val multiDay ="))
        // the view model's end stops a running measure
        assertTrue(vm.contains("        placeAdd.close()\n        planRoute.close() // the four routing threads\n"))
    }

    @Test fun theOldNamesForwardToTheSlice() {
        listOf(
            "fun addPoint(p: GeoPoint, lineSnapM: Double = 30.0, onPoi: Boolean = false) = placeAdd.addPoint(p, lineSnapM, onPoi)",
            "fun addPoiAsStop(poi: com.onpath.app.poi.Poi, lineSnapM: Double = 30.0) = placeAdd.addPoiAsStop(poi, lineSnapM)",
            "fun poiAsNewDestination(poi: com.onpath.app.poi.Poi) = placeAdd.poiAsNewDestination(poi)",
            "fun holdOnMap(p: GeoPoint, lineSnapM: Double) = placeAdd.holdOnMap(p, lineSnapM)",
            "private fun cancelHoldMeasure(why: String) = placeAdd.cancelHoldMeasure(why)",
            "fun holdAsStop() = placeAdd.holdAsStop()",
            "fun holdAsNewDestination() = placeAdd.holdAsNewDestination()",
            "fun candidateJoin(): Pair<GeoPoint, GeoPoint>? = placeAdd.candidateJoin()",
            "fun openFirstPointMenu(p: GeoPoint) = placeAdd.openFirstPointMenu(p)",
            "fun startHere(p: GeoPoint) = placeAdd.startHere(p)",
            "fun routeFromMyLocation(p: GeoPoint) = placeAdd.routeFromMyLocation(p)",
            "fun poiFromMyLocation(poi: com.onpath.app.poi.Poi) = placeAdd.poiFromMyLocation(poi)",
            "fun addPoiPoint(poi: com.onpath.app.poi.Poi, lineSnapM: Double = 30.0) = placeAdd.addPoiPoint(poi, lineSnapM)",
        ).forEach { assertTrue("forwarder: $it", vm.contains("    $it\n")) }
        // the three state holders: read and written through the slice (the map's overlay closes the menus by `vm.x = null`)
        assertTrue(vm.contains("    var holdAddMenu: PlaceAddSlice.HoldAdd?\n        get() = placeAdd.holdAddMenu\n        set(v) { placeAdd.holdAddMenu = v }\n"))
        assertTrue(vm.contains("    var holdMeasuring: GeoPoint?\n        get() = placeAdd.holdMeasuring\n        private set(v) { placeAdd.holdMeasuring = v }\n"))
        assertTrue(vm.contains("    var firstPointMenu: GeoPoint?\n        get() = placeAdd.firstPointMenu\n        set(v) { placeAdd.firstPointMenu = v }\n"))
        // the card's roles map one to one
        assertTrue(vm.contains("    fun addPlace(place: com.onpath.app.search.Place, role: PlaceRole) = placeAdd.addPlace(place, when (role) {\n" +
            "        PlaceRole.SMART -> AddRole.SMART\n        PlaceRole.START -> AddRole.START\n        PlaceRole.DESTINATION -> AddRole.DESTINATION\n    })\n"))
    }

    @Test fun theMembersThatStayAreReadingTheSlice() {
        // the POI menu (piece 12) decides its rows on the same measure, its ring and its thread
        val poiMenu = vm.substringAfter("    fun openPoiMenu(").substringBefore("\n    }\n")
        assertTrue(poiMenu, poiMenu.contains("PlaceMenuMeasure.start(placeAdd.holdPaths, pts, p, placeAdd.lineDpOf(p, lineSnapM), PlaceAddMenu.detours(pts, p)!!,"))
        assertTrue(poiMenu, poiMenu.contains("{ a, b, cancel -> routedLength(a, b, act, level, cancel) }) { r ->"))
        val menu = vm.substringAfter("    fun placeMenu(").substringBefore("\n    }\n")
        assertTrue(menu, menu.contains("PlaceAddMenu.planningPlaceMenu(placeAdd.lineDpOf(p, poiMenuRows?.lineSnapM ?: 30.0), d)"))
        assertTrue(menu, menu.contains("return PlaceAddMenu.menu(waypoints.size, touring, placeAdd.zoneFor(p))"))
        // the label and the card say what the add really does: the same decision
        assertTrue(vm.substringAfter("    fun poiAddLabel(").substringBefore("\n    }\n").contains("val ins = placeAdd.insertionFor(GeoPoint(poi.lat, poi.lon), lineSnapM)"))
        assertTrue(vm.substringAfter("    fun placeCardUi(").substringBefore("\n    }\n").contains("val ins = placeAdd.insertionFor(GeoPoint(place.lat, place.lon), 30.0)"))
        // "Start here" of the point menu is the add's edit
        assertTrue(vm.substringAfter("    fun makeStartFromMenu(").substringBefore("\n    }\n").contains("placeAdd.startAtPoint(index)"))
        // closing every map menu goes through the forwarders of the three state holders and stops a running measure
        val close = vm.substringAfter("    fun closeMapOverlays(").substringBefore("\n    }\n")
        assertTrue(close, close.contains("cancelHoldMeasure(why)"))
        assertTrue(close, close.contains("poiMenu = null; poiMenuRows = null; firstPointMenu = null; holdAddMenu = null; if (planPoi != null) closePlaceCard()"))
    }

    @Test fun theAddingFunctionsAreNotInTheViewModelAnyMore() {
        for (gone in listOf("fun addPoint(p: GeoPoint, lineSnapM: Double = 30.0, onPoi: Boolean = false) {", "private class Insertion(", "private fun insertionFor(",
                "private fun lineDpOf(", "private fun zoneFor(", "private fun insertAsStop(", "private fun asNewDestination(", "class HoldAdd(",
                "private fun measureHold(", "private fun addPoiWith(", "private fun startAtPoint(", "private inline fun fittingPlaceAdd(",
                "private val holdPaths", "private val holdRouting", "private var pinTaken", "private fun set(list"))
            assertTrue("$gone moved to the slice", !vm.contains(gone))
    }
}
