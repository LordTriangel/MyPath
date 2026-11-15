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
        // ascent and descent are the tour's own; nothing else is made up
        assertEquals(listOf("Ascent", "Descent"), ReviewText.details(hike, empty).map { it.first })
        // the activity is always known
        assertEquals("Hike T2", ReviewText.note(hike, empty))
    }

    @Test fun theHighlightsInTheSketchsOrder() {
        val r = TourReview(longestPause = TourReview.AtTrack(1500.0, 6200.0), highest = TourReview.AtTrack(1890.0, 6100.0),
            fastestKm = TourReview.AtTrack(700.0, 9000.0))
        val rows = ReviewText.highlights(hike, r)
        assertEquals(listOf("Longest break", "Highest point", "Fastest km"), rows.map { it.label })
        assertEquals(listOf("25 min", "1890 m", "11:40 min/km"), rows.map { it.value })
        assertEquals(listOf("km 6.2", "km 6.1", "km 9"), rows.map { it.where })
    }

    @Test fun pausesWithTheirNumberWhenKnown() {
        assertEquals("Breaks (4)", ReviewText.pausesLabel(4))
        assertEquals("Breaks", ReviewText.pausesLabel(0))
        assertEquals("Breaks", ReviewText.pausesLabel(null))
    }
}
