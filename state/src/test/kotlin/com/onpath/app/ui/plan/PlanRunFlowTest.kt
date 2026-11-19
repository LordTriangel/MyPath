package com.onpath.app.ui.plan

import com.onpath.app.domain.NotRouted
import com.onpath.app.domain.PlanStatus
import com.onpath.app.domain.plan.RouteRun
import com.onpath.app.domain.plan.RunCause
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.RouteCancel
import com.onpath.app.routing.SlowPlan
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The route run's state and what its effects do (rebuild phase 2 piece 9, first part) as flows of the REAL [PlanRunSlice]
 * with the real [RouteRun] and [RunCause]: a run starting, the long-route question (ask, yes, no), the slow-plan question,
 * Cancel, "Plan anyway", and a plan left without a route. The slice's own state at the moment each port call arrives is
 * recorded, so the ORDER of effects is proven (a stopped run is stopped before its route goes), and every port call must
 * come on the thread the slice is used on (the main thread: the fake checks it). The run's coroutine (`recalc`) is still
 * in the view model: it is a recorded call here.
 */
class PlanRunFlowTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    private val a = GeoPoint(46.60, 11.10)
    private val b = GeoPoint(46.65, 11.12)
    private val c = GeoPoint(46.70, 11.15)
    /** About 1,500 km: far beyond a day on foot. */
    private val far = GeoPoint(60.0, 11.10)

    private class App {
        private val main = Thread.currentThread()
        var points: List<GeoPoint> = emptyList()
        val causes = RunCause()
        /** What the view model was asked to do, in order, with the slice's state at that moment. */
        val log = ArrayList<String>()
        lateinit var slice: PlanRunSlice
        var job: Job? = null
        var cancel: RouteCancel? = null

        private fun onMain(what: String) { check(Thread.currentThread() === main) { "$what called off the main thread" } }
        /** True when the slice holds no run handle any more (job, router signal, wait card) – read when each call arrives. */
        private fun state() = "stopped=${slice.routeJob == null && slice.routeCancel == null && slice.routeProgress == null && slice.routeShare == null}"

        val host = object : PlanRunHost {
            override fun points(): List<GeoPoint> { onMain("points"); return points }
            override fun clearRoute() { onMain("clearRoute"); log += "clearRoute ${state()}" }
            override fun noCorridor() { onMain("noCorridor"); log += "noCorridor ${state()}" }
            override fun loadDeferredMaps(before: List<GeoPoint>) {
                onMain("loadDeferredMaps"); log += "loadMaps ${before.size} deferred=${slice.longRouteDeferred?.size}"
            }
            override fun recalc() { onMain("recalc"); log += "recalc next=${causes.next::class.simpleName} run=${causes.run::class.simpleName}" }
            override fun cancelRun(why: NotRouted) { onMain("cancelRun"); log += "cancelRun $why" }
        }

        init { slice = PlanRunSlice(causes, host) }

        /** What `recalc` does before it launches: the cause starts, the event goes in. */
        fun startRun(pts: List<GeoPoint>, act: Activity = Activity.HIKE, planMode: Boolean = true): RouteRun.Effect.StartRouting? {
            points = pts
            causes.start()
            val effects = slice.routeEvent(RouteRun.Event.Recalc(pts, act, planMode, newPlan = false))
            slice.perform(effects)
            return effects.filterIsInstance<RouteRun.Effect.StartRouting>().firstOrNull()
        }

        /** The handles `recalc` sets for a run it launched. */
        fun launchHandles() {
            job = Job().also { slice.routeJob = it }
            cancel = RouteCancel().also { slice.routeCancel = it }
            slice.routeProgress = 1 to 3; slice.routeShare = 0.4f
        }
    }

    @Test fun twoPointsStartARunAndTheStateSaysCalculating() {
        val app = App()
        val start = app.startRun(listOf(a, b))
        assertEquals(1, start?.gen); assertEquals(1, start?.rounds); assertTrue(start!!.askAtLimit)
        assertTrue(app.slice.routing)
        assertTrue(app.slice.routeRun.isCurrent(1))
        assertEquals(emptyList<String>(), app.log) // starting a run asks the app for nothing
    }

    @Test fun aNewerRunSupersedesTheOlderOne() {
        val app = App()
        val first = app.startRun(listOf(a, b))!!
        val second = app.startRun(listOf(a, b, c))!!
        assertEquals(first.gen + 1, second.gen)
        // the older run's result is told apart by its generation
        val back = app.slice.routeEvent(RouteRun.Event.Returned(first.gen, active = true))
        assertTrue(RouteRun.Effect.Superseded in back)
        assertTrue(app.slice.routing) // the newer one still runs
        assertFalse(app.slice.routeRun.isCurrent(first.gen))
    }

    @Test fun aSlowRunOffersCancelAndAFinishedOneTakesItBack() {
        val app = App()
        val gen = app.startRun(listOf(a, b))!!.gen
        assertFalse(app.slice.routingSlow)
        app.slice.routeEvent(RouteRun.Event.Slow(gen))
        assertTrue(app.slice.routingSlow)
        app.slice.routeEvent(RouteRun.Event.Returned(gen, active = true))
        assertFalse(app.slice.routingSlow)
    }

    @Test fun cancelWhileCalculatingTakesBackWhatStartedItAndCancelOfNothingDoesNothing() {
        val app = App()
        app.slice.cancelRouting()
        assertEquals(emptyList<String>(), app.log)
        app.startRun(listOf(a, b))
        app.slice.cancelRouting()
        assertEquals(listOf("cancelRun STOPPED"), app.log)
    }

    // ---------------- plan left without a route ----------------

    @Test fun aPlanLeftUnroutedStopsTheRunFirstThenTheRouteAndItsCorridorGo() {
        val app = App()
        app.startRun(listOf(a, b)); app.launchHandles()
        val job = app.job!!; val cancel = app.cancel!!
        app.points = listOf(a, b)
        app.slice.leaveUnrouted(NotRouted.STOPPED)
        // the run and its router stopped, the wait card's progress gone – all before the route is cleared (stopped=true at the call)
        assertEquals(listOf("clearRoute stopped=true", "noCorridor stopped=true"), app.log)
        assertTrue(job.isCancelled); assertTrue(cancel.isCancelled)
        assertNull(app.slice.routeJob); assertNull(app.slice.routeCancel)
        assertEquals(NotRouted.STOPPED, app.slice.notRouted)
        assertFalse(app.slice.routing)
    }

    @Test fun anUnroutedPlanIsRoutedAnywayWithoutTakingOverTheCause() {
        val app = App()
        app.points = listOf(a, b)
        app.causes.pointEdit(); app.causes.start() // the run that was left: a point edit
        app.slice.leaveUnrouted(NotRouted.STOPPED)
        app.log.clear()
        app.slice.planAnyway()
        // a new run: not "again" – its cause is what the trigger said (nothing: Other)
        assertEquals(listOf("recalc next=Other run=PointEdit"), app.log)
    }

    @Test fun planAnywayNeedsTwoPoints() {
        val app = App()
        app.points = listOf(a)
        app.slice.planAnyway()
        assertEquals(emptyList<String>(), app.log)
    }

    // ---------------- the long-route question ----------------

    @Test fun aVeryLongPlanAsksFirstAndNothingIsRoutedOrLoaded() {
        val app = App()
        val start = app.startRun(listOf(a, far))
        assertNull(start)
        assertTrue(app.slice.longRouteAsk!! > 86_400.0)
        assertFalse(app.slice.routing)
        assertEquals(listOf("clearRoute stopped=true", "noCorridor stopped=true"), app.log)
    }

    private fun answerLong(app: App, plan: Boolean) {
        val effects = app.slice.routeEvent(RouteRun.Event.LongAnswer(plan, app.points))
        app.slice.perform(effects)
    }

    @Test fun yesPlansItAnywayLoadsTheDeferredMapsOnceAndRunsForTheSameCause() {
        val app = App()
        app.causes.pointEdit()
        app.startRun(listOf(a, far))
        app.slice.longRouteDeferred = listOf(a)
        app.log.clear()
        answerLong(app, plan = true)
        // the maps first (the deferred points are handed over and forgotten), then the run again for the cause that started it
        assertEquals(listOf("loadMaps 1 deferred=null", "recalc next=PointEdit run=PointEdit"), app.log)
        assertNull(app.slice.longRouteAsk)
        // asked only once for these points
        val start = app.startRun(listOf(a, far))
        assertTrue(start != null)
        assertTrue(app.slice.routing)
    }

    @Test fun noKeepsThePointsUnroutedAndTakesBackTheChange() {
        val app = App()
        app.startRun(listOf(a, far))
        app.slice.longRouteDeferred = listOf(a)
        app.log.clear()
        answerLong(app, plan = false)
        assertEquals(listOf("cancelRun LONG"), app.log)
        assertNull(app.slice.longRouteAsk)
        assertEquals(listOf(a), app.slice.longRouteDeferred) // "Plan anyway" later still loads them
    }

    @Test fun aLongPlanLeftByCancelCanBePlannedAnywayLater() {
        val app = App()
        app.startRun(listOf(a, far))
        app.slice.longRouteDeferred = listOf(a)
        answerLong(app, plan = false)
        app.slice.leaveUnrouted(NotRouted.LONG)
        app.log.clear()
        app.slice.planAnyway()
        assertEquals(listOf("loadMaps 1 deferred=null", "recalc next=Other run=Other"), app.log)
    }

    // ---------------- the slow-plan question ----------------

    private fun slowAsked(app: App): Int {
        val gen = app.startRun(listOf(a, b))!!.gen
        app.slice.routeEvent(RouteRun.Event.TimeLimit(gen, 1))
        return gen
    }

    @Test fun theTimeLimitAsksAndWaitingOnRunsAgainForTheSameCauseWithMorePeriods() {
        val app = App()
        app.causes.pointEdit()
        slowAsked(app)
        assertEquals(1, app.slice.slowPlanAsk)
        assertFalse(app.slice.routing)
        app.slice.answerSlowPlan(SlowPlan.Answer.KEEP_WAITING)
        assertEquals(listOf("recalc next=PointEdit run=PointEdit"), app.log)
        assertNull(app.slice.slowPlanAsk)
        // the new run starts with one more period and still asks at its limit
        val again = app.startRun(listOf(a, b))!!
        assertEquals(2, again.rounds)
        assertEquals(PlanStatus.Cause.PointEdit, app.causes.run)
    }

    @Test fun cancelOfTheSlowQuestionIsTheUsualCancel() {
        val app = App()
        slowAsked(app)
        app.slice.answerSlowPlan(SlowPlan.Answer.CANCEL)
        assertEquals(listOf("cancelRun STOPPED"), app.log)
        assertNull(app.slice.slowPlanAsk)
    }

    @Test fun anAnswerWithNoQuestionOpenDoesNothing() {
        val app = App()
        app.slice.answerSlowPlan(SlowPlan.Answer.KEEP_WAITING)
        assertEquals(emptyList<String>(), app.log)
    }

    // ---------------- the stop ----------------

    @Test fun stoppingTheRunCancelsTheRouterAndTheJobAndClearsTheWaitCard() {
        val app = App()
        app.startRun(listOf(a, b)); app.launchHandles()
        val job = app.job!!; val cancel = app.cancel!!
        app.slice.perform(listOf(RouteRun.Effect.StopRun))
        assertTrue(job.isCancelled); assertTrue(cancel.isCancelled)
        assertNull(app.slice.routeJob); assertNull(app.slice.routeCancel)
        assertNull(app.slice.routeProgress); assertNull(app.slice.routeShare)
    }
}
