package com.onpath.app.recording

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.rides.Ride
import com.onpath.app.rides.RideApply
import com.onpath.app.rides.RideDetector
import com.onpath.app.rides.RideRecord
import com.onpath.app.rides.RideStore
import com.onpath.app.rides.TourStats
import com.onpath.app.rides.TraceBuilder
import com.onpath.app.rides.record
import com.onpath.app.routing.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L5 (docs/design/umbau-phase-2-tour.md §6.2): the service's copy of the recorded activity follows a switch made in the
 * app, so the end of the tour judges the track as the activity it was ridden as. Example: hike for 5 minutes, then the
 * activity is switched to bike and the rest is ridden at 26 km/h – judged as a hike that is a "vehicle ride".
 */
class RecordedActivityTest {

    private class Store(val tour: Tour, val segs: List<List<TrackPoint>>) : RideStore {
        var rides: RideRecord? = null
        override fun tour(id: Long) = tour
        override fun segments(id: Long) = segs
        override fun setRides(id: Long, rec: RideRecord?, stats: TourStats): Boolean { rides = rec; return true }
        override fun insertPart(from: Tour, name: String, segments: List<List<TrackPoint>>, stats: TourStats, createdMs: Long) = error("no split here")
    }

    private val trace = TraceBuilder().walk(5.0).move(20 * 60, 26.0, 90.0)
    private val rec = record(trace.fixes)

    private fun endWith(a: Activity): RideRecord? {
        val tour = Tour(1, TourKind.RECORDED, "Tour", trace.fixes.first().t, "BIKE_TOURING", 2, rec.distanceM, rec.ascentM, rec.descentM,
            rec.durationS, "active", emptyList())
        val s = Store(tour, rec.segments)
        RideApply.finish(s, 1, rec.stats, trace.t + 1000, a)
        return s.rides
    }

    @Test fun theSwitchChangesWhatTheEndJudgesTheTrackAs() {
        val ra = RecordedActivity(Activity.HIKE, 2)
        assertTrue("the stale hike sees a vehicle in a bike ride", endWith(ra.timeActivity(null)) != null)
        assertTrue(ra.switchTo("BIKE_TOURING", 2))
        assertEquals(Activity.BIKE_TOURING, ra.activity)
        assertNull("judged as the bike it was ridden as: no ride", endWith(ra.timeActivity(null)))
        assertEquals(emptyList<Ride>(), RideDetector.detect(rec.segments, ra.timeActivity(null)))
    }

    @Test fun theNavigatedSessionStillWinsForTheTimes() {
        val ra = RecordedActivity(Activity.HIKE, 2)
        ra.switchTo("MTB", 3)
        assertEquals(Activity.EBIKE, ra.timeActivity(Activity.EBIKE))
        assertEquals(Activity.MTB, ra.timeActivity(null))
    }

    @Test fun anUnknownOrMissingActivityChangesNothingAndALevelOutOfRangeKeepsTheOldOne() {
        val ra = RecordedActivity(Activity.HIKE, 4)
        assertFalse(ra.switchTo(null, 2)); assertFalse(ra.switchTo("SKATEBOARD", 2))
        assertEquals(Activity.HIKE to 4, ra.activity to ra.sac)
        assertTrue(ra.switchTo("EBIKE", 0))
        assertEquals(Activity.EBIKE to 4, ra.activity to ra.sac)
        assertTrue(ra.switchTo("HIKE", 5))
        assertEquals(Activity.HIKE to 5, ra.activity to ra.sac)
    }
}
