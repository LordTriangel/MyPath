package com.onpath.app.recording

import com.onpath.app.debug.enumOrNull
import com.onpath.app.routing.Activity

/**
 * The activity (and T level) the running recording is recorded as – the service's copy of the tour row's activity.
 * Set at the start / continue from the row, and changed by [switchTo] when the app says so
 * ([RecordingCommand.activityChanged]: the check sheet in a free recording, L5 of docs/design/umbau-phase-2-tour.md). The
 * end of the tour (the ride detection of `RideApply.finish`), the time prior and a rescued row read it ([timeActivity]).
 */
class RecordedActivity(var activity: Activity = Activity.HIKE, var sac: Int = 2) {

    /**
     * The app switched the activity: [name] is an [Activity] name, [level] a T level 1..5 (anything else keeps the
     * stored level). Returns false – nothing changed – for a missing or unknown name.
     */
    fun switchTo(name: String?, level: Int): Boolean {
        val a = enumOrNull<Activity>(name, "recording") ?: return false
        activity = a
        if (level in 1..5) sac = level
        return true
    }

    /** The activity the times and the rides are computed for: the navigated session's, else the recording's. */
    fun timeActivity(navigated: Activity?): Activity = navigated ?: activity
}
