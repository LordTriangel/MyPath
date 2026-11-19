package com.onpath.app.domain.routes

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.domain.RoutesTab
import com.onpath.app.domain.routes.RouteSort.Choice
import com.onpath.app.domain.routes.RouteSort.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * My routes' pure list rules (converted from RouteSortTest, ListTopTest, OverviewPagesTest – test-audit G13): the sort
 * order and its chips, the list top after a save, swiping through the overviews.
 */
class RoutesListRulesTest {
    private fun tour(id: Long, kind: TourKind = TourKind.PLANNED, name: String = "T$id", created: Long = id,
                     km: Double = 1.0, h: Double = 0.0, up: Double = 0.0) =
        Tour(id, kind, name, created, "HIKE", 2, km * 1000, up, up, h * 3600, "done", emptyList())
    private fun ids(l: List<Tour>) = l.map { it.id }

    private val a = tour(1, created = 100, km = 14.2, h = 6.1, up = 1210.0, name = "Hirzer")
    private val b = tour(2, created = 300, km = 9.6, h = 5.2, up = 1350.0, name = "Ifinger")
    private val c = tour(3, created = 500, km = 31.5, h = 2.0, up = 120.0, name = "Etschradweg")
    private val d = tour(4, TourKind.IMPORTED, created = 400, km = 8.1, h = 2.1, up = 210.0, name = "Waalweg")
    private val list = listOf(a, b, c, d)
    private fun sorted(l: List<Tour>, ch: Choice) = ids(RouteSort.sort(l, ch))

    @Test fun sortOrdersAndTapTurnsTheChosenChipRound() {
        assertEquals(Choice(Key.NEWEST, true), RouteSort.DEFAULT)
        assertEquals(listOf(3L, 4L, 2L, 1L), sorted(list, RouteSort.DEFAULT))
        assertEquals(listOf(1L, 2L, 4L, 3L), sorted(list, Choice(Key.OLDEST)))
        assertEquals(listOf(3L, 1L, 2L, 4L), sorted(list, Choice(Key.LENGTH)))
        assertEquals(listOf(1L, 2L, 4L, 3L), sorted(list, Choice(Key.DURATION)))
        assertEquals(listOf(2L, 1L, 4L, 3L), sorted(list, Choice(Key.ASCENT)))
        assertEquals(listOf(4L, 2L, 1L, 3L), sorted(list, Choice(Key.LENGTH, biggestFirst = false)))
        assertEquals(listOf(3L, 4L, 1L, 2L), sorted(list, Choice(Key.ASCENT, biggestFirst = false)))
        // ties go newest first whatever the input order
        val x = tour(10, created = 100); val y = tour(11, created = 200); val z = tour(12, created = 200)
        for (input in listOf(listOf(x, y, z), listOf(z, x, y), listOf(y, z, x))) {
            assertEquals("$input", listOf(12L, 11L, 10L), sorted(input, Choice(Key.LENGTH)))
            assertEquals("$input", listOf(12L, 11L, 10L), sorted(input, Choice(Key.LENGTH, biggestFirst = false)))
        }
        val len = RouteSort.tap(RouteSort.DEFAULT, Key.LENGTH)
        assertEquals(Choice(Key.LENGTH, true), len)
        assertEquals(Choice(Key.LENGTH, false), RouteSort.tap(len, Key.LENGTH))
        assertEquals(Choice(Key.LENGTH, true), RouteSort.tap(RouteSort.tap(len, Key.LENGTH), Key.LENGTH))
        assertEquals("another key starts biggest first again", Choice(Key.ASCENT, true), RouteSort.tap(Choice(Key.LENGTH, false), Key.ASCENT))
        assertEquals("the date chips have no direction", Choice(Key.NEWEST), RouteSort.tap(Choice(Key.NEWEST), Key.NEWEST))
        assertEquals(Choice(Key.OLDEST), RouteSort.tap(Choice(Key.NEWEST), Key.OLDEST))
    }

    @Test fun sortStoredFormRoundTripsAndToleratesJunk() {
        for (k in Key.entries) for (big in listOf(true, false)) assertEquals(Choice(k, big), RouteSort.decode(RouteSort.encode(Choice(k, big))))
        assertEquals(RouteSort.DEFAULT, RouteSort.decode(null))
        assertEquals(RouteSort.DEFAULT, RouteSort.decode(""))
        assertEquals(RouteSort.DEFAULT, RouteSort.decode("DIFFICULTY:down")) // a newer build's key
        assertEquals(Choice(Key.ASCENT, true), RouteSort.decode("ASCENT"))
    }

    /** With the search and the tabs: the order holds within what the tab and the search leave. */
    @Test fun sortWorksAfterTabAndSearch() {
        val rec1 = tour(20, TourKind.RECORDED, "Tour Hirzer", 600, km = 20.0, h = 3.0, up = 500.0)
        val rec2 = tour(21, TourKind.RECORDED, "Tour Meran", 700, km = 40.0, h = 4.0, up = 300.0)
        val all = list + rec1 + rec2
        val planned = RoutesTab.filter(all, RoutesTab.PLANNED)
        assertEquals(listOf(3L, 1L, 2L, 4L), sorted(planned, Choice(Key.LENGTH)))
        val done = RoutesTab.filter(all, RoutesTab.DONE)
        assertEquals(listOf(21L, 20L), sorted(done, Choice(Key.LENGTH)))
        assertEquals(listOf(20L, 21L), sorted(done, Choice(Key.ASCENT)))
        val found = RoutesTab.search(planned, "i")
        assertEquals(listOf(1L, 2L), sorted(found, Choice(Key.LENGTH)))
        assertEquals(listOf(2L, 1L), sorted(found, Choice(Key.LENGTH, biggestFirst = false)))
    }

    /**
     * E2E 2026-11-07 (CoreFieldE2ETest.aSavedRouteTapped…, GitHub class runs; CorePlanE2ETest.aRouteAddedWhileMyRoutesIsOpen…
     * on the emulator): My routes
     * opened with the list read before, the fresh read put the new route on top – but the list kept its old first row at the
     * top (a LazyColumn holds its first visible row by key) and the new route lay above the screen. A list at its top stays
     * at its top when its first row changes; a list scrolled down keeps its place.
     */
    @Test fun aListAtItsTopStaysAtItsTopWhenItsFirstRowChanges() {
        // seen: the list at the top with "Plan 12:27" first, then "E2E saved route" read before it
        assertEquals(0, ListTop.scrollTo(firstKeyBefore = 27L, firstKeyNow = 99L, wasAtTop = true))
        assertNull("scrolled down keeps its place", ListTop.scrollTo(firstKeyBefore = 27L, firstKeyNow = 99L, wasAtTop = false))
        assertNull(ListTop.scrollTo(firstKeyBefore = 27L, firstKeyNow = 27L, wasAtTop = true))
        assertNull("the first composition", ListTop.scrollTo(firstKeyBefore = null, firstKeyNow = 27L, wasAtTop = true))
        assertTrue(ListTop.atTop(firstVisibleIndex = 0, firstVisibleOffset = 0))
        assertFalse("the first row partly scrolled away", ListTop.atTop(0, 12))
        assertFalse(ListTop.atTop(1, 0))
    }

    /** Swiping through the overviews (owner 2026-11-15): the list as My routes shows it, each tour once, from the tapped one. */
    @Test fun overviewPagesZuletztFirstEachTourOnceStartingAtTheTappedOne() {
        val t1 = tour(1); val t2 = tour(2); val t3 = tour(3)
        assertEquals(listOf(2L, 1L, 3L), ids(OverviewPages.of(t2, listOf(t1, t2, t3))))
        assertEquals(listOf(1L, 3L), ids(OverviewPages.of(null, listOf(t1, t3))))
        val pages = listOf(t1, t2, t3)
        assertEquals(2, OverviewPages.indexOf(pages, t3))
        assertEquals(pages, OverviewPages.around(pages, t2))
        assertEquals("a tour not in the list (it changed under it): that tour alone, page 0", listOf(9L), ids(OverviewPages.around(pages, tour(9))))
        assertEquals(0, OverviewPages.indexOf(OverviewPages.around(pages, tour(9)), tour(9)))
    }
}
