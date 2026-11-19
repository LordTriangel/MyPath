package com.onpath.app.recording

import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L5 (docs/design/umbau-phase-2-tour.md): the service hears an activity switch made in a free recording and uses it
 * where it used the old one (the end's ride detection, the time prior, the rescued row). The service is an Android
 * service, so the wiring is pinned in its source; the rule itself is `RecordedActivityTest` in `:core`.
 */
class RecordedActivityServiceTest {
    private val svc = sourceFile("recording/RecordingService.kt").readText()

    @Test fun theServiceKnowsTheCommandAndStopsItselfWhenNothingRecords() {
        assertEquals("com.onpath.app.ACTIVITY_CHANGED", RecordingCommand.activityChanged("BIKE", 2).action)
        assertEquals(mapOf<String, Any>("activity" to "BIKE", "sac" to 2), RecordingCommand.activityChanged("BIKE", 2).extras)
        assertTrue(svc.contains("const val ACTION_ACTIVITY_CHANGED = RecordingWire.ACTION_ACTIVITY_CHANGED"))
        val control = svc.substringAfter("val control = intent?.action in setOf(").substringBefore(")")
        assertTrue("a control action: with no recording the service stops itself again", control.contains("ACTION_ACTIVITY_CHANGED"))
    }

    @Test fun theCommandSwitchesTheRecordedActivityAndTheTimePrior() {
        val handler = svc.substringAfter("ACTION_ACTIVITY_CHANGED ->").substringBefore("return START_STICKY")
        assertTrue(handler.contains("recorded.switchTo(intent.getStringExtra(EXTRA_ACTIVITY), level)"))
        assertTrue("the time prior follows", handler.contains("trip.setPrior(priorFor(timeActivity()))"))
        assertTrue("the rescue row reads its level from the same copy (also after a switch on the route)", svc.contains("val sac = recorded.sac"))
    }

    @Test fun theEndTheTimesAndTheRescueReadTheSwitchedActivity() {
        assertTrue(svc.contains("private fun timeActivity() = recorded.timeActivity(navigator?.session?.activity)"))
        assertTrue("the end", svc.substringAfter("private fun finish(startId: Int)").substringBefore("\n    }\n").contains("timeActivity()"))
        assertTrue("the rescue", svc.contains("db.rescueRecording(tourId, name, recorded.activity.name"))
        assertTrue("no second copy of the activity", !Regex("""\brecActivity\b""").containsMatchIn(svc))
    }

    @Test fun anActivitySwitchTakenOnTheRouteIsKeptForAfterTheFollowEnds() {
        val step = svc.substringAfter("if (step.activityChanged && tourId >= 0) {").substringBefore("Prefs(this).activity")
        assertTrue(step.contains("recorded.switchTo(nav.session.activity.name, nav.session.sac)"))
    }
}
