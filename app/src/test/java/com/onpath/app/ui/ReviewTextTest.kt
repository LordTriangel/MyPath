package com.onpath.app.ui

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.time.TourReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A done tour's look back (owner 2026-11-15, round 3): its words, and nothing for what the data does not give. */
class ReviewTextTest {
    private val hike = Tour(1, TourKind.RECORDED, "Leiter Alm Runde", 0L, "HIKE", 2, 12_400.0, 620.0, 640.0, 16_500.0, "done", emptyList())

    @Test fun thePlanLine() {
        assertEquals("12 % faster than planned", ReviewText.plan(12))
        assertEquals("10 % slower than planned", ReviewText.plan(-10))
        assertEquals("Just as planned", ReviewText.plan(0))
        assertNull(ReviewText.plan(null))
    }

    @Test fun aHikeReadsMinutesPerKmARideKmPerHour() {
        assertEquals("11:40 min/km", ReviewText.kmTime(700.0, walking = true))
        assertEquals("36.0 km/h", ReviewText.kmTime(100.0, walking = false))
    }

    @Test fun nothingKnownGivesNoHighlightsAndOnlyTheStoredNumbers() {
        val empty = TourReview()
        assertTrue(ReviewText.highlights(hike, empty).isEmpty())
        // „Mehr“ (owner 2026-11-19): the tour's own descent and activity, nothing made up
        val more = More.of(hike, empty)!!
        assertEquals("↓640 m", more.descent)
        assertEquals("Hike T2", more.note)
        assertNull(more.clock); assertNull(more.rates); assertNull(more.plan)
    }

    @Test fun theHighlightsInTheSketchsOrder() {
        val r = TourReview(longestPause = TourReview.AtTrack(1500.0, 6200.0), highest = TourReview.AtTrack(1890.0, 6100.0),
            fastestKm = TourReview.AtTrack(700.0, 9000.0))
        val rows = ReviewText.highlights(hike, r)
        assertEquals(listOf("Longest break", "Highest point", "Fastest km"), rows.map { it.label })
        assertEquals(listOf("25 min", "1890 m", "11:40 min/km"), rows.map { it.value })
        assertEquals(listOf("km 6.2", "km 6.1", "km 9"), rows.map { it.where })
    }

    /** Owner 2026-11-16: on top km · ↑ m · km/h; the total time goes down to moving · breaks. */
    @Test fun withAnAverageTheTopEndsWithItAndTheTotalGoesDown() {
        val r = TourReview(totalS = 16_500.0, movingS = 13_080.0, pauseS = 3420.0, pauseCount = 4, avgKmh = 3.4)
        assertEquals(listOf("12.4 km" to "Distance", "↑620 m" to "Ascent", "3.4 km/h" to "Ø speed"), ReviewText.top(hike, r))
        assertEquals(listOf("4:35 h" to "Total time", "3:38 h" to "Moving", "57 min" to "Breaks (4)"), ReviewText.lower(hike, r))
        // only the known ones below: no moving time known → the total alone under the picture
        assertEquals(listOf("Total time"), ReviewText.lower(hike, TourReview(totalS = 16_500.0, avgKmh = 3.4)).map { it.second })
        // the review has no total: the tour's stored duration
        assertEquals("4:35 h" to "Total time", ReviewText.lower(hike, TourReview(avgKmh = 3.4)).first())
    }

    /** No average (moving time not known / under a minute): the top keeps the total time third, nothing empty, no "–". */
    @Test fun withoutAnAverageTheTopKeepsTheTotalTime() {
        val r = TourReview(totalS = 16_500.0, movingS = 13_080.0, pauseS = 3420.0, pauseCount = 4)
        assertEquals(listOf("Distance", "Ascent", "Total time"), ReviewText.top(hike, r).map { it.second })
        assertEquals("4:35 h", ReviewText.top(hike, r)[2].first)
        assertEquals(listOf("Moving", "Breaks (4)"), ReviewText.lower(hike, r).map { it.second })
        // no review yet (still read / not known): the stored numbers only
        assertEquals(listOf("12.4 km", "↑620 m", "4:35 h"), ReviewText.top(hike, null).map { it.first })
        assertTrue(ReviewText.lower(hike, null).isEmpty())
        assertTrue(ReviewText.lower(hike, TourReview()).isEmpty())
    }

    @Test fun pausesWithTheirNumberWhenKnown() {
        assertEquals("Breaks (4)", ReviewText.pausesLabel(4))
        assertEquals("Breaks", ReviewText.pausesLabel(0))
        assertEquals("Breaks", ReviewText.pausesLabel(null))
    }
}
