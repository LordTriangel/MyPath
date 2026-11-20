package com.onpath.app.ui.routes

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.debug.Problems
import com.onpath.app.gpx.GpxWaypoint
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlannedRoute
import com.onpath.app.testkit.JdbcTours
import com.onpath.app.testkit.ManualExecutor
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Zuletzt and the recycle bin as the user's actions ([RecentPlans], cleanup piece 4) against the real tables
 * ([JdbcTours]): what the view model's source text was pinned for before – the draft is written before the plan
 * leaves the map, the name follows – is checked here as behaviour, with the failures (a full database, a tour gone).
 * Everything runs on the test thread (an unconfined scope), so the order of the writes is the order of the lines – but
 * the core rule "written before the plan leaves the map" is checked with dispatchers that run only on demand.
 */
class RecentPlansTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    private val db = JdbcTours()
    @After fun close() = db.close()

    private val store = object : RoutesStore {
        override fun listTours() = db.listTours()
        override fun listDeleted() = db.listDeleted()
        override fun setDraft(id: Long, draft: Boolean) = db.setDraft(id, draft)
        override fun setDeleted(id: Long, deletedMs: Long?) = db.setDeleted(id, deletedMs)
        override fun loadSegments(id: Long) = db.loadSegments(id)
        override fun deleteForGood(id: Long) = db.deleteTour(id)
        override fun rename(id: Long, name: String) = db.rename(id, name)
    }

    /** The view model as the controller sees it: what it was asked, in order. */
    private inner class FakeHost : RecentPlansHost {
        val log = ArrayList<String>()
        var refreshes = 0
        var problem: String? = null
        var created = 1_000L
        /** The place name: answered when the test says so (null = no name fits). */
        var name = CompletableDeferred<String?>()
        var neverNames = false
        override fun insertDraft(row: RecentPlans.DraftRow): Long =
            db.insertTour(TourKind.PLANNED, row.name, row.activity, row.sac, row.segments, row.waypoints, distance = row.distanceM,
                createdMs = created++, draft = true)
        var updateFails = false
        override fun updateDraft(id: Long, row: RecentPlans.DraftRow): Boolean =
            if (updateFails) throw IllegalStateException("database or disk is full") else db.replacePlanned(id, row.activity, row.sac, row.distanceM, row.ascentM, row.descentM, row.durationS, row.segments, row.waypoints,
                row.pinned, difficulty = row.difficulty, days = row.days, clearDays = row.days == null)
        override fun storeTimes(id: Long, route: PlannedRoute?, activity: Activity) { log += "times $id" }
        override suspend fun proposeName(points: List<GeoPoint>, route: PlannedRoute?): String? = if (neverNames) awaitCancellation() else name.await()
        override fun tourChanged(id: Long) { log += "backup $id" }
        override fun purged(uuid: String) { log += "purged $uuid" }
        override fun syncChanged(why: String) { log += "sync: $why" }
        override fun refreshTours() { refreshes++ }
        override fun problem(text: String) { problem = text }
        override var trimmed = false
    }

    private val host = FakeHost()
    private val routes = RoutesState(store, clock = { 5_000L })
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private fun recent(nameWaitMs: Long = 60_000L) = RecentPlans(routes, store, host, scope, Dispatchers.Unconfined, nameWaitMs)
    private fun settle() = runBlocking { scope.coroutineContext.job.children.forEach { it.join() } }

    private fun pts(vararg lat: Double) = lat.map { GeoPoint(it, 7.0) }
    private fun leaving(points: List<GeoPoint>, unsaved: Boolean = true, savedId: Long = -1L, dated: String = "Route 18 Nov, 10:12") =
        RecentPlans.Leaving(points, unsaved, savedId, route = null, activity = Activity.HIKE, sac = 2,
            stops = points.map { GpxWaypoint(it.lat, it.lon, null) }, pins = emptySet(), dated = dated, difficulty = { null })
    private fun saved(name: String, vararg lat: Double, draft: Boolean = false, created: Long = 10L): Long =
        db.insertTour(TourKind.PLANNED, name, "HIKE", 2, listOf(lat.map { TrackPoint(it, 7.0, null) }),
            lat.map { GpxWaypoint(it, 7.0, null) }, createdMs = created, draft = draft)
    private fun drafts() = db.listTours().filter { it.draft }

    // ---------------- a plan leaves the map ----------------

    /**
     * Tester review 2026-11-07: the draft is written BEFORE the plan leaves the map – not after a wait of up to 5 s for a
     * place name in the background (a kill in that window lost the plan). The name follows as a rename.
     * The view model's thread and the IO thread are two [ManualExecutor]s here that run nothing by themselves: when
     * keep() is back and neither has run a task, the row must be there – a write moved into background work (a launch,
     * the io block) would not be.
     */
    @Test fun theDraftIsWrittenBeforeThePlanLeavesTheMapAndTheNameFollows() {
        val main = ManualExecutor("main"); val io = ManualExecutor("io")
        val onDemand = CoroutineScope(Job() + main.asCoroutineDispatcher())
        /** Background work runs now, both threads, until nothing waits. */
        fun background() { while (main.runAll() + io.runAll() > 0) { /* each may hand work to the other */ } }
        try {
            RecentPlans(routes, store, host, onDemand, io.asCoroutineDispatcher(), 60_000L).keep(leaving(pts(51.0, 51.1)), "cleared")
            // keep() is back and NO background task has run: the row is there, under its date name, and holds the place
            val row = drafts().single()
            assertEquals("Route 18 Nov, 10:12", row.name)
            assertEquals(listOf(51.0 to 7.0, 51.1 to 7.0), RoutesState.keyOf(row))
            assertEquals(row.id, routes.recentId)
            assertEquals(1, host.refreshes)
            assertTrue("nothing ran in the background yet", host.log.isEmpty())
            assertEquals("the name work waits", 1, main.size); assertEquals(0, io.size)
            background()
            assertEquals("the model times, then the wait for the name", listOf("times ${row.id}"), host.log)
            assertEquals("Route 18 Nov, 10:12", drafts().single().name)
            host.name.complete("Algund → Meran")
            background()
            assertEquals("Algund → Meran", drafts().single().name)
            assertEquals("the list shows the new name", 2, host.refreshes)
            assertTrue(problems.none())
        } finally { onDemand.cancel() }
    }

    @Test fun aNameThatNeverComesLosesNothing() {
        host.neverNames = true
        recent(nameWaitMs = 20L).keep(leaving(pts(51.0, 51.1)), "cleared")
        settle()
        assertEquals("Route 18 Nov, 10:12", drafts().single().name)
        assertEquals(1, host.refreshes)
        assertTrue(problems.none())
    }

    @Test fun noNameOrARenamedDraftKeepsItsName() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        db.rename(drafts().single().id, "Meine Runde") // renamed by the user while the proposal was on its way
        host.name.complete("Algund → Meran")
        assertEquals("Meine Runde", drafts().single().name)
        host.name = CompletableDeferred(null)
        r.keep(leaving(pts(52.0, 52.1), dated = "Route 18 Nov, 10:20"), "cleared")
        assertEquals("Route 18 Nov, 10:20", drafts().single().name)
    }

    /** One point is no plan; the same points twice make no second entry; a second plan sends the first to the bin. */
    @Test fun exactlyOnePlanStaysUnderZuletztAndTheOneBeforeGoesToTheBin() {
        val r = recent()
        r.keep(leaving(pts(51.0)), "cleared")
        assertTrue(db.listTours().isEmpty()); assertEquals("no row, no read of the list", 0, host.refreshes)
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        val first = drafts().single().id
        r.keep(leaving(pts(51.0, 51.1)), "cleared") // Undo, cleared again
        assertEquals(first, drafts().single().id)
        r.keep(leaving(pts(52.0, 52.1)), "replaced by \"x\"")
        val second = drafts().single().id
        assertTrue(second != first)
        assertEquals("the plan before is in the recycle bin, never gone", listOf(first), db.listDeleted().map { it.id })
        assertEquals(second, routes.recentId)
    }

    /**
     * Owner 2026-11-20 (final, app + planner): a plan opened from Zuletzt and left CHANGED updates its entry – no new
     * draft, nothing in the recycle bin; the entry keeps its name (the user may have given it).
     */
    @Test fun aChangedPlanOpenedFromZuletztUpdatesItsEntry() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        val id = drafts().single().id
        db.rename(id, "Meine Runde")
        host.log.clear()
        // opened from Zuletzt (the plan belongs to the draft), a point moved, left again
        r.keep(leaving(pts(51.0, 51.2), unsaved = true, savedId = id, dated = "Route 18 Nov, 10:20"), "cleared")
        settle()
        val row = drafts().single()
        assertEquals("the same entry", id, row.id)
        assertEquals("with the new points", listOf(51.0 to 7.0, 51.2 to 7.0), RoutesState.keyOf(row))
        assertEquals("and the new line", listOf(51.0, 51.2), db.loadSegments(id).single().map { it.lat })
        assertEquals("its name stays", "Meine Runde", row.name)
        assertTrue("nothing in the recycle bin", db.listDeleted().isEmpty())
        assertEquals(id, routes.recentId)
        assertEquals("the model times follow the new line; no name is asked for", listOf("times $id"), host.log)
        assertTrue(problems.none())
    }

    /** The entry waits in the bin because another plan took the place meanwhile: it comes back out, updated – one plan stays. */
    @Test fun aChangedPlanWhoseEntryWentToTheBinMeanwhileBringsItBackUpdated() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        val id = drafts().single().id
        r.keep(leaving(pts(52.0, 52.1)), "cleared") // another plan took the place
        val other = drafts().single().id
        r.keep(leaving(pts(51.0, 51.2), unsaved = true, savedId = id), "cleared")
        assertEquals(id, drafts().single().id)
        assertEquals(listOf(51.0 to 7.0, 51.2 to 7.0), RoutesState.keyOf(drafts().single()))
        assertEquals("exactly one plan under Zuletzt: the other one is in the bin, never gone", listOf(other), db.listDeleted().map { it.id })
        assertEquals(id, routes.recentId)
    }

    /**
     * Review 2026-11-20: the plan says "saved as it is" by its own link, but the entry's row holds other points (an
     * update the link did not follow): the plan on the map is written – never dropped for the row.
     */
    @Test fun aPlanCalledUnchangedWhoseEntryHoldsOtherPointsIsStillWritten() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1, 51.2)), "cleared")
        val id = drafts().single().id
        r.keep(leaving(pts(51.0, 51.1), unsaved = false, savedId = id), "cleared")
        assertEquals(id, drafts().single().id)
        assertEquals(listOf(51.0 to 7.0, 51.1 to 7.0), RoutesState.keyOf(drafts().single()))
        assertTrue(db.listDeleted().isEmpty())
    }

    /** The update fails (disk full): the plan is kept as a new draft, the entry goes back to the bin it came from – one draft, nothing lost. */
    @Test fun anUpdateThatFailsKeepsThePlanAsANewDraft() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        val id = drafts().single().id
        r.keep(leaving(pts(52.0, 52.1)), "cleared") // another plan took the place: the entry waits in the bin
        val other = drafts().single().id
        host.updateFails = true
        r.keep(leaving(pts(51.0, 51.2), unsaved = true, savedId = id), "cleared")
        val kept = drafts().single()
        assertTrue("a new draft", kept.id != id && kept.id != other)
        assertEquals(listOf(51.0 to 7.0, 51.2 to 7.0), RoutesState.keyOf(kept))
        assertEquals("both older ones are in the bin, never gone", setOf(id, other), db.listDeleted().map { it.id }.toSet())
        assertEquals(listOf("Zuletzt: the plan's entry could not be updated – kept as a new plan"), problems.said("routes", Problems.Level.WARN))
    }

    /** A SAVED route opened from Zuletzt and changed is never overwritten: it stays as it is, the change is a draft. */
    @Test fun aChangedSavedRouteFromZuletztStaysAsItIsAndTheChangeIsADraft() {
        val id = saved("Feierabendrunde", 51.0, 51.1)
        recent().keep(leaving(pts(51.0, 51.2), unsaved = true, savedId = id), "cleared")
        assertEquals(listOf(51.0 to 7.0, 51.1 to 7.0), RoutesState.keyOf(db.getTour(id)!!))
        val draft = drafts().single()
        assertTrue(draft.id != id); assertEquals(listOf(51.0 to 7.0, 51.2 to 7.0), RoutesState.keyOf(draft))
        assertEquals(draft.id, routes.recentId)
    }

    /** The entry is gone for good (deleted in the bin meanwhile): the plan is kept as a new draft – never lost. */
    @Test fun aChangedPlanWhoseEntryIsGoneForGoodIsKeptAnew() {
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1)), "cleared")
        val id = drafts().single().id
        db.deleteTour(id)
        r.keep(leaving(pts(51.0, 51.2), unsaved = true, savedId = id), "cleared")
        assertEquals(listOf(51.0 to 7.0, 51.2 to 7.0), RoutesState.keyOf(drafts().single()))
        assertTrue(problems.none())
    }

    /** A plan saved as it is makes no draft: its saved route takes the Zuletzt place; one with a saved route's points neither. */
    @Test fun aSavedPlanTakesThePlaceWithoutASecondEntry() {
        val id = saved("Feierabendrunde", 51.0, 51.1)
        val r = recent()
        r.keep(leaving(pts(51.0, 51.1), unsaved = false, savedId = id), "cleared")
        assertTrue(drafts().isEmpty()); assertEquals(id, routes.recentId)
        r.keep(leaving(pts(51.0, 51.1), unsaved = true), "cleared")
        assertTrue("never a draft with a saved route's points", drafts().isEmpty())
        assertEquals(1, db.listTours().size)
        assertEquals("the place may have changed hands: the list is read each time", 2, host.refreshes)
    }

    /** A full database: said in the problem log, nothing thrown at the caller (the plan's clear goes on), the list still read. */
    @Test fun aFailedWriteIsReportedAndNeverThrown() {
        db.failWrites = 1
        recent().keep(leaving(pts(51.0, 51.1)), "cleared")
        assertTrue(db.listTours().isEmpty())
        assertEquals(listOf("The plan could not be kept under Zuletzt"), problems.said("db", Problems.Level.ERROR))
        assertEquals(1, host.refreshes)
        assertTrue("no name work for a row that is not there", host.log.isEmpty())
    }

    /** "Los geht's" updated a saved route in place: its version before is under Zuletzt with its name, line and points. */
    @Test fun theVersionATourStartReplacedIsKeptUnderZuletzt() {
        val id = saved("Feierabendrunde", 51.0, 51.1)
        val old = db.getTour(id)!!; val line = db.loadSegments(id)
        // the route is updated in place first (the view model's overwrite) – here: other points
        db.exec("UPDATE tours SET waypoints=? WHERE id=?", com.onpath.app.data.TourJson.waypointsToJson(listOf(GpxWaypoint(53.0, 7.0, null), GpxWaypoint(53.1, 7.0, null))), id)
        recent().keepReplaced(old, line)
        val kept = drafts().single()
        assertEquals("Feierabendrunde", kept.name)
        assertEquals(RoutesState.keyOf(old), RoutesState.keyOf(kept))
        assertEquals(line, db.loadSegments(kept.id))
        assertEquals(1, host.refreshes)
        // the same version again: nothing twice, nothing read again
        recent().keepReplaced(old, line)
        assertEquals(1, drafts().size); assertEquals(1, host.refreshes)
    }

    // ---------------- Zuletzt → Speichern / Löschen ----------------

    @Test fun savingADraftMakesItASavedRouteWithBackupSyncAndTheMark() {
        val id = saved("Route 18 Nov", 51.0, 51.1, draft = true)
        recent().saveDraft(db.getTour(id)!!)
        assertFalse(db.getTour(id)!!.draft)
        assertEquals(id, routes.justSavedId); assertEquals(id, routes.recentId)
        assertEquals(listOf("backup $id", "sync: plan saved from Zuletzt"), host.log)
        assertEquals(1, host.refreshes); assertNull(host.problem)
    }

    @Test fun savingADraftThatIsGoneOrCannotBeWrittenSaysSoAndMarksNothing() {
        val id = saved("Route 18 Nov", 51.0, 51.1, draft = true)
        val t = db.getTour(id)!!
        db.failWrites = 1
        recent().saveDraft(t)
        assertTrue(db.getTour(id)!!.draft)
        assertEquals("The plan could not be saved. Your plan is still here – please try again.", host.problem)
        assertEquals(-1L, routes.justSavedId); assertTrue(host.log.isEmpty()); assertEquals(0, host.refreshes)
        assertEquals(listOf("A plan under Zuletzt could not be saved"), problems.said("db", Problems.Level.ERROR))
        host.problem = null
        db.deleteTour(id)
        recent().saveDraft(t)
        assertTrue(host.problem != null); assertTrue(host.log.isEmpty())
    }

    /** No question, no backup file (a draft has none), nothing for the browser. */
    @Test fun deletingADraftSendsItToTheBinAtOnce() {
        val id = saved("Route 18 Nov", 51.0, 51.1, draft = true)
        recent().deleteDraft(db.getTour(id)!!)
        assertEquals(listOf(id), db.listDeleted().map { it.id })
        assertEquals(5_000L, db.getTour(id)!!.deletedMs)
        assertTrue(host.log.isEmpty()); assertEquals(1, host.refreshes)
        db.failWrites = 1
        recent().deleteDraft(db.getTour(id)!!)
        assertEquals("a failed write: reported, the list not read again", 1, host.refreshes)
        assertEquals(1, problems.said("db", Problems.Level.ERROR).size)
    }

    // ---------------- recycle bin ----------------

    @Test fun theBinListsRestoresAndDeletesForGood() {
        val a = saved("A", 51.0, 51.1, created = 1L); val b = saved("B", 52.0, 52.1, created = 2L)
        db.setDeleted(a, 100L); db.setDeleted(b, 200L)
        val r = recent()
        assertNull("closed until opened", r.bin)
        r.openBin()
        assertEquals("newest deletion first", listOf(b, a), r.bin!!.map { it.id })
        r.restore(db.getTour(a)!!)
        assertNull(db.getTour(a)!!.deletedMs)
        assertEquals(listOf("backup $a", "sync: restored from the recycle bin"), host.log)
        assertEquals("the open list follows", listOf(b), r.bin!!.map { it.id }); assertEquals(1, host.refreshes)
        host.log.clear()
        val gone = db.getTour(b)!!
        r.deleteForever(gone)
        assertNull(db.getTour(b))
        assertEquals(listOf("purged ${gone.uuid}", "sync: deleted for good"), host.log)
        assertTrue(r.bin!!.isEmpty())
        assertNull(host.problem); assertTrue(problems.none())
    }

    /** A restored draft takes the Zuletzt place (the draft there goes to the bin) – it shows nowhere else. */
    @Test fun aRestoredDraftBecomesTheZuletztPlan() {
        val old = saved("Route alt", 51.0, 51.1, draft = true, created = 1L)
        db.setDeleted(old, 100L)
        val now = saved("Route neu", 52.0, 52.1, draft = true, created = 2L)
        val r = recent()
        r.restore(db.getTour(old)!!)
        assertEquals(listOf(old), drafts().map { it.id })
        assertEquals(listOf(now), db.listDeleted().map { it.id })
        assertEquals(old, routes.recentId)
    }

    @Test fun aFailedRestoreOrDeleteSaysSoAndChangesNothing() {
        val a = saved("Algund → Meran", 51.0, 51.1)
        db.setDeleted(a, 100L)
        val t = db.getTour(a)!!
        val r = recent()
        db.failWrites = 1
        r.restore(t)
        assertEquals(100L, db.getTour(a)!!.deletedMs)
        assertEquals("“Algund → Meran” could not be restored. Please try again.", host.problem)
        assertTrue("no backup, no sync for a restore that did not happen", host.log.isEmpty())
        assertEquals("the list is shown again as it is", listOf(a), r.bin!!.map { it.id })
        db.failWrites = 1
        r.deleteForever(t)
        assertEquals("still in the bin, its backup file stays", listOf(a), r.bin!!.map { it.id })
        assertTrue(host.log.isEmpty())
        assertEquals(listOf("Deleting a tour for good failed", "Restoring from the recycle bin failed"), problems.said("db", Problems.Level.ERROR))
    }

    // ---------------- the one clean-up after the one-plan rule ----------------

    @Test fun theDraftsOfAnOlderVersionGoToTheBinOnceAllButTheNewest() {
        val ids = (1L..3L).map { saved("Route $it", 50.0 + it, 51.0 + it, draft = true, created = it) }
        val keptSaved = saved("Gespeichert", 60.0, 60.1, created = 9L)
        val r = recent()
        r.trimOnce()
        assertEquals(listOf(ids[2]), drafts().map { it.id })
        assertEquals(setOf(ids[0], ids[1]), db.listDeleted().map { it.id }.toSet())
        assertTrue(db.listTours().any { it.id == keptSaved })
        assertTrue(host.trimmed); assertEquals(1, host.refreshes)
        // guarded: a later start moves nothing, reads nothing
        saved("Route 4", 70.0, 70.1, draft = true, created = 20L)
        r.trimOnce()
        assertEquals(2, drafts().size); assertEquals(1, host.refreshes)
    }

    @Test fun aFailedCleanUpIsTriedAgainAtTheNextStart() {
        (1L..2L).forEach { saved("Route $it", 50.0 + it, 51.0 + it, draft = true, created = it) }
        db.failWrites = 1
        val r = recent()
        r.trimOnce()
        assertFalse(host.trimmed); assertEquals(2, drafts().size)
        assertEquals(1, problems.said("routes", Problems.Level.WARN).size)
        r.trimOnce()
        assertTrue(host.trimmed); assertEquals(1, drafts().size)
    }

    @Test fun nothingToCleanUpIsRememberedToo() {
        recent().trimOnce()
        assertTrue(host.trimmed); assertEquals(0, host.refreshes)
    }
}
