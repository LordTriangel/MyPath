package com.onpath.app.ui.tour

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.domain.plan.RouteRun
import com.onpath.app.domain.plan.RouteRun.Effect
import com.onpath.app.domain.plan.RouteRun.Event
import com.onpath.app.domain.SplitRequests
import com.onpath.app.navigation.ActivityCheck
import com.onpath.app.navigation.FollowStore
import com.onpath.app.navigation.NavKind
import com.onpath.app.navigation.NavSession
import com.onpath.app.navigation.Trace.line
import com.onpath.app.navigation.Trace.pt
import com.onpath.app.navigation.Trace.walk
import com.onpath.app.recording.RecordingWire
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.testkit.FakeNavService
import com.onpath.app.testkit.FakeRecordingClient
import com.onpath.app.testkit.FakeRoutingEngine
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The pure parts of the tour's contract with the recording service, provable before the tour code leaves the view model
 * (docs/design/umbau-phase-2-tour.md §4.1, piece 16a): what the app writes to disk is what the service reads when the
 * command arrives (rule 1 – a command is handled a main-loop turn LATER, so a write after it is too late), a guidance
 * that ended in the app is not brought back by a late result of the old route (rules 1, 19), the hand-over file of an
 * activity proposal (rule 1), what holds a new tour back while an interrupted one is asked about (rule 5), the delete
 * decision of the end (rule 10) and the route run's stale results (rules 16, 24, 25).
 *
 * Real classes ([FollowStore] on a temp folder, [FakeNavService] with the real Navigator and re-router, [TourController],
 * [RouteRun], [SplitRequests]); fakes only at the edges – the recording client records what is on disk AT THE MOMENT a
 * command is sent ([FakeRecordingClient.onSend]), the service's main loop and routing thread are [ManualExecutor]s
 * the test steps.
 *
 * What these tests are NOT: they do not run the view model's code. The app side is modelled here (`appFollows` writes the
 * files, then sends); what is proved is [FollowStore], the service's one-turn delay ([FakeNavService]) and the client's
 * commands. The view model's own ORDER is held by the source pins (TourOrderPinsTest) and, after piece 18a, by the flows
 * on the real slice. Confirmed by the review of piece 16a: the service has NO version check – it reads whatever
 * `nav-session.json` holds at the command's turn – so the write must stay synchronous on main, before the send.
 */
class TourContractFlowTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val problems = ProblemsRule()
    /** TourController holds Compose state (OwnComposeSnapshot says why). */
    @get:Rule val composeState = OwnComposeSnapshot()

    private val client = FakeRecordingClient()
    private val sessionFile get() = File(tmp.root, NavSession.FILE)
    private val proposalFile get() = File(tmp.root, ActivityCheck.PROPOSAL_FILE)
    private val store get() = FollowStore(tmp.root)

    /** What was on disk when a command went out. */
    private class AtSend(val action: String, val followed: FollowStore.Followed, val proposalOnDisk: Boolean)
    private val atSend = ArrayList<AtSend>()

    private fun plan(token: Long, vararg corners: Pair<Double, Double>): NavSession =
        NavSession.start(NavKind.PLAN, "Plan $token", Activity.HIKE, 3, line(*corners), corners.map { pt(it.first, it.second) })
            .copy(token = token, reached = true)

    private val route1 = arrayOf(0.0 to 0.0, 1000.0 to 0.0, 1000.0 to 1000.0)
    private val route2 = arrayOf(0.0 to 0.0, 1000.0 to 0.0, 2000.0 to 0.0)

    /** The app's side of "the tour follows [s]" as the view model does it: both files, then the command. */
    private fun appFollows(s: NavSession, checkNow: Boolean? = null) {
        store.appFollows(s, s.name, s.active)
        client.followChanged(checkNow)
    }

    /** The service on the app's folder; every FOLLOW_CHANGED the client is sent reaches its main loop (queued, not run). */
    private fun serviceOn(engine: FakeRoutingEngine = FakeRoutingEngine()): FakeNavService {
        val service = FakeNavService(sessionFile, engine)
        client.onSend = { cmd ->
            atSend += AtSend(cmd.action, store.load(), proposalFile.isFile)
            if (cmd.action == RecordingWire.ACTION_FOLLOW_CHANGED) service.followChanged()
        }
        return service
    }

    // ---- rule 1: the files before the command ----

    /** Proves the fake service's one-turn delay and [FollowStore] – not the view model's order (the pins and, after 18a, the flows do that). */
    @Test fun theSessionWrittenBeforeTheCommandIsTheOneTheServiceNavigates() {
        val service = serviceOn()
        appFollows(plan(1, *route1))
        service.main.runAll()
        assertEquals(1L, service.navigator!!.session.token)
        atSend.clear()

        appFollows(plan(2, *route2), checkNow = true)
        val sent = atSend.single()
        assertEquals("FOLLOW_CHANGED", sent.action.substringAfterLast('.'))
        assertEquals("the command goes out with the new session already on disk", 2L, sent.followed.session!!.token)
        assertEquals("… and its line and name under it", "Plan 2", sent.followed.follow!!.first)
        assertEquals(true, client.sent.last().extras[RecordingWire.EXTRA_CHECK_NOW])
        assertEquals("a command is handled a main-loop turn later, not inside the call", 1L, service.navigator!!.session.token)

        service.main.runAll()
        assertEquals(2L, service.navigator!!.session.token)
        assertEquals("Plan 2", service.navigator!!.session.name)
    }

    /** Documents the hazard behind rule 1 and proves NOTHING about production code (the assertions hold for any app code). */
    @Test fun aWriteOneTurnLateLeavesTheServiceOnTheOldSessionUntilTheNextCommand() {
        val service = serviceOn()
        appFollows(plan(1, *route1)); service.main.runAll()

        client.followChanged()                    // the command first …
        service.main.runAll()                     // … the service's turn reads the files: still session 1
        val late = plan(2, *route2)
        store.appFollows(late, late.name, late.active) // … and only now the write: the reason for rule 1
        assertEquals("the file holds the new session", 2L, store.loadSession()!!.token)
        assertEquals("the service follows the old one", 1L, service.navigator!!.session.token)
        service.main.runAll()
        assertEquals("nothing tells it to look again", 1L, service.navigator!!.session.token)

        client.followChanged(); service.main.runAll()
        assertEquals(2L, service.navigator!!.session.token)
    }

    // ---- rules 1, 19: a guidance that ended is not brought back by an old route ----

    /** A tour follows route 1, is 150 m past its corner, took the way back: the new way is being routed. Returns the engine. */
    private fun reRouteBeingRouted(service: FakeNavService) {
        appFollows(plan(1, *route1)); service.main.runAll()
        walk(0.0 to 0.0, 1000.0 to 0.0, 1150.0 to 0.0).forEach { service.fix(it) }
        service.main.runAll()
        assertEquals("one re-route waits on the routing thread", 1, service.routing.size)
        service.routing.runAll(); service.main.runAll()        // the question for the old line is open
        service.take(); service.main.runAll()                    // "Take new route": its way is being routed …
        assertEquals(1, service.routing.size)
    }

    @Test fun theResultOfTheOldRouteLandingBeforeTheCommandIsDroppedAndNothingComesBack() {
        val service = serviceOn()
        reRouteBeingRouted(service)
        atSend.clear()
        store.appEnds()                                          // clearFollow: the files first …
        service.routing.runAll(); service.main.runAll()          // … the old line's result arrives before the command's turn
        assertEquals("the conditional write found another state of the file and wrote nothing", 1, service.dropped)
        client.followChanged()
        assertEquals("the command goes out with both files gone", FollowStore.Followed(null, null), atSend.single().followed)
        service.settle()
        assertFalse("the removed route does not come back", sessionFile.exists())
        assertEquals(FollowStore.Followed(null, null), store.load())
        assertNull(service.navigator)
    }

    @Test fun theCommandLandingBeforeTheResultOfTheOldRouteEndsTheGuidanceAndTheResultFindsNobody() {
        val service = serviceOn()
        reRouteBeingRouted(service)
        store.appEnds(); client.followChanged()
        service.settle()
        assertNull("the service stopped following", service.navigator)
        assertFalse(sessionFile.exists())
        assertEquals(FollowStore.Followed(null, null), store.load())
        assertEquals("the late result was ignored, nothing to drop", 0, service.dropped)
    }

    // ---- rule 1: the proposal file before PROPOSE_SWITCH ----

    @Test fun aProposalIsOnDiskWhenTheCommandGoesOutAndIsHandedOverOnce() {
        serviceOn()
        val proposed = plan(7, *route2)
        store.propose(proposed)
        client.proposeSwitch(1234.5)
        val sent = atSend.single()
        assertEquals("PROPOSE_SWITCH", sent.action.substringAfterLast('.'))
        assertTrue("the hand-over file exists at the command", sent.proposalOnDisk)
        assertEquals(1234.5, client.sent.last().extras[RecordingWire.EXTRA_TIME_S])
        assertNull("the proposal is not the followed session", store.loadSession())

        assertEquals(7L, store.takeProposal()!!.token)
        assertFalse("the file is gone after the take", proposalFile.exists())
        assertNull("a second take finds nothing", store.takeProposal())
        assertNull("a take without any file finds nothing", FollowStore(tmp.newFolder()).takeProposal())
    }

    // ---- rule 5: nothing starts beside an interrupted tour, at every step of the start-up sequence ----

    private val interrupted = Tour(7L, TourKind.RECORDED, "Interrupted", 0L, "HIKE", 2, 1200.0, 30.0, 10.0, 900.0, "active", emptyList())

    @Test fun showUnfinishedBeforeTheFlagClearsHoldsEveryStartAtEveryStep() {
        val c = TourController(tourRecorder(client))
        val steps = ArrayList<String>()
        fun tryStart(step: String) { steps += "$step: canStartNew=${c.canStartNew}"; c.start("HIKE", 2) }

        c.unfinishedPending = true; tryStart("pending set")
        c.showUnfinished(interrupted, emptyList()); tryStart("question shown, check not over")
        c.unfinishedPending = false; tryStart("flag cleared, question open")
        assertTrue("held at every step: ${steps.joinToString()}", steps.all { it.endsWith("canStartNew=false") })
        assertEquals("not one START was sent", emptyList<String>(), client.actions)

        c.unfinishedRides = interrupted to com.onpath.app.rides.RideView.Summary(emptyList(), emptyList(), emptyList(),
            com.onpath.app.rides.RideChoice.entries.first(), emptyMap())
        c.unfinished = null
        assertFalse("the ride summary of the question is still the question", c.canStartNew)
        c.start("HIKE", 2)
        assertEquals(emptyList<String>(), client.actions)

        c.closeUnfinished()
        assertTrue(c.canStartNew)
        c.start("HIKE", 2)
        assertEquals(listOf("START"), client.actions)
    }

    // ---- rule 10: the delete decision is taken once ----

    @Test fun theDeleteDecisionIsTakenOnceAndTheNextEndSavesEvenWhenNotShort() {
        val c = TourController(tourRecorder(client))
        c.end(delete = true); c.stop()
        assertEquals(TourController.Finished.DELETED, c.finishedOutcome(short = false))
        assertEquals("the next tour is not binned", TourController.Finished.SAVED, c.finishedOutcome(short = false))
        c.end(delete = true)
        assertEquals("a delete outranks the short rule, once", TourController.Finished.DELETED, c.finishedOutcome(short = true))
        assertEquals(TourController.Finished.SHORT, c.finishedOutcome(short = true))
    }

    @Test fun endingToSaveAfterEndingToDeleteResetsTheDecision() {
        val c = TourController(tourRecorder(client))
        c.end(delete = true); c.end(delete = false)
        assertEquals(TourController.Finished.SAVED, c.finishedOutcome(short = false))
        c.end(delete = true); c.end(delete = false)
        assertEquals("a short recording is still binned with its box, not as a delete", TourController.Finished.SHORT, c.finishedOutcome(short = true))
    }

    // ---- rules 16, 24, 25: stale results of the route run ----

    private val a = GeoPoint(51.16, 6.93)
    private val b = GeoPoint(51.20, 6.95)
    private val c3 = GeoPoint(51.25, 7.00)

    private fun RouteRun.after(vararg events: Event): RouteRun.Step = events.fold(RouteRun.Step(this)) { step, e -> step.state.on(e) }
    private fun recalc(pts: List<GeoPoint>) = Event.Recalc(pts, Activity.HIKE, planMode = true, newPlan = false)
    private fun ok(gen: Int) = Event.Succeeded(gen, planMode = true, sheetCanRise = true)

    @Test fun aStaleRunNeverYieldsAFollowOfTheEditedPlan() {
        val first = RouteRun().on(recalc(listOf(a, b))).state                       // run 1
        val edited = first.after(Event.PlanEdited, recalc(listOf(a, b, c3)))          // run 2 supersedes it
        val late = edited.state.on(ok(1))                                           // run 1's result lands late
        assertEquals("nothing of the stale run is taken or followed", emptyList<Effect>(), late.effects)
        assertTrue("the edit still waits", late.state.editPending)
        val good = late.state.on(ok(2))
        assertEquals("the newest run's result follows the edit", Effect.FollowEditedPlan, good.effects.last())
        assertFalse(good.state.editPending)
    }

    @Test fun aSwitchedActivityAdoptsTheBaselineAndSuppressesTheFollowOfTheEdit() {
        val running = RouteRun().on(recalc(listOf(a, b))).state
        val step = running.after(Event.AdoptBaseline, Event.PlanEdited, ok(1))
        assertTrue("the baseline is adopted", step.effects.filterIsInstance<Effect.TakeRoute>().single().adoptBaseline)
        assertTrue("no follow of an edit", step.effects.none { it == Effect.FollowEditedPlan })
    }

    @Test fun followNextWinsOverAnEditAndTheEditWaits() {
        val running = RouteRun().on(recalc(listOf(a, b))).state
        val step = running.after(Event.FollowNext, Event.PlanEdited, ok(1))
        assertEquals(listOf(Effect.FollowNewRoute), step.effects.drop(1))
        assertTrue("the edit is not followed twice: it waits for the next good run", step.state.editPending)
    }

    @Test fun aSplitRequestIsStaleAfterANewerOneAfterInvalidateAndForAnotherPlan() {
        val r = SplitRequests()
        val plan = listOf(a, b, c3)
        val first = r.next(plan)
        assertTrue(r.isCurrent(first, plan))
        val second = r.next(plan)
        assertFalse("a newer request makes the older stale", r.isCurrent(first, plan))
        assertTrue(r.isCurrent(second, plan))
        assertFalse("another plan", r.isCurrent(second, listOf(a, c3)))
        r.invalidate()
        assertFalse("after invalidate (edit, Undo, clear, end of the guidance)", r.isCurrent(second, plan))
    }
}
