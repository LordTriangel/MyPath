package com.onpath.app.cells

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Owner 2026-11-19: joining the downloaded map in reported nothing – that is where the number stood. Now the store tells
 * how many pieces are in as it writes a generation (every layer, the POIs, the POI tiles), never back, the last report
 * = all; a tile layer reports while it is written too ([PmTiles.merge]).
 */
class JoinProgressTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun theJoinReportsThePiecesIn() {
        val store = CellStore(tmp.newFolder("data"))
        val a = CellId(543, 361); val b = CellId(544, 361)
        val da = tmp.newFolder("a"); val db = tmp.newFolder("b")
        store.apply(add = listOf(CellStore.Download(CellFixtures.cell(da, a, "v1"), da))).also { store.confirmLoaded(it.number) }
        val seen = ArrayList<Pair<Long, Long>>()
        store.apply(add = listOf(CellStore.Download(CellFixtures.cell(db, b, "v1"), db))) { d, t -> seen += d to t }
        assertTrue("several reports: $seen", seen.size >= CellFixtures.LAYERS.size)
        val total = seen.first().second
        assertTrue(seen.all { it.second == total && it.first in 0..total })
        for (i in 1 until seen.size) assertTrue("never back: $seen", seen[i].first >= seen[i - 1].first)
        assertEquals(0L, seen.first().first)
        assertEquals("the last report: all in", total, seen.last().first)
    }

    @Test fun aLayerReportsWhileItIsWritten() {
        val c = CellId(543, 361)
        val dir = tmp.newFolder("c")
        CellFixtures.cell(dir, c, "v1", maxZ = 14)
        val written = ArrayList<Long>()
        val out = File(tmp.root, "out.pmtiles")
        PmTiles.merge(listOf(PmTiles.Input(File(dir, "base.pmtiles")) { _, _, _ -> true }), out) { written += it }
        assertTrue(written.isNotEmpty())
        for (i in 1 until written.size) assertTrue(written[i] >= written[i - 1])
        assertTrue("all tile bytes in the end", written.last() > 0 && written.last() <= out.length())
    }

    /** A failing reporter never stops the join (the map matters more than its number). */
    @Test fun aFailingReporterDoesNotStopTheJoin() {
        val store = CellStore(tmp.newFolder("data"))
        val a = CellId(543, 361)
        val da = tmp.newFolder("a")
        val g = store.apply(add = listOf(CellStore.Download(CellFixtures.cell(da, a, "v1"), da))) { _, _ -> throw IllegalStateException("ui gone") }
        assertEquals(listOf(a), g.cells.map { it.id })
    }
}
