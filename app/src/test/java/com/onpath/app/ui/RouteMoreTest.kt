package com.onpath.app.ui

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.time.TourReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * „Mehr“ of a route's overview (owner 2026-11-19): only what the overview does not show – and nothing of it when the data
 * does not give it; no „Mehr“ at all when nothing is left.
 */
class RouteMoreTest {
    private val start = 1_763_201_520_000L // 10:12 UTC (unit tests run in UTC)
    private val end = start + (4 * 3600L + 35 * 60) * 1000
    private val done = Tour(1, TourKind.RECORDED, "Leiter Alm Runde", 0L, "HIKE", 2, 12_400.0, 620.0, 640.0, 16_500.0, "done",
        emptyList(), hasPlan = true)
    private val planned = Tour(2, TourKind.PLANNED, "Meran – Hirzer", 0L, "HIKE", 3, 14_200.0, 1210.0, 300.0, 6.1 * 3600, "done",
        emptyList())
    private val review = TourReview(totalS = 16_500.0, movingS = 13_080.0, pauseS = 3420.0, pauseCount = 2, startMs = start,
        endMs = end, avgKmh = 3.4, upMh = 410.0, downMh = 590.0, planPct = 12, plannedS = 14_880.0, neededS = 13_080.0,
        pauseSpans = listOf(TourReview.Span(start + 46 * 60_000L, start + 54 * 60_000L),
            TourReview.Span(start + 113 * 60_000L, start + 138 * 60_000L)),
        rank = TourReview.Rank(3, 14))

    @Test fun aDoneTourShowsTheExtrasAsGraphicsAndNoDoubles() {
        val m = More.of(done, review)!!
        val c = m.clock!!
        assertEquals("10:12", c.from); assertEquals("14:47", c.to)
        // the two pauses as gaps where they lie on the way (46 of 275 min, 8 min long …)
        assertEquals(2, c.gaps.size)
        assertEquals(46f / 275, c.gaps[0].first, 0.001f)
        assertEquals(54f / 275, c.gaps[0].second, 0.001f)
        assertEquals("Breaks (2)", c.key)
        assertEquals("On the way from 10:12 to 14:47, Breaks (2) 57 min", c.description)
        assertEquals("↓640 m", m.descent)
        // up / down on one scale: the faster side fills its bar
        val r = m.rates!!
        assertEquals(1f, r.part(590.0)); assertEquals(410f / 590, r.part(410.0), 0.001f)
        assertEquals("Uphill 410 m/h, Downhill 590 m/h", r.description)
        // planned vs needed: one scale, the difference in minutes
        val p = m.plan!!
        assertEquals(1f, p.plannedPart); assertEquals(13_080f / 14_880, p.neededPart, 0.001f)
        assertEquals("30 min faster", p.difference)
        assertEquals("Planned 4:08 h, Needed 3:38 h, 30 min faster, without breaks", p.description)
        assertEquals("Hike T2 · no. 3 by length of 14", m.note)
        // slower than planned: „… longer“; within a minute: as planned
        assertEquals("20 min longer", More.Plan(3600.0, 4800.0).difference)
        assertEquals("Just as planned", More.Plan(3600.0, 3630.0).difference)
        assertEquals(0.75f, More.Plan(3600.0, 4800.0).plannedPart, 0.001f)
    }

    @Test fun aDoneTourWithoutPausesHasAWholeBarAndNoKey() {
        val m = More.of(done, review.copy(pauseS = 0.0, pauseCount = 0, pauseSpans = emptyList(), downMh = null))!!
        val c = m.clock!!
        assertEquals(emptyList<Pair<Float, Float>>(), c.gaps)
        assertNull(c.key)
        assertEquals("On the way from 10:12 to 14:47", c.description)
        // only the known side of up / down
        assertEquals("Uphill 410 m/h", m.rates!!.description)
        // no plan followed: no planned / needed bars
        assertNull(More.of(done, review.copy(plannedS = null, neededS = null))!!.plan)
    }

    @Test fun aPlannedRouteShowsOnlyTheDescentAndTheActivity() {
        // a review is never one of a plan's: ignored
        val m = More.of(planned, review)!!
        assertNull(m.clock); assertNull(m.rates); assertNull(m.plan)
        assertEquals("↓300 m", m.descent)
        assertEquals("Hike T3", m.note)
        assertEquals(m, More.of(planned, null))
    }

    @Test fun aDoneTourStillReadKeepsItsStoredExtras() {
        val m = More.of(done, null)!!
        assertNull(m.clock); assertNull(m.rates); assertNull(m.plan)
        assertEquals("↓640 m", m.descent); assertEquals("Hike T2", m.note)
    }

    @Test fun nothingLeftMeansNoMore() {
        val flat = planned.copy(kind = TourKind.IMPORTED, activity = null, descentM = 0.0)
        assertNull(More.of(flat, null))
        // a done tour whose review knows nothing extra and has no activity / descent: no „Mehr“ either
        assertNull(More.of(done.copy(activity = null, descentM = 0.0), TourReview(totalS = 100.0, movingS = 90.0)))
        assertNotNull(More.of(done.copy(activity = null, descentM = 0.0), TourReview(upMh = 300.0)))
    }
}
