package com.onpath.app.ui

import com.onpath.app.cells.CellId
import com.onpath.app.cells.CellState
import com.onpath.app.cells.MapCells
import com.onpath.app.data.RoutingDataLoader
import com.onpath.app.domain.ActionPercent
import com.onpath.app.domain.StepShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Form B, the sign beside the map (owner 2026-11-19): what counts, the ONE number, its lines, when it shows / hides. */
class BackgroundDownloadsTest {
    private val a = CellId(543, 361)
    private val b = CellId(544, 361)
    private val c = CellId(545, 361)
    private val mb = 1_048_576L

    @Test fun onlyLoadingCellsOutsideTheWindowCount() {
        val states = mapOf(
            a to CellState.Downloading(2 * mb, 10 * mb),
            b to CellState.Building("base", 0.5, 60),
            c to CellState.WaitingForWifi(5 * mb), // waits: nothing runs
        )
        val items = BackgroundProgress.cellItems(states, waitedFor = setOf(b)) { if (it == b) 8 * mb else null }
        assertEquals(listOf("cell:${a.id}"), items.map { it.key })
        assertEquals(ActionPercent.Item("cell:${a.id}", 10 * mb, 0.30 + 0.50 * 0.2), items.single())
        assertTrue(BackgroundProgress.cellItems(mapOf(a to CellState.Ready("v1"), b to CellState.Failed("x")), emptySet()) { null }.isEmpty())
    }

    /** Each area by its steps: the server's own progress, its bytes, the pieces in while it is joined. */
    @Test fun eachLoadByItsSteps() {
        val s = StepShares.DEFAULT
        val joining = MapCells.Joining(setOf(c), 50, 100)
        val items = BackgroundProgress.cellItems(mapOf(a to CellState.Building("base", 0.5, 60), c to CellState.Installing), emptySet(),
            shares = s, joining = joining) { 10 * mb }
        assertEquals(listOf(0.15, 0.90), items.map { it.fraction })
        val r = RoutingDataLoader.Status(listOf("x"), RoutingDataLoader.Phase.LOADING, 5 * mb, 10 * mb, null)
        assertEquals(0.5 * 5 / 7, BackgroundProgress.routingItem(r, s).fraction, 1e-9)
        assertEquals(5.0 / 7 + 2.0 / 7 * 0.5, BackgroundProgress.routingItem(r.copy(doneBytes = 10 * mb, writing = true, written = 0.5), s).fraction, 1e-9)
    }

    /** A check for a newer version of an installed area (just asked, the server has not answered) is no loading. */
    @Test fun checkingInstalledAreaIsNotLoading() {
        val states = mapOf(a to CellState.Requested, b to CellState.Requested, c to CellState.Building("base", 0.2, 60))
        val items = BackgroundProgress.cellItems(states, emptySet(), installed = setOf(a, c)) { null }
        assertEquals(listOf("cell:${b.id}", "cell:${c.id}"), items.map { it.key })
    }

    /** One number over the action: finished loads count in full, a new load joining never drops it, the end starts afresh. */
    @Test fun oneNumberCountsFinishedLoadsAndNeverGoesBack() {
        val p = BackgroundProgress(ActionPercent())
        val one = p.update(listOf(ActionPercent.Item("a", 10 * mb, 0.5), ActionPercent.Item("b", 10 * mb, 0.0)), 2, 0)!!
        assertEquals(25, one.percent)
        assertEquals(20 * mb, one.bytes)
        val two = p.update(listOf(ActionPercent.Item("b", 10 * mb, 0.5)), 1, 0)!!
        assertEquals(75, two.percent)
        val three = p.update(listOf(ActionPercent.Item("b", 10 * mb, 0.5), ActionPercent.Item("c", 100 * mb, 0.0)), 2, 0)!!
        assertEquals(75, three.percent)
        assertNull(p.update(emptyList(), 0, 0))
        assertEquals(0, p.update(listOf(ActionPercent.Item("d", mb, 0.0)), 1, 0)!!.percent)
    }

    /** The details: one line per name with where it stands; a name that finished stays as "done" until the action ends. */
    @Test fun linesByNameTheFinishedOnesDone() {
        val p = BackgroundProgress(ActionPercent())
        fun groups(vararg g: Pair<String, LoadPhase>) = LoadNames.groups(g.map { (n, ph) -> LoadEntry(LoadWhy.POINT, n, bytes = 10 * mb, phase = ph) })
        p.update(listOf(ActionPercent.Item("a", mb, 0.1)), 2, 0, groups = groups("Bad Waldsee" to LoadPhase.LOADING, "Meran" to LoadPhase.PREPARING))
        val ui = p.update(listOf(ActionPercent.Item("a", mb, 0.5)), 1, 0, groups = groups("Meran" to LoadPhase.FINISHING))!!
        assertEquals(listOf("Around Bad Waldsee" to "done", "Around Meran" to "almost done"), ui.rows.map { it.name to it.state })
        assertEquals("The maps you have stay.", ui.notes.last())
        // a name change does not restart the 10 s wait nor hide the sign (the gate keys on nothing but presence)
        val gate = backgroundGate()
        gate.update(ui, 0)
        assertNotNull(gate.update(ui.copy(rows = emptyList()), 10_000).shown)
        val src = java.io.File("src/main/java/com/onpath/app/ui/MapScreen.kt").readText()
        assertTrue("Plan shows the sign under ☰", "BackgroundDownloadChip(" in src)
    }

    /** Owner: appears only after a background download ran > 10 s (2 s is too hectic), stays until all is done. */
    @Test fun signShowsAfterTenSecondsAndStaysUntilDone() {
        val gate = backgroundGate()
        val ui = BackgroundDownloadUi(2, 0, 20 * mb, 5)
        assertNull(gate.update(ui, 0).shown)
        assertNull(gate.update(ui.copy(percent = 20), 9_000).shown) // progress updates do not restart the wait
        assertNotNull(gate.update(ui.copy(percent = 30), 10_000).shown)
        assertEquals(60, gate.update(ui.copy(percent = 60), 60_000).shown?.percent) // progress shows at once, no timeout
        assertNull(gate.update(null, 70_000).shown) // all done: goes
    }

    @Test fun shortBackgroundDownloadsNeverShow() {
        val gate = backgroundGate()
        val ui = BackgroundDownloadUi(1, 0, mb, 0)
        assertNull(gate.update(ui, 0).shown)
        assertNull(gate.update(null, 8_000).shown) // done after 8 s: never shown
        assertNull(gate.update(ui, 9_000).shown) // a new one waits its own 10 s
        assertNull(gate.update(ui, 18_000).shown)
        assertNotNull(gate.update(ui, 19_000).shown)
    }

    @Test fun waitingLineInSettings() {
        assertEquals("Waiting to download: 16 areas · about 380 MB", WaitingDownloads(16, 2, 380 * mb).line)
        assertEquals("Waiting to download · about 9 MB", WaitingDownloads(0, 1, 9 * mb).line)
        assertEquals(false, WaitingDownloads(0, 0, 0).any)
    }

    /** A newer version of an area is told in the sign's details (when it is ready), no veil on the map. */
    @Test fun newerMapIsToldInTheDetails() {
        val onlyNewer = BackgroundProgress(ActionPercent()).update(listOf(ActionPercent.Item("cell:${a.id}", 10 * mb, 0.0)), 1, 0,
            newer = 1, newerEtaMin = 6)!!
        assertEquals("Newer map ready in about 6 min.", onlyNewer.notes.first())
        assertEquals(listOf("The maps you have stay."), BackgroundDownloadUi(3, 1, 0, 10).notes)
        // MapScreen no longer reads cell states (no "Loading the newer map…" veil): download ticks don't recompose it
        val src = java.io.File("src/main/java/com/onpath/app/ui/MapScreen.kt").readText()
        assertTrue("MapScreen must not collect cell states", "cells.states.collectAsState" !in src && "MapUpdateLayer" !in src)
    }

    /** The sign and the notification feed the same action: the same number (owner 2026-11-19). */
    @Test fun theNotificationSaysTheSameNumber() {
        val svc = java.io.File("src/main/java/com/onpath/app/cells/MapLoadService.kt").readText()
        assertTrue(svc.contains("BackgroundAction.shared.update(items)"))
        assertTrue(java.io.File("src/main/java/com/onpath/app/ui/BackgroundDownloads.kt").readText()
            .contains("class BackgroundProgress(private val action: ActionPercent = BackgroundAction.shared)"))
    }
}
