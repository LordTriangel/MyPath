package com.onpath.app.ui

import com.onpath.app.data.Tour
import com.onpath.app.data.TourDb
import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.recording.RecState
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlannedRoute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** What the demo rides read from / say to the view model (phase 2, piece 6). */
interface DemoRidesHost {
    val route: PlannedRoute?
    val shown: ShownTrack?
    val activity: Activity
    val recState: StateFlow<RecState?>
    val tours: List<Tour>
    fun planFactors(): com.onpath.app.time.Factors
    fun sayInfo(text: String, place: NoticePlace)
    fun sayError(text: String, place: NoticePlace)
    fun initialCamera(): Triple<Double, Double, Double>
}

/**
 * Developer tools → Demo (recording/DemoLocation): the rides the view model started itself – "Demo: ride", Navigate's
 * "▶ Sim", a recorded tour ridden again, standing at the map centre. Stays in `:app` (it drives `DemoLocation`); its pure
 * decisions (the line, the offered tours, the label) are [DemoRules] in `:state`. Moved out of `AppViewModel` as they were
 * (piece 6 of the rebuild); the view model's same-name members forward here.
 */
class DemoRides(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val db: TourDb,
    private val host: DemoRidesHost,
) {
    /** What "Demo: ride" would ride: the planned route, else the shown track (a GPX file / tour); null = nothing. */
    fun demoLine(): Pair<String, List<GeoPoint>>? = DemoRules.demoLine(host.route?.points) { host.shown }

    /** Rides [demoLine] from its start at the activity's modelled speed (user's pace); real GPS is ignored meanwhile. */
    fun startDemoRide() {
        val (label, line) = demoLine() ?: return
        val path = com.onpath.app.recording.DemoPath.timed(line, host.activity, host.planFactors())
        if (path == null) { host.sayInfo("This line is too short for a demo ride.", NoticePlace.SETTINGS); return }
        com.onpath.app.recording.DemoLocation.startRide(path, label)
    }

    /**
     * Navigate → "▶ Sim" (developer tools, [NavSimChip]): rides [line] – the line followed now – from where the tour is
     * on it ([NavSimStart]), else [demoLine] from its start; a ride already on goes first.
     */
    fun startNavSim(line: List<GeoPoint>?) {
        val followed = line?.takeIf { it.size >= 2 }
        val (label, l) = followed?.let { "followed route" to it } ?: demoLine()
            ?: run { host.sayError("Nothing to simulate: no followed route or plan.", NoticePlace.MAP); return }
        val path = com.onpath.app.recording.DemoPath.timed(l, host.activity, host.planFactors())
            ?: run { host.sayError("This line is too short for a demo ride.", NoticePlace.MAP); return }
        val nav = host.recState.value?.nav
        val fromM = NavSimStart.alongM(sameLine = followed != null && nav?.line == followed,
            reached = nav?.session?.reached == true, shownAlongM = nav?.shownAlongM, progressM = nav?.progressM)
        Problems.trace("demo", "Tour simulation started on the Navigate page ($label) at ${fromM.toInt()} m of ${path.lengthM.toInt()} m")
        com.onpath.app.recording.DemoLocation.startRide(path, label, startAlongM = fromM)
        navSimLine = l
    }

    /** Developer tools → Demo: the last recorded tours (not demo ones) to ride again at their recorded times. */
    fun recentRecordedTours(): List<Tour> = DemoRules.recentRecordedTours(host.tours, com.onpath.app.recording.DemoLocation.TOUR_PREFIX)

    fun demoTourLabel(t: Tour): String = DemoRules.demoTourLabel(t)

    /** Rides recorded tour [t] again along its track at its own times (debugging a field tour, owner 2026-11-01). */
    fun startDemoTour(t: Tour) {
        scope.launchSafe("app", "startDemoTour") {
            val pts = withContext(io) {
                runCatching { db.loadSegments(t.id).flatten() }.onFailure { Problems.warn("demo", "Recorded tour not read for the demo", it) }
                    .getOrDefault(emptyList())
            }
            val path = com.onpath.app.recording.DemoPath.recorded(pts.map { GeoPoint(it.lat, it.lon, it.ele) }, pts.map { it.timeMs })
                ?: run { host.sayError("This tour has no track with times to ride.", NoticePlace.SETTINGS); return@launchSafe }
            navSimLine = null // a recorded tour is ridden as it was – never moved onto a changed line
            Problems.trace("demo", "Riding recorded tour ${t.id} \"${t.name}\" again at its recorded times (${pts.size} points)")
            com.onpath.app.recording.DemoLocation.startRide(path, "recorded tour \"${t.name}\"")
        }
    }

    /** The line the Navigate "Sim" rides; null = no Sim from the Navigate page. */
    var navSimLine: List<GeoPoint>? = null

    /**
     * The followed line changed (plan edit, new route) while the Navigate "Sim" rides: it goes on along the new line from
     * where it is ([com.onpath.app.recording.DemoLocation.switchLine]) – else the voice speaks of one line while the
     * simulated position moves on another (owner 2026-11-01).
     */
    fun navSimFollow(line: List<GeoPoint>?) {
        val st = com.onpath.app.recording.DemoLocation.state.value
        if (st == null || st.kind != com.onpath.app.recording.DemoLocation.Kind.RIDE) { navSimLine = null; return }
        if (navSimLine == null || line == null || line.size < 2 || line == navSimLine) return
        val path = com.onpath.app.recording.DemoPath.timed(line, host.activity, host.planFactors()) ?: return
        navSimLine = line
        com.onpath.app.recording.DemoLocation.switchLine(path, "followed route")
    }

    /** Stands at the map centre (non-moving shots). */
    fun startDemoAtMapCentre() {
        val (lat, lon, _) = host.initialCamera()
        com.onpath.app.recording.DemoLocation.jumpTo(lat, lon, null, "map centre")
    }
}
