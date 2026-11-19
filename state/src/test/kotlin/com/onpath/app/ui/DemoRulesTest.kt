package com.onpath.app.ui

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The demo rides' decisions (developer tools → Demo): which line is ridden, which recorded tours are offered, their label. */
class DemoRulesTest {
    private fun p(i: Int) = GeoPoint(46.0 + i * 0.001, 11.0, 0.0)
    private fun track(vararg segs: List<GeoPoint>) = ShownTrack(1L, "Rax", segs.toList(), emptyList(), 0.0, 0.0, 0.0, 0.0)
    private fun tour(id: Long, name: String = "Tour $id", kind: TourKind = TourKind.RECORDED, distM: Double = 5000.0) =
        Tour(id, kind, name, 0L, "HIKE", 2, distM, 0.0, 0.0, 0.0, "done", emptyList())

    @Test fun theLineIsThePlannedRouteFirst() {
        val r = listOf(p(0), p(1), p(2))
        val got = DemoRules.demoLine(r, track(listOf(p(5), p(6))))
        assertEquals("planned route" to r, got)
    }

    @Test fun aRouteOfOnePointFallsBackToTheShownTrackWhichIsJoinedAcrossSegments() {
        val got = DemoRules.demoLine(listOf(p(0)), track(listOf(p(5)), listOf(p(6), p(7))))
        assertEquals("track \"Rax\"" to listOf(p(5), p(6), p(7)), got)
    }

    @Test fun nothingToRideIsNull() {
        assertNull(DemoRules.demoLine(null, null))
        assertNull(DemoRules.demoLine(null, track(listOf(p(1)))))
    }

    @Test fun recordedToursOnlyNotDemoOnesNotTheTinyOnesNewestEightFirst() {
        val tours = (1L..12L).map { tour(it) } +
            tour(50, kind = TourKind.PLANNED) + tour(51, name = "Demo · Rax") + tour(52, distM = 499.9) + tour(53, distM = 500.0)
        val got = DemoRules.recentRecordedTours(tours, "Demo · ").map { it.id }
        assertEquals(listOf(53L, 12, 11, 10, 9, 8, 7, 6), got)
    }

    @Test fun theLabelNamesTheTourAndKilometresWithADotWhateverTheLocale() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("ride tour \"Rax\" (12.3 km, recorded times)", DemoRules.demoTourLabel(tour(1, "Rax", distM = 12345.0)))
        } finally { java.util.Locale.setDefault(old) }
    }
}
