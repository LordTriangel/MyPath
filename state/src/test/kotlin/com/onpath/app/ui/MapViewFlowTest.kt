package com.onpath.app.ui

import com.onpath.app.domain.AppMode
import com.onpath.app.domain.PlanCamera
import com.onpath.app.gpx.GpxWaypoint
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import com.onpath.strings.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The map's camera (rebuild phase 2 piece 5) as flows of the REAL [MapViewSlice] with a real [NoticeBoard]: the first
 * route is fitted once and not after a hand on the map, "Show whole route" picks the plan / the shown track / the followed
 * line, a hand on the map ends following and free look ends after 25 m, the profile scrub holds 4 s and then follows again,
 * no position → the neutral note, the compass, locate, coming back to the app, the plan shown whole, a piece. Fakes only at
 * the edges: the camera port (records WHAT was asked and the state of the slice at the moment – so "following stops first" is
 * the order the test sees, not what the test says), the position, the host (what the view model answers and is told) and
 * the clock: the slice's scope runs on a virtual-time dispatcher (inline like Main.immediate; the waits are advanced by the
 * test). Every fake checks that it is called on the thread the slice runs on.
 *
 * Replaces the source pins of the view model's `fitFirstRoute` (WholeRouteTest.firstRouteIsFitted: following stopped before
 * the fit, a touch since the ask wins).
 */
class MapViewFlowTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    /** Runs a launched block inline (like Main.immediate) and holds `delay`s until [advance] reaches them. */
    @OptIn(InternalCoroutinesApi::class)
    private class VirtualMain : CoroutineDispatcher(), Delay {
        var now = 0L
        private class Wait(val at: Long, val seq: Int, val cont: CancellableContinuation<Unit>)
        private val waits = ArrayList<Wait>()
        private var seq = 0
        val pending: Int get() = waits.count { it.cont.isActive }
        override fun isDispatchNeeded(context: CoroutineContext) = false
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            waits += Wait(now + timeMillis, seq++, continuation)
        }
        fun advance(ms: Long) {
            val to = now + ms
            while (true) {
                val next = waits.filter { it.cont.isActive }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: break
                if (next.at > to) break
                waits.remove(next); now = next.at; next.cont.resume(Unit)
            }
            now = to
            waits.removeAll { !it.cont.isActive }
        }
    }

    private val here = GeoPoint(46.67, 11.16)
    private val a = GeoPoint(46.60, 11.10); private val b = GeoPoint(46.62, 11.12); private val c = GeoPoint(46.64, 11.14)
    private fun track(vararg pts: GeoPoint) = ShownTrack(1L, "Track", listOf(pts.toList()),
        listOf(GpxWaypoint(46.9, 11.9, "Peak")), 1.0, 1.0, 1.0, 1.0)

    private inner class App {
        val main = VirtualMain()
        val testThread: Thread = Thread.currentThread()
        var elapsed = 1_000L // the phone has been up a while: 0 would read as "never stopped"
        /** Everything the camera and the host were asked, in order; the camera lines carry the slice's state at that moment. */
        val log = ArrayList<String>()
        var fix: GeoPoint? = null
        var modeNow = AppMode.PLAN
        var headingUpNow = false
        var navStartZoomNow: Double? = null
        var route: List<GeoPoint>? = null
        var points: List<GeoPoint> = emptyList()
        var shownTrack: ShownTrack? = null
        var followed: List<GeoPoint>? = null
        var original: List<GeoPoint>? = null
        var touringNow = false
        var keepUntil = 0L
        var highlighted: GeoPoint? = null
        var spots: List<GeoPoint>? = null
        var activityNow = Activity.HIKE
        lateinit var map: MapViewSlice

        private fun onSliceThread(what: String) = check(Thread.currentThread() === testThread) { "$what on another thread" }
        private fun state() = "follow=${map.followMe} free=${map.freeLookAt != null}"

        val board = NoticeBoard(routesOpen = { false }, settingsOpen = { false }, mapShown = { modeNow == AppMode.PLAN })
        val camera = object : Camera {
            override fun fit(points: List<GeoPoint>) { onSliceThread("fit"); log += "fit ${points.size} [${state()}]" ; fitted += points }
            override fun centerOn(point: GeoPoint, zoom: Double) { onSliceThread("centerOn"); log += "centerOn ${point.lat} z$zoom [${state()}]" }
            override fun panTo(point: GeoPoint) { onSliceThread("panTo"); log += "panTo ${point.lat} [${state()}]" }
        }
        val fitted = ArrayList<GeoPoint>()
        val host = object : MapViewHost {
            override val mode: AppMode get() = modeNow
            override val activity: Activity get() = activityNow
            override val headingUp: Boolean get() = headingUpNow
            override fun setHeadingUp(on: Boolean) { onSliceThread("setHeadingUp"); log += "headingUp $on [follow=${map.followMe} recentre=${map.recentreTick} north=${map.northTick}]"; headingUpNow = on }
            override var navStartZoom: Double?
                get() = navStartZoomNow
                set(v) { onSliceThread("navStartZoom"); log += "navStartZoom $v"; navStartZoomNow = v }
            override val routePoints: List<GeoPoint>? get() = route
            override val waypoints: List<GeoPoint> get() = points
            override val shown: ShownTrack? get() = shownTrack
            override val followLine: List<GeoPoint>? get() = followed
            override val navOriginal: List<GeoPoint>? get() = original
            override val touring: Boolean get() = touringNow
            override val keepCameraUntilMs: Long get() = keepUntil
            override fun highlight(at: GeoPoint?) { onSliceThread("highlight"); log += "highlight ${at?.lat}"; highlighted = at }
            override fun closeSpots() { onSliceThread("closeSpots"); log += "closeSpots [${state()}]" }
            override fun spotLine(piece: List<GeoPoint>) { onSliceThread("spotLine"); log += "spotLine ${piece.size} [${state()}]"; spots = piece }
            override fun appReturned(inPlan: Boolean) { onSliceThread("appReturned"); log += "returned plan=$inPlan [${state()}] on=${map.appOnScreen}" }
            override fun appLeft() { onSliceThread("appLeft"); log += "left on=${map.appOnScreen}" }
        }
        init {
            map = MapViewSlice(board, camera, { onSliceThread("position"); fix }, host, CoroutineScope(Job() + main),
                uptimeMs = { main.now }, elapsedMs = { elapsed })
        }
        fun advance(ms: Long) { main.advance(ms); elapsed += ms }
        fun cameraCalls() = log.filter { it.startsWith("fit") || it.startsWith("centerOn") || it.startsWith("panTo") }
    }

    // ---------------------------------------------------------------- the first route

    @Test fun theFirstRouteIsFittedOnceTheSheetHasSettledWithFollowingStoppedFirst() {
        val app = App()
        app.route = listOf(a, b, c); app.points = listOf(a, c)
        app.map.followMe = true; app.map.freeLookAt = a
        app.map.fitFirstRoute(listOf(a, b, c), listOf(a, c))
        app.advance(WholeRoute.SETTLE_MS - 1)
        assertTrue("not before the sheet has settled", app.cameraCalls().isEmpty())
        assertEquals("free look is still there until then", a, app.map.freeLookAt)
        app.advance(1)
        assertEquals("free look gone when the fit is asked (it would follow again by itself)",
            listOf("fit 5 [follow=false free=false]"), app.cameraCalls())
        assertNull(app.map.freeLookAt)
        assertEquals("the line and the points", listOf(a, b, c, a, c), app.fitted)
        assertFalse(app.map.followMe)
    }

    @Test fun aSheetThatRisesWaitsLonger() {
        val app = App()
        app.route = listOf(a, b); app.points = listOf(a, b)
        app.map.fitFirstRoute(listOf(a, b), listOf(a, b), extraMs = 300L)
        app.advance(WholeRoute.SETTLE_MS)
        assertTrue(app.cameraCalls().isEmpty())
        app.advance(300)
        assertEquals(1, app.cameraCalls().size)
    }

    @Test fun aHandOnTheMapMeanwhileKeepsTheUsersView() {
        val app = App()
        app.route = listOf(a, b); app.points = listOf(a, b)
        app.advance(100) // the touch stamp must be later than the ask
        app.map.fitFirstRoute(listOf(a, b), listOf(a, b))
        app.advance(50)
        app.map.onMapTouch()
        app.map.followMe = true
        app.advance(WholeRoute.SETTLE_MS)
        assertTrue("no fit after a touch", app.cameraCalls().isEmpty())
        assertTrue("and following is not stopped for it", app.map.followMe)
        assertTrue(problems.traced("map").any { "First route not fitted" in it })
    }

    @Test fun theFirstRouteIsNotFittedWhenPlanWasLeftATrackIsShownOrTheRouteMovedOn() {
        for (change in listOf<(App) -> Unit>({ it.modeNow = AppMode.NAVIGATE }, { it.shownTrack = track(a, b) }, { it.route = listOf(a, c) })) {
            val app = App()
            app.route = listOf(a, b); app.points = listOf(a, b)
            app.map.fitFirstRoute(listOf(a, b), listOf(a, b))
            change(app)
            app.advance(WholeRoute.SETTLE_MS)
            assertTrue(app.cameraCalls().isEmpty())
        }
    }

    // ---------------------------------------------------------------- Show whole route

    @Test fun wholeRoutePicksTheShownTrackBeforeThePlanBeforeTheFollowedLine() {
        val app = App()
        app.route = listOf(a, b); app.points = listOf(a, c); app.followed = listOf(b, c)
        app.shownTrack = track(a, b, c)
        app.map.followMe = true; app.map.freeLookAt = a
        app.map.showWholeRoute()
        assertEquals("the track, its waypoint, north-up fit; following and free look stopped first",
            listOf("fit 4 [follow=false free=false]"), app.cameraCalls())
        assertEquals(listOf(a, b, c, GeoPoint(46.9, 11.9)), app.fitted)
        app.fitted.clear(); app.log.clear()
        app.shownTrack = null
        app.map.showWholeRoute()
        assertEquals("the plan line, then the plan's points", listOf(a, b, a, c), app.fitted)
        app.fitted.clear()
        app.route = null; app.points = emptyList()
        app.map.showWholeRoute()
        assertEquals("no plan: the followed line", listOf(b, c), app.fitted)
    }

    @Test fun wholeRouteInNavigateShowsTheFollowedLineTheOriginalAndWhereIAm() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE
        app.followed = listOf(b, c); app.original = listOf(a, b); app.fix = here
        app.map.followMe = true
        app.map.showWholeRoute()
        assertEquals(listOf(b, c, a, b, here), app.fitted)
        assertFalse("following comes back with locate only", app.map.followMe)
    }

    @Test fun wholeRouteWithNothingToShowDoesNothing() {
        val app = App()
        app.map.followMe = true
        app.map.showWholeRoute()
        assertTrue(app.cameraCalls().isEmpty()); assertTrue("following stays", app.map.followMe)
        assertFalse(app.map.hasPlanLine()); assertFalse(app.map.hasShownLine()); assertFalse(app.map.hasFollowLine())
        app.route = listOf(a); // one point is no line
        assertFalse(app.map.hasPlanLine())
        app.route = listOf(a, b); app.followed = listOf(a, b); app.shownTrack = track(a, b)
        assertTrue(app.map.hasPlanLine() && app.map.hasShownLine() && app.map.hasFollowLine())
    }

    @Test fun planAndMeTogetherOrLocateWhenThereIsNoPosition() {
        val app = App()
        app.route = listOf(a, b); app.points = listOf(a, b)
        app.map.followMe = false
        app.map.showPlanAndMe()
        assertEquals("no position: no fit", emptyList<String>(), app.cameraCalls())
        assertTrue("locate() follows the position", app.map.followMe)
        app.map.followMe = true; app.map.freeLookAt = a
        app.fix = here
        app.map.showPlanAndMe()
        assertEquals(listOf("fit 5 [follow=false free=false]"), app.cameraCalls())
        assertNull("free look is gone", app.map.freeLookAt)
        assertEquals(listOf(a, b, a, b, here), app.fitted)
        assertTrue(problems.traced("map").any { "plan and position shown together (5 points)" in it })
    }

    // ---------------------------------------------------------------- a hand on the map, free look

    @Test fun aHandOnTheMapEndsFollowingAndFreeLookEndsAfter25MetresOfWalking() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE; app.fix = here
        app.map.followMe = true
        app.map.onMapTouch()
        app.advance(200)
        app.map.onMapGesture()
        assertFalse(app.map.followMe)
        assertEquals("free look starts where the walker was", here, app.map.freeLookAt)
        val r0 = app.map.recentreTick
        app.map.checkFreeLook(here.lat + 0.0001, here.lon) // ~11 m
        assertFalse("not yet", app.map.followMe); assertEquals(r0, app.map.recentreTick)
        app.map.checkFreeLook(here.lat + 0.0003, here.lon) // ~33 m
        assertTrue("walked on: the map follows again", app.map.followMe)
        assertEquals("and the map is told to centre", r0 + 1, app.map.recentreTick)
        assertNull(app.map.freeLookAt)
    }

    @Test fun aStopWithoutATouchIsNoFreeLookAndPlanHasNone() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE; app.fix = here
        app.map.onMapTouch()
        app.advance(1_501)
        app.map.followMe = true
        app.map.onMapGesture() // the app's own camera move
        assertFalse(app.map.followMe); assertNull("a stop without a recent touch", app.map.freeLookAt)
        app.map.onMapTouch(); app.map.onMapGesture()
        assertEquals(here, app.map.freeLookAt)
        app.modeNow = AppMode.PLAN
        app.map.onMapTouch(); app.map.onMapGesture()
        assertNull("Plan: no free look", app.map.freeLookAt)
        app.modeNow = AppMode.NAVIGATE; app.fix = null
        app.map.onMapTouch(); app.map.onMapGesture()
        assertNull("no position: no free look", app.map.freeLookAt)
    }

    @Test fun freeLookEndsWhenFollowingResumedOrNavigateWasLeft() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE; app.fix = here
        app.map.onMapTouch(); app.map.onMapGesture()
        app.map.followMe = true // compass / locate meanwhile
        app.map.checkFreeLook(here.lat + 0.01, here.lon)
        assertNull(app.map.freeLookAt); assertEquals("no second recentre", 0, app.map.recentreTick)
        app.map.followMe = false
        app.map.onMapTouch(); app.map.onMapGesture()
        app.modeNow = AppMode.PLAN
        app.map.checkFreeLook(here.lat + 0.01, here.lon)
        assertNull(app.map.freeLookAt); assertFalse(app.map.followMe)
    }

    @Test fun planWithWalkingDirectionUpStopsTurningAtOnceWhenAFingerGoesDown() {
        val app = App()
        app.headingUpNow = true; app.map.followMe = true
        app.map.onMapTouch()
        assertFalse(app.map.followMe)
        app.headingUpNow = false; app.map.followMe = true
        app.map.onMapTouch()
        assertTrue("north up: the map keeps following", app.map.followMe)
        app.headingUpNow = true; app.modeNow = AppMode.NAVIGATE
        app.map.onMapTouch()
        assertTrue("Navigate: never", app.map.followMe)
    }

    // ---------------------------------------------------------------- scrub on the profile

    @Test fun theScrubPausesFollowingShowsTheSpotAndFollowsAgainAfterFourSeconds() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE
        app.map.followMe = true
        app.map.navScrub(b)
        assertEquals(listOf("highlight 46.62", "panTo 46.62 [follow=false free=false]"), app.log.filter { it.startsWith("highlight") || it.startsWith("panTo") })
        app.map.navScrubEnd()
        app.advance(3_999)
        assertEquals("the dot stays", b, app.highlighted); assertFalse(app.map.followMe)
        val r0 = app.map.recentreTick
        app.advance(1)
        assertNull("then it goes", app.highlighted)
        assertTrue("and the map returns to the position", app.map.followMe)
        assertEquals(r0 + 1, app.map.recentreTick)
    }

    @Test fun aNewFingerWithinTheHoldCancelsTheEndAndSeveralMovesFollowOnce() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE
        app.map.followMe = true
        app.map.navScrub(a); app.map.navScrubEnd()
        app.advance(2_000)
        app.map.navScrub(b) // the finger is back: the earlier hold is cancelled, and following was already paused
        app.advance(3_000)
        assertEquals("the first hold must not take the dot away", b, app.highlighted); assertFalse(app.map.followMe)
        app.map.navScrubEnd()
        app.advance(4_000)
        assertNull(app.highlighted); assertTrue(app.map.followMe)
        assertEquals(1, app.map.recentreTick)
        assertEquals("nothing is left waiting", 0, app.main.pending)
    }

    @Test fun theScrubDoesNotFollowAgainWhenItWasNotFollowingOrNavigateWasLeft() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE
        app.map.navScrub(a); app.map.navScrubEnd(); app.advance(4_000)
        assertFalse("it was not following before", app.map.followMe); assertNull(app.highlighted)
        app.map.followMe = true
        app.map.navScrub(a); app.map.navScrubEnd()
        app.modeNow = AppMode.PLAN
        app.advance(4_000)
        assertFalse("Plan: following does not start", app.map.followMe); assertNull(app.highlighted)
    }

    // ---------------------------------------------------------------- compass, locate, no position

    @Test fun compassWithoutAPositionSaysItNeutrallyInPlanAndWithTheErrorBoxInNavigate() {
        val app = App()
        app.map.onCompass(mapTurned = false)
        assertEquals(PlanGps.Note.TURN_NEEDS_POSITION, app.map.planGpsNote); assertEquals(1, app.map.planGpsNoteTick)
        assertNull("no red box in Plan", app.board.errorCard)
        assertFalse("orientation untouched", app.log.any { it.startsWith("headingUp") })
        app.modeNow = AppMode.NAVIGATE
        app.map.onCompass(mapTurned = false)
        assertTrue("Navigate: the error box", app.board.errorCard != null)
        assertEquals(1, app.map.planGpsNoteTick)
    }

    @Test fun compassWithAPositionSwitchesToWalkingDirectionAndFollows() {
        val app = App()
        app.fix = here
        app.map.onCompass(mapTurned = false)
        assertTrue(app.headingUpNow); assertTrue(app.map.followMe); assertEquals(1, app.map.recentreTick); assertEquals(0, app.map.northTick)
    }

    @Test fun compassOnWalkingDirectionTurnsNorthByFollowingOrByATick() {
        val app = App()
        app.fix = here; app.headingUpNow = true; app.map.followMe = true
        app.map.onCompass(mapTurned = false)
        assertFalse(app.headingUpNow); assertEquals("following turns it north itself: no second camera move", 0, app.map.northTick)
        assertEquals(1, app.map.recentreTick)
        app.headingUpNow = true; app.map.followMe = false
        app.map.onCompass(mapTurned = false)
        assertEquals("not following: the map is turned north", 1, app.map.northTick); assertEquals(1, app.map.recentreTick)
        assertFalse(app.map.followMe)
        assertEquals("the order: the orientation is stored first, then the map turns / follows",
            listOf("headingUp false [follow=true recentre=0 north=0]", "headingUp false [follow=false recentre=1 north=0]"),
            app.log.filter { it.startsWith("headingUp") })
    }

    @Test fun compassStoresTheOrientationBeforeItFollows() {
        val app = App()
        app.fix = here
        app.map.onCompass(mapTurned = false)
        assertEquals(listOf("headingUp true [follow=false recentre=0 north=0]"), app.log.filter { it.startsWith("headingUp") })
        assertTrue(app.map.followMe)
    }

    @Test fun compassOnANorthMapWhenTheMapWasTurnedTurnsItBack() {
        val app = App()
        app.map.onCompass(mapTurned = true)
        assertEquals(1, app.map.northTick); assertFalse(app.headingUpNow); assertNull(app.map.planGpsNote)
    }

    @Test fun locateFollowsAndInNavigateBringsTheStandardZoomBack() {
        val app = App()
        app.map.locate()
        assertTrue(app.map.followMe); assertNull("Plan: the user's zoom stays", app.navStartZoomNow); assertEquals(0, app.map.recentreTick)
        app.map.followMe = false
        app.modeNow = AppMode.NAVIGATE
        app.map.locate()
        assertEquals(17.0, app.navStartZoomNow!!, 0.0); assertEquals(1, app.map.recentreTick)
        app.activityNow = Activity.BIKE_TOURING
        app.map.locate()
        assertEquals(16.0, app.navStartZoomNow!!, 0.0)
    }

    @Test fun noPositionForAnEditIsANoteInPlanAndAnErrorElsewhere() {
        val app = App()
        app.map.noPositionForEdit(PlanGps.Note.ONE_POINT_NO_POSITION, Res.string.msg_one_point_no_position)
        assertEquals(PlanGps.Note.ONE_POINT_NO_POSITION, app.map.planGpsNote); assertNull(app.board.errorCard)
        app.modeNow = AppMode.NAVIGATE
        app.map.noPositionForEdit(PlanGps.Note.ONE_POINT_NO_POSITION, Res.string.msg_one_point_no_position)
        assertTrue(app.board.errorCard != null)
    }

    // ---------------------------------------------------------------- the plan at a moment

    @Test fun thePlanIsShownWholeOnceTheSheetHasSettled() {
        val app = App()
        app.route = listOf(a, b, c); app.points = listOf(a, c)
        app.map.followMe = true; app.map.freeLookAt = a
        app.map.showPlanWhole(PlanCamera.Moment.FROM_NAVIGATE)
        app.advance(WholeRoute.SETTLE_MS - 1)
        assertTrue(app.cameraCalls().isEmpty())
        app.advance(1)
        assertEquals(listOf("fit 5 [follow=false free=false]"), app.cameraCalls())
        assertTrue(problems.traced("map").any { "Plan shown whole (from_navigate, 2 points)" in it })
    }

    @Test fun oneStopIsCentredAtZoom14() {
        val app = App()
        app.points = listOf(b)
        app.map.followMe = true
        app.map.showPlanWhole(PlanCamera.Moment.TOUR_END)
        app.advance(WholeRoute.SETTLE_MS)
        assertEquals(listOf("centerOn 46.62 z14.0 [follow=false free=false]"), app.cameraCalls())
    }

    @Test fun thePlanIsNotShownAfterATouchWhenPlanWasLeftATrackIsShownOrThereIsNoPlan() {
        val cases = listOf<Pair<String, (App) -> Unit>>(
            "touch" to { it.advance(100); it.map.onMapTouch() },
            "left Plan" to { it.modeNow = AppMode.NAVIGATE },
            "track shown" to { it.shownTrack = track(a, b) },
            "no plan" to { it.points = emptyList() },
        )
        for ((why, change) in cases) {
            val app = App()
            app.points = listOf(a, c); app.route = listOf(a, b, c)
            app.advance(10)
            app.map.showPlanWhole(PlanCamera.Moment.APP_START)
            app.advance(100); change(app)
            app.advance(WholeRoute.SETTLE_MS)
            assertTrue("$why: no camera move", app.cameraCalls().isEmpty())
            assertTrue(why, problems.traced("map").any { "Plan not shown whole (app_start)" in it })
        }
    }

    // ---------------------------------------------------------------- a piece of the route

    @Test fun aPieceClosesTheSpotsStopsFollowingDrawsAndThenTheMapGoes() {
        val app = App()
        app.map.followMe = true
        app.map.showPiece(listOf(a, b), null)
        assertEquals(listOf("closeSpots [follow=true free=false]", "spotLine 2 [follow=false free=false]", "fit 2 [follow=false free=false]"),
            app.log)
        app.log.clear()
        app.map.showPiece(listOf(a, b), b to 15.0)
        assertEquals("centred when a centre is given", "centerOn 46.62 z15.0 [follow=false free=false]", app.log.last())
    }

    // ---------------------------------------------------------------- the app comes and goes

    @Test fun backInTheAppInNavigateJumpsToThePositionAndTellsTheMapDataAfterwards() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE; app.fix = here
        app.map.onLeaveApp()
        assertFalse(app.map.appOnScreen); assertEquals(listOf("left on=false"), app.log)
        app.log.clear()
        app.map.onReturnToApp()
        assertTrue(app.map.appOnScreen); assertTrue(app.map.followMe); assertEquals(1, app.map.recentreTick)
        assertEquals("the map data hears of it when the flags are set", listOf("returned plan=false [follow=true free=false] on=true"), app.log)
    }

    @Test fun backInTheAppInPlanOrWithoutAPositionLeavesTheCamera() {
        val app = App()
        app.fix = here
        app.map.onReturnToApp()
        assertFalse(app.map.followMe); assertEquals("returned plan=true [follow=false free=false] on=true", app.log.single())
        app.modeNow = AppMode.NAVIGATE; app.fix = null
        app.map.onReturnToApp()
        assertFalse(app.map.followMe); assertEquals(0, app.map.recentreTick)
    }

    @Test fun appStartCentresAgainOnlyAfterTenMinutesAndNotWhileSomethingRuns() {
        val app = App()
        app.map.startCameraDone = true; app.map.userMovedMap = true; app.map.followMe = true
        app.map.onAppStop()
        app.advance(10 * 60_000L - 1)
        app.map.onAppStart()
        assertEquals("a quick look elsewhere moves nothing", 0, app.map.startCameraGen); assertTrue(app.map.startCameraDone)
        app.advance(1)
        app.map.onAppStart()
        assertEquals(1, app.map.startCameraGen); assertFalse(app.map.startCameraDone); assertFalse(app.map.userMovedMap); assertFalse(app.map.followMe)
        // a tour runs: nothing
        app.touringNow = true; app.map.startCameraDone = true
        app.map.onAppStop(); app.advance(11 * 60_000L); app.map.onAppStart()
        assertEquals(1, app.map.startCameraGen); assertTrue(app.map.startCameraDone)
        // Navigate: nothing
        app.touringNow = false; app.modeNow = AppMode.NAVIGATE
        app.map.onAppStop(); app.advance(11 * 60_000L); app.map.onAppStart()
        assertEquals(1, app.map.startCameraGen)
    }

    @Test fun aRealStartCentresAndASharedPlaceKeepsTheCamera() {
        val app = App()
        app.elapsed = 5_000L
        app.keepUntil = 65_000L
        app.map.startCameraDone = true
        app.map.onAppStart() // never stopped = a real start, but a place was just shared
        assertEquals(0, app.map.startCameraGen); assertTrue(app.map.startCameraDone)
        app.elapsed = 65_000L
        app.map.onAppStart()
        assertEquals(1, app.map.startCameraGen); assertFalse(app.map.startCameraDone)
    }

    @Test fun backAfterAWhileWithAPlanShowsThePlanAlsoDuringATour() {
        val app = App()
        app.points = listOf(a, c); app.route = listOf(a, b, c); app.touringNow = true
        app.map.onAppStop()
        app.advance(10 * 60_000L)
        app.map.onAppStart()
        assertEquals("the start camera is not asked", 0, app.map.startCameraGen)
        assertTrue("the plan is shown once the sheet settled", app.cameraCalls().isEmpty())
        app.advance(WholeRoute.SETTLE_MS)
        assertEquals(1, app.cameraCalls().size)
        assertTrue(problems.traced("map").any { "Plan shown whole (back_in_app, 2 points)" in it })
    }

    @Test fun aRealFirstStartWithAPlanTakesTheStartCameraNotThePlanShownWhole() {
        val app = App()
        app.points = listOf(a, c); app.route = listOf(a, b, c)
        app.map.startCameraDone = true
        app.map.onAppStart() // never stopped: nothing to be "back" from
        assertEquals("the start camera runs", 1, app.map.startCameraGen); assertFalse(app.map.startCameraDone)
        app.advance(WholeRoute.SETTLE_MS)
        assertTrue("the plan is not shown whole", app.cameraCalls().isEmpty())
    }

    @Test fun wholeRouteInPlanLeavesMyPositionOutOfTheFit() {
        val app = App()
        app.followed = listOf(b, c); app.fix = here // Plan, a tour's line only
        app.map.showWholeRoute()
        assertEquals(listOf(b, c), app.fitted)
    }

    @Test fun theScrubFollowsAgainOnlyForTheScrubThatPausedFollowing() {
        val app = App()
        app.modeNow = AppMode.NAVIGATE
        app.map.followMe = true
        app.map.navScrub(a); app.map.navScrubEnd(); app.advance(4_000)
        assertTrue(app.map.followMe)
        app.map.followMe = false // the user took the map meanwhile
        app.map.navScrub(b); app.map.navScrubEnd(); app.advance(4_000)
        assertFalse("this scrub did not pause following: no jump back", app.map.followMe)
    }
}
