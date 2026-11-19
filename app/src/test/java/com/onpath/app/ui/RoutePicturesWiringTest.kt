package com.onpath.app.ui

import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 piece 6: the route pictures and the demo rides moved from AppViewModel to plain classes in :app. What these
 * helpers decide is covered by ThumbRules / OverviewRules / RouteThumbsTest / OverviewPicturesTest / DemoPathTest /
 * NavSimStartTest / DemoRulesTest (:state); the picture work itself by RouteThumbE2ETest / OverviewSwipeE2ETest. This pins
 * the wiring: what the view model hands in and forwards, and the stored places / guards that moved verbatim.
 */
class RoutePicturesWiringTest {
    private val vm = sourceFile("ui/AppViewModel.kt").readText()
    private val pics = sourceFile("ui/RoutePictures.kt").readText()
    private val demo = sourceFile("ui/DemoRides.kt").readText()
    private fun has(src: String, what: String, regex: Boolean = false) =
        assertTrue("not found: $what", if (regex) Regex(what).containsMatchIn(src) else src.contains(what))

    @Test fun theViewModelBuildsThePicturesLazilyWithItsScopeIoStoreAndRoutes() {
        has(vm, """private val pictures by lazy \{\s+RoutePictures\(getApplication\(\), viewModelScope, Dispatchers\.IO, db, routes, object : RoutePicturesHost""", true)
        for (f in listOf("val thumbs get() = pictures.thumbs", "val thumbVersion: Int get() = pictures.thumbVersion",
            "val overviewPictures get() = pictures.overviewPictures", "val thumbTimings: List<Long> get() = pictures.thumbTimings",
            "fun kickThumbs(firstUuid: String? = null) = pictures.kickThumbs(firstUuid)")) has(vm, f)
        has(vm, "pictures.overviewShot(t, segments, tint, widthPx, heightPx, paddingPx, under)")
        has(vm, "pictures.routeSnapshot(segments, tint, widthPx, heightPx, paddingPx, under)")
    }

    @Test fun theViewModelBuildsTheDemoRidesLazilyAndForwardsEveryMember() {
        has(vm, """private val demo by lazy \{\s+DemoRides\(viewModelScope, Dispatchers\.IO, db, object : DemoRidesHost""", true)
        for (f in listOf("demo.demoLine()", "demo.startDemoRide()", "demo.startNavSim(line)", "demo.recentRecordedTours()",
            "demo.demoTourLabel(t)", "demo.startDemoTour(t)", "demo.navSimFollow(line)", "demo.startDemoAtMapCentre()"))
            has(vm, "= $f")
        has(vm, "if (com.onpath.app.recording.DemoLocation.endWithTour()) demo.navSimLine = null")
    }

    @Test fun theStoredFoldersAndTheBusyRuleAreAsTheyWere() {
        has(pics, "java.io.File(app.filesDir, \"thumbs\")")
        has(pics, "java.io.File(app.filesDir, \"overviews\")")
        has(pics, "host.mode == AppMode.NAVIGATE || host.recState.value != null") // never while navigating or recording
        has(pics, "scope.launchSafe(\"routes\", \"Map picture shown\", Dispatchers.Main) { thumbVersion++ }")
        has(pics, "overviewPictures.setSize(widthPx, heightPx, paddingPx, done = t.kind == TourKind.RECORDED)") // owner 2026-11-15
        has(pics, "val store = t.uuid.isNotEmpty() && t.id >= 0") // the plan on the map is never stored
        has(pics, "Map pictures not started")
        has(pics, "Overview pictures not started")
    }

    @Test fun theDemoRidesKeepTheirGuards() {
        has(demo, "if (path == null) { host.sayInfo(\"This line is too short for a demo ride.\", NoticePlace.SETTINGS); return }")
        has(demo, "navSimLine = null // a recorded tour is ridden as it was")
        has(demo, "if (navSimLine == null || line == null || line.size < 2 || line == navSimLine) return")
        has(demo, "DemoRules.recentRecordedTours(host.tours, com.onpath.app.recording.DemoLocation.TOUR_PREFIX)")
    }
}
