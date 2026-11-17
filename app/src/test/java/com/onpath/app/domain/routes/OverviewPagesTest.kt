package com.onpath.app.domain.routes

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Swiping through the overviews (owner 2026-11-15): the list as My routes shows it, each tour once, from the tapped one. */
class OverviewPagesTest {
    private fun tour(id: Long) = Tour(id, TourKind.PLANNED, "T$id", id, "HIKING", 2, 1000.0, 10.0, 10.0, 600.0, "done", emptyList())

    @Test fun zuletztFirstThenTheListAndASavedOneOnce() {
        val a = tour(1); val b = tour(2); val c = tour(3)
        assertEquals(listOf(2L, 1L, 3L), OverviewPages.of(b, listOf(a, b, c)).map { it.id })
        assertEquals(listOf(1L, 3L), OverviewPages.of(null, listOf(a, c)).map { it.id })
    }

    @Test fun startsAtTheTappedTour() {
        val pages = listOf(tour(1), tour(2), tour(3))
        assertEquals(2, OverviewPages.indexOf(pages, tour(3)))
        assertEquals(pages, OverviewPages.around(pages, tour(2)))
        // a tour not in the list (it changed under it): that tour alone, page 0
        assertEquals(listOf(9L), OverviewPages.around(pages, tour(9)).map { it.id })
        assertEquals(0, OverviewPages.indexOf(OverviewPages.around(pages, tour(9)), tour(9)))
    }
}
