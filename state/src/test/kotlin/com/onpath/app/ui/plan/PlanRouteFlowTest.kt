package com.onpath.app.ui.plan

import com.onpath.app.data.AppSettings
import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.domain.AppMode
import com.onpath.app.domain.PlanStatus
import com.onpath.app.domain.SpotKind
import com.onpath.app.domain.plan.RouteRun
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlannedLeg
import com.onpath.app.routing.PlannedRoute
import com.onpath.app.routing.Rd5Parts
import com.onpath.app.routing.RouteResult
import com.onpath.app.routing.RoutingEngine
import com.onpath.app.routing.WayAttrs
import com.onpath.app.testkit.FakeRoutingEngine
import com.onpath.app.testkit.JdbcTours
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import com.onpath.app.time.Pace
import com.onpath.app.time.TimeEstimate
import com.onpath.app.time.TourTimes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * The first half of the plan's route (rebuild phase 2 piece 8) as flows of the REAL [PlanRouteSlice]: the activity / T
 * level the user picks (what is remembered, what is asked of the run and in which order, what "route changed" keeps), the
 * routing engine with its leg cache (a real new engine clears the legs, a failed start is retried), the model time of
 * the plan and of the route list (the pace, the stored groups, the cache), the route warnings (memo, the amber line, the
 * list) and the four routing threads. Only the edges are fakes: the router ([FakeRoutingEngine]), the time stores, the
 * preferences and the host that records what the view model would be told (in order) – the real tables ([JdbcTours])
 * hold the stored points. The run itself (`recalc`) is piece 9: here it is a recorded call.
 */
class PlanRouteFlowTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()
    @get:Rule val tmp = TemporaryFolder()

    private val db = JdbcTours()
    private val apps = ArrayList<App>()
    @After fun close() { apps.forEach { it.slice.close() }; db.close() }

    private val stepDeg = 10.0 / 111_195.0
    private val road = WayAttrs("quiet_road", "asphalt")
    private val steps = WayAttrs("steps", "paved")

    /** A bike route with 30 m of steps: BIKE_TREKKING warns "Steps", a hike does not. */
    private fun stepsRoute(): PlannedRoute {
        val pts = ArrayList<GeoPoint>()
        var i = 0
        for ((w, m) in listOf(road to 1000, steps to 30, road to 1000)) repeat(m / 10) { pts += GeoPoint(46.6 + i++ * stepDeg, 11.1, 500.0, w) }
        pts += GeoPoint(46.6 + i * stepDeg, 11.1, 500.0, road)
        return PlannedRoute(listOf(PlannedLeg(pts, false)), 2030.0, 10.0, 10.0, 1800.0, null)
    }

    private fun plainRoute(km: Double = 3.0): PlannedRoute {
        val n = (km * 100).toInt()
        val pts = (0..n).map { GeoPoint(46.6 + it * stepDeg, 11.1, 500.0 + it * 0.5, road) }
        return PlannedRoute(listOf(PlannedLeg(pts, false)), km * 1000, n * 0.5, 0.0, km * 720, null)
    }

    private class MemoryPrefs(override var activity: Activity = Activity.HIKE, override var sac: Int = 2) : PlanRoutePrefs

    private class FakeRouter(val legs: File) : RouterPort {
        var real = true
        var creates = 0
        var fingerprintFails = false
        val engines = ArrayList<FakeRoutingEngine>()
        override fun create(): RoutingEngine { creates++; return FakeRoutingEngine().also { engines += it } }
        override val isRealRouter: Boolean get() = real
        override fun fingerprint(): String { if (fingerprintFails) throw IllegalStateException("no assets"); return "fp1" }
        override val legsDir: File get() = legs
        /** The routing data's version: a stored leg of an older one is not fresh any more. */
        var version = "v1"
        override fun partVersion(part: Rd5Parts.Part) = version
    }

    private inner class FakeTimes : TimesStore {
        val groups = HashMap<Long, DoubleArray>()
        var puts = 0
        var gets = 0
        var failPut = false
        var failSegments = false
        var segmentsReads = 0
        val live = HashMap<Long, TourTimes>()
        override fun loadSegments(id: Long): List<List<TrackPoint>> {
            segmentsReads++
            if (failSegments) throw IllegalStateException("db closed")
            return db.loadSegments(id)
        }
        override fun planGroups(id: Long, distanceM: Double): DoubleArray? { gets++; return groups[id] }
        override fun putPlanGroups(id: Long, distanceM: Double, groups: DoubleArray) {
            if (failPut) throw java.io.IOException("disk full")
            puts++; this.groups[id] = groups
        }
        override fun tourTimes(id: Long): TourTimes? = live[id]
    }

    private inner class App(var settings: AppSettings = AppSettings()) {
        val prefs = MemoryPrefs()
        val router = FakeRouter(File(tmp.root, "legs"))
        val times = FakeTimes()
        /** What the view model was told, in order. */
        val log = ArrayList<String>()
        var route: PlannedRoute? = null
        var routing = false
        var longAsk: Double? = null
        var mode = AppMode.PLAN
        var touring = false
        var refreshes = 0
        var fitted: List<GeoPoint>? = null

        val host = object : PlanRouteHost {
            override val settings: AppSettings get() = this@App.settings
            override var route: PlannedRoute?
                get() = this@App.route
                set(v) { this@App.route = v }
            override val routing: Boolean get() = this@App.routing
            override val longRouteAsk: Double? get() = this@App.longAsk
            override val mode: AppMode get() = this@App.mode
            override val touring: Boolean get() = this@App.touring
            override fun recalc() { log += "recalc ${slice.activity.name} T${slice.sac} prefs=${prefs.activity.name}/T${prefs.sac}" }
            override fun routeEvent(e: RouteRun.Event) { log += "event ${(e as? RouteRun.Event.CompareActivity)?.let { "compare ${it.on}" } ?: e}" }
            override fun refreshTours() { refreshes++; log += "refreshTours" }
            override fun clearShownNote() { log += "clearShownNote" }
            override fun showWhole(points: List<GeoPoint>) { fitted = points; log += "showWhole" }
        }
        val slice: PlanRouteSlice = PlanRouteSlice(prefs, router, times, host)
        init { apps += this }
    }

    private fun plannedTour(activity: String? = "HIKE", km: Double = 3.0, name: String = "Tour"): Tour {
        val seg = (0..(km * 100).toInt()).map { TrackPoint(46.6 + it * stepDeg, 11.1, 500.0 + it * 0.5) }
        val id = db.insertTour(TourKind.PLANNED, name, activity, 2, listOf(seg), distance = km * 1000, duration = 99.0)
        return db.getTour(id)!!
    }

    // ---------------- activity / T level ----------------

    @Test fun anOwnActivityChangeInPlanIsRememberedThenThePlanRunsAgainAndSaysNothing() {
        val app = App().also { it.route = plainRoute() }
        app.slice.routeChangedNotice = "old notice"
        app.slice.selectActivity(Activity.BIKE_TREKKING)
        assertEquals(Activity.BIKE_TREKKING, app.slice.activity)
        assertEquals(Activity.BIKE_TREKKING, app.prefs.activity)
        // the comparison goes (the user changed it themselves), the run starts with the new activity already stored, then the note goes
        assertEquals(listOf("event compare false", "recalc BIKE_TREKKING T2 prefs=BIKE_TREKKING/T2", "clearShownNote"), app.log)
        assertNull(app.slice.routeChangedNotice)
        assertNull(app.slice.activityBefore)
        // Cancel while it is calculated takes back exactly this change: the activity from before
        assertEquals(PlanStatus.Cause.ActivityChange(Activity.HIKE, 2), app.slice.runCauses.next)
    }

    @Test fun anActivityChangeDuringATourKeepsTheRouteBeforeForTheComparison() {
        val app = App().also { it.route = plainRoute(); it.touring = true }
        val before = app.route
        app.slice.selectActivity(Activity.BIKE_TREKKING)
        assertSame(before, app.slice.activityBefore)
        assertEquals(listOf("event compare true", "recalc BIKE_TREKKING T2 prefs=BIKE_TREKKING/T2", "clearShownNote"), app.log)
        // a second change while that one is pending keeps the first route (the comparison is with what the user saw)
        app.route = plainRoute(4.0)
        app.slice.selectActivity(Activity.MTB)
        assertSame(before, app.slice.activityBefore)
    }

    @Test fun aChangeWhileTheRouteIsStillCalculatedHasNothingToCompareWith() {
        val app = App().also { it.route = plainRoute(); it.touring = true; it.routing = true }
        app.slice.selectActivity(Activity.BIKE_TREKKING)
        assertNull(app.slice.activityBefore)
        assertEquals("event compare false", app.log.first())
    }

    @Test fun anActivityTakenOverFromElsewhereComparesAndCancelOnlyStops() {
        val app = App().also { it.route = plainRoute() }
        app.slice.selectActivity(Activity.MTB, userChange = false)
        assertNotNull(app.slice.activityBefore) // from elsewhere: "New route for … – check it" even in Plan
        assertEquals("event compare true", app.log.first())
        assertEquals(PlanStatus.Cause.Other, app.slice.runCauses.next) // Cancel then only stops
        assertEquals(Activity.MTB, app.prefs.activity)
    }

    @Test fun aSecondChangeWhileTheFirstIsOpenKeepsTheActivityFromBeforeTheFirst() {
        val app = App()
        app.slice.selectActivity(Activity.BIKE_TREKKING)
        app.slice.runCauses.start() // the run of the first change begins
        app.routing = true
        app.slice.selectActivity(Activity.MTB)
        assertEquals(PlanStatus.Cause.ActivityChange(Activity.HIKE, 2), app.slice.runCauses.next)
        // a very long plan that waits for its answer counts as open too
        val other = App()
        other.slice.selectActivity(Activity.BIKE_TREKKING); other.slice.runCauses.start(); other.longAsk = 90_000.0
        other.slice.selectActivity(Activity.MTB)
        assertEquals(PlanStatus.Cause.ActivityChange(Activity.HIKE, 2), other.slice.runCauses.next)
        // finished: the next change takes back the one before it
        val done = App()
        done.slice.selectActivity(Activity.BIKE_TREKKING); done.slice.runCauses.start()
        done.slice.selectActivity(Activity.MTB)
        assertEquals(PlanStatus.Cause.ActivityChange(Activity.BIKE_TREKKING, 2), done.slice.runCauses.next)
    }

    @Test fun theTLevelIsClampedRememberedAndOnlyAHikeRoutesAgain() {
        val hike = App()
        hike.slice.selectSac(2)
        assertEquals(emptyList<String>(), hike.log) // the same level: nothing at all
        hike.slice.selectSac(9)
        assertEquals(5, hike.slice.sac); assertEquals(5, hike.prefs.sac)
        assertEquals(listOf("event compare false", "recalc HIKE T5 prefs=HIKE/T5", "clearShownNote"), hike.log)
        hike.log.clear()
        hike.slice.selectSac(-3)
        assertEquals(1, hike.slice.sac)
        assertEquals(PlanStatus.Cause.ActivityChange(Activity.HIKE, 5), hike.slice.runCauses.next)
        // a bike has no T level: stored, no run, no "changed" comparison
        val bike = App().also { it.prefs.activity = Activity.MTB }
        val bikeSlice = PlanRouteSlice(bike.prefs, bike.router, bike.times, bike.host)
        bikeSlice.selectSac(4)
        assertEquals(4, bike.prefs.sac)
        assertEquals(listOf("clearShownNote"), bike.log)
    }

    @Test fun theLastChoiceIsWhereTheNextStartBeginsAndAStoredNameIsReadBack() {
        val prefs = MemoryPrefs(Activity.MTB, 4)
        val app = App()
        val slice = PlanRouteSlice(prefs, app.router, app.times, app.host)
        assertEquals(Activity.MTB, slice.activity); assertEquals(4, slice.sac)
        assertEquals(Activity.EBIKE, slice.activityOrNull("EBIKE"))
        assertNull(slice.activityOrNull("HELICOPTER"))
        assertNull(slice.activityOrNull(null))
    }

    // ---------------- engine, leg cache ----------------

    private val a = GeoPoint(46.60, 11.10)
    private val b = GeoPoint(46.61, 11.10)
    private fun leg() = RouteResult(listOf(a, b), 1000.0, 5.0, 5.0, 800.0)

    @Test fun aRealNewEngineIsKeptAndDropsTheLegsOfTheOldOne() {
        // (the stored copy of a leg is judged by the data version; what the engine drops is the copy in memory)
        val app = App()
        app.slice.legCache.put(a, b, Activity.HIKE, 2, leg())
        app.router.version = "v2" // new routing data arrived: only the memory still has the old leg
        assertNotNull(app.slice.legCache.get(a, b, Activity.HIKE, 2))
        val first = app.slice.routingEngine()
        assertNull("a new engine = new data: the old legs are routed again", app.slice.legCache.get(a, b, Activity.HIKE, 2))
        // kept: the same one again, nothing is created, nothing is cleared
        app.slice.legCache.put(a, b, Activity.HIKE, 2, leg())
        app.router.version = "v3"
        assertSame(first, app.slice.routingEngine())
        assertEquals(1, app.router.creates)
        assertNotNull(app.slice.legCache.get(a, b, Activity.HIKE, 2))
        // new routing data: the engine goes, the next call makes a new one and drops the legs again
        app.slice.engine = null
        val second = app.slice.routingEngine()
        assertNotSame(first, second)
        assertEquals(2, app.router.creates)
        assertNull(app.slice.legCache.get(a, b, Activity.HIKE, 2))
    }

    @Test fun aFailedStartIsNotKeptAndIsTriedAgainWithoutDroppingTheLegs() {
        val app = App().also { it.router.real = false } // the straight-line stand-in
        app.slice.legCache.put(a, b, Activity.HIKE, 2, leg())
        val stand = app.slice.routingEngine()
        assertNull(app.slice.engine)
        assertNotNull(app.slice.legCache.get(a, b, Activity.HIKE, 2))
        app.router.real = true
        val real = app.slice.routingEngine()
        assertNotSame(stand, real)
        assertEquals(2, app.router.creates)
        assertSame(real, app.slice.engine)
    }

    @Test fun legsAreStoredWithTheFingerprintAndAnUnreadableFingerprintIsReportedNotFatal() {
        val ok = App()
        ok.slice.legCache.put(a, b, Activity.HIKE, 2, leg())
        assertTrue("a leg file is written", File(ok.router.legs, "").walkTopDown().any { it.isFile })
        assertTrue(problems.none("routing"))
        val bad = App().also { it.router.fingerprintFails = true }
        bad.slice.legCache.put(a, b, Activity.HIKE, 2, leg())
        assertTrue(problems.said("routing").any { it.contains("Routing fingerprint not computed") })
        assertNotNull("the leg is still kept in memory", bad.slice.legCache.get(a, b, Activity.HIKE, 2))
    }

    @Test fun theFourRoutingThreadsStopWithTheSlice() {
        val app = App()
        val t = app.slice.threads
        val all = listOf(t.routing, t.background, t.check, t.hold)
        assertEquals("four different threads", 4, all.map { it.executor }.toSet().size)
        assertTrue(all.none { (it.executor as ExecutorService).isShutdown })
        app.slice.close()
        assertTrue(all.all { (it.executor as ExecutorService).isShutdown })
    }

    // ---------------- time estimate ----------------

    @Test fun thePlanTimeFollowsThePaceSettingAndRetimeKeepsTheRouteButNotItsNumber() {
        val app = App().also { it.route = plainRoute(5.0) }
        val route = app.route!!
        val average = app.slice.planFactors()
        app.settings = AppSettings(hikePace = Pace.RELAXED)
        val relaxed = app.slice.planFactors()
        assertEquals(TimeEstimate.planFactors(Activity.HIKE, app.settings), relaxed)
        assertTrue(relaxed != average)
        app.slice.retime()
        val retimed = app.route!!
        assertEquals(route.points, retimed.points)
        assertEquals(TimeEstimate.planSeconds(route.points, Activity.HIKE, relaxed), retimed.durationS, 0.0)
        assertTrue(retimed.durationS != route.durationS)
        assertEquals("the list is read again", 1, app.refreshes)
        // no route (calculating / none): nothing to retime, the list is read again all the same
        app.route = null
        app.slice.retime()
        assertNull(app.route)
        assertEquals(2, app.refreshes)
    }

    @Test fun aSavedRoutesTimesAreStoredPerGroupOnlyForARealLineAndAFailureIsReported() {
        val app = App()
        app.slice.storePlanTimes(7, null, Activity.HIKE)
        app.slice.storePlanTimes(7, PlannedRoute(emptyList(), 0.0, 0.0, 0.0, 0.0, null), Activity.HIKE)
        assertEquals(0, app.times.puts)
        app.slice.storePlanTimes(7, plainRoute(), Activity.HIKE)
        assertEquals(1, app.times.puts)
        assertNotNull(app.times.groups[7L])
        app.times.failPut = true
        app.slice.storePlanTimes(8, plainRoute(), Activity.HIKE)
        assertNull(app.times.groups[8L])
        assertTrue(problems.said("time").any { it.contains("Plan times not stored") })
    }

    @Test fun aPlannedRoutesListTimeComesFromItsStoredGroupsThenFromTheCacheAndNewPaceMakesItNew() {
        val app = App()
        val t = plannedTour()
        app.slice.storePlanTimes(t.id, plainRoute(3.0), Activity.HIKE) // what Save does
        val shown = app.slice.withModelTime(t)
        val expected = com.onpath.app.time.PlanTimesStore.seconds(app.times.groups[t.id]!!, app.slice.planFactors(Activity.HIKE))
        assertEquals(expected, shown.durationS, 0.0)
        assertTrue("not the stored 99 s", shown.durationS != 99.0)
        val gets = app.times.gets
        assertEquals(expected, app.slice.withModelTime(t).durationS, 0.0)
        assertEquals("the second time comes from the cache", gets, app.times.gets)
        // a new pace → a new number; an overwritten route (new distance) → computed again
        app.settings = AppSettings(hikePace = Pace.FIT)
        val fit = app.slice.withModelTime(t).durationS
        assertTrue(fit < expected)
        assertEquals(gets + 1, app.times.gets)
        assertEquals(1, app.slice.plannedTimes.size)
        app.slice.plannedTimes.remove(t.id) // what Save's "forget" does
        assertEquals(fit, app.slice.withModelTime(t).durationS, 0.0)
        assertEquals(gets + 2, app.times.gets)
    }

    @Test fun aPlannedRouteWithoutStoredGroupsIsTimedFromItsPointsAndAFailureShowsTheStoredTime() {
        val app = App()
        val t = plannedTour(km = 3.0)
        val f = app.slice.planFactors(Activity.HIKE)
        val pts = db.loadSegments(t.id).flatten().map { it.toGeo() }
        assertEquals(TimeEstimate.planSeconds(pts, Activity.HIKE, f), app.slice.withModelTime(t).durationS, 0.0)
        // one point: no line to time, the stored time stays
        val tiny = db.getTour(db.insertTour(TourKind.PLANNED, "tiny", "HIKE", 2, listOf(listOf(TrackPoint(46.6, 11.1))), duration = 55.0))!!
        assertEquals(55.0, app.slice.withModelTime(tiny).durationS, 0.0)
        // unreadable points: reported, the stored time stays
        val broken = App().also { it.times.failSegments = true }
        assertEquals(99.0, broken.slice.withModelTime(t).durationS, 0.0)
        assertTrue(problems.said("time").any { it.contains("Model time of a planned route could not be computed") })
    }

    @Test fun anImportedTrackIsTimedByItsFileAndARecordedTourKeepsItsStoredTime() {
        val app = App()
        val seg = listOf(TrackPoint(46.6, 11.1, 500.0, 1_000), TrackPoint(46.601, 11.1, 500.0, 61_000), TrackPoint(46.602, 11.1, 500.0, 121_000))
        val imported = db.getTour(db.insertTour(TourKind.IMPORTED, "gpx", "HIKE", 2, listOf(seg), distance = 220.0, duration = 5.0))!!
        assertEquals(TimeEstimate.importedSeconds(listOf(seg), Activity.HIKE, app.slice.planFactors(Activity.HIKE)),
            app.slice.withModelTime(imported).durationS, 0.0)
        val recorded = db.getTour(db.insertTour(TourKind.RECORDED, "rec", "HIKE", 2, listOf(seg), duration = 4_000.0))!!
        assertSame(recorded, app.slice.withModelTime(recorded))
        assertEquals("no stored points read for it", 1, app.times.segmentsReads)
        // an unknown activity name reads as Hike
        val odd = plannedTour(activity = "HELICOPTER")
        app.slice.storePlanTimes(odd.id, plainRoute(3.0), Activity.HIKE)
        assertEquals(com.onpath.app.time.PlanTimesStore.seconds(app.times.groups[odd.id]!!, app.slice.planFactors(Activity.HIKE)),
            app.slice.withModelTime(odd).durationS, 0.0)
    }

    @Test fun aFinishedToursTimesAreTheServicesThenTheStoredPointsAndAFailureIsReportedAsNone() {
        val app = App()
        val t = plannedTour()
        val live = TourTimes.of(3_600.0, 300.0)
        app.times.live[t.id] = live
        assertSame(live, app.slice.tourTimes(t))
        app.times.live.clear()
        val computed = app.slice.tourTimes(t)
        assertNotNull(computed) // recomputed from the stored points
        app.times.failSegments = true
        assertNull(app.slice.tourTimes(t))
        assertTrue(problems.said("time").any { it.contains("Times of a finished tour could not be computed") })
    }

    @Test fun timeDetailsOpenOnlyWithARouteAndCarryThePaceAndTheKind() {
        val app = App(AppSettings(hikePace = Pace.EASY))
        app.slice.openTimeDetails()
        assertNull(app.slice.timeDetails)
        app.route = plainRoute()
        app.slice.openTimeDetails(technical = true)
        val d = app.slice.timeDetails!!
        assertTrue(d.technical)
        assertEquals(Pace.EASY.label, d.paceLabel)
        assertEquals(Pace.EASY.factors(Activity.HIKE).flat, d.paceFactor, 1e-9)
        app.slice.openTimeDetails()
        assertFalse(app.slice.timeDetails!!.technical)
    }

    // ---------------- route warnings ----------------

    @Test fun spotsAreWorkedOutForTheRouteOnlyWhenItIsNotBeingCalculatedAndFollowTheActivity() {
        val app = App().also { it.route = stepsRoute() }
        app.prefs.activity = Activity.BIKE_TREKKING
        val bike = App().also { it.route = app.route }
        val s = PlanRouteSlice(MemoryPrefs(Activity.BIKE_TREKKING, 2), bike.router, bike.times, bike.host)
        assertEquals(listOf(SpotKind.STEPS), s.routeSpots.map { it.kind })
        assertEquals("Steps", s.warningLineText())
        assertEquals(1, s.warningCount())
        assertSame("remembered for these points, activity and level", s.routeSpots, s.routeSpots)
        bike.routing = true
        assertTrue(s.routeSpots.isEmpty())
        assertNull(s.warningLineText())
        bike.routing = false
        // the same line for a hike: steps are normal on foot – and the memo does not hold on to the bike's answer
        s.selectActivity(Activity.HIKE)
        assertTrue(s.routeSpots.isEmpty())
        assertNull(s.warningLineText())
        assertEquals(0, s.warningCount())
    }

    @Test fun theAmberLineOpensTheListOfSpotsOrFitsTheRouteAndAlwaysDropsTheNotice() {
        val app = App().also { it.route = stepsRoute() }
        val s = PlanRouteSlice(MemoryPrefs(Activity.BIKE_TREKKING, 2), app.router, app.times, app.host)
        s.routeChangedNotice = "New route for gravel bike – check it"
        assertEquals("New route for gravel bike – check it", s.routeChangedLineText())
        s.tapWarningLine()
        assertNull(s.routeChangedLineText())
        assertTrue(s.spotsOpen)
        assertNull(app.fitted)
        s.closeSpots()
        assertFalse(s.spotsOpen)
        // nothing to list: the map shows the whole route
        val hike = App().also { it.route = stepsRoute() }
        hike.slice.routeChangedNotice = "x"
        hike.slice.tapWarningLine()
        assertFalse(hike.slice.spotsOpen)
        assertNull(hike.slice.routeChangedNotice)
        assertEquals(hike.route!!.points, hike.fitted)
        // no route at all: the notice goes, nothing else happens
        val none = App()
        none.slice.routeChangedNotice = "x"
        none.slice.tapWarningLine()
        assertNull(none.slice.routeChangedNotice)
        assertEquals(emptyList<String>(), none.log)
    }

    @Test fun tappingTheRouteChangedLineDropsItAndFitsTheRoute() {
        val app = App().also { it.route = plainRoute() }
        app.slice.routeChangedNotice = "New route for … – check it"
        app.slice.tapRouteChangedLine()
        assertNull(app.slice.routeChangedNotice)
        assertEquals(app.route!!.points, app.fitted)
        val none = App()
        none.slice.routeChangedNotice = "x"
        none.slice.tapRouteChangedLine()
        assertNull(none.slice.routeChangedNotice)
        assertNull(none.fitted)
    }

    @Test fun aPlanEditDropsTheChangedNoticeThePendingComparisonAndTheBrowserNote() {
        val app = App().also { it.route = plainRoute(); it.touring = true }
        app.slice.selectActivity(Activity.MTB)
        app.slice.routeChangedNotice = "New route"
        app.slice.syncRoutingNote = "Planned in the browser with other routing"
        app.log.clear()
        app.slice.planChanged()
        assertNull(app.slice.routeChangedNotice)
        assertNull(app.slice.activityBefore)
        assertNull(app.slice.syncRoutingNote)
        assertEquals(listOf("event compare false"), app.log)
    }
}
