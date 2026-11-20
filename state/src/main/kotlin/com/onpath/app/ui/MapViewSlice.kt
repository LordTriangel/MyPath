package com.onpath.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onpath.app.data.Geo
import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.domain.AppMode
import com.onpath.app.domain.PlanCamera
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.strings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/** What [MapViewSlice] reads of the rest of the app and tells it (main thread). The view model implements it. */
interface MapViewHost {
    val mode: AppMode
    val activity: Activity
    /** The session's one orientation: walking direction up (true) or north up. */
    val headingUp: Boolean
    /** Stores the orientation in the settings (no change when it is already so). */
    fun setHeadingUp(on: Boolean)
    /** Zoom the map takes when Navigate starts / locate is tapped in Navigate; null once taken. */
    var navStartZoom: Double?
    /** The planned route's line (null = no route). */
    val routePoints: List<GeoPoint>?
    /** The plan's points. */
    val waypoints: List<GeoPoint>
    /** The track shown on the map, if any. */
    val shown: ShownTrack?
    /** The line followed in a tour (null = none). */
    val followLine: List<GeoPoint>?
    /** The followed line as it was started while a new route is followed (null = none / not touring). */
    val navOriginal: List<GeoPoint>?
    /** A tour is recording. */
    val touring: Boolean
    /** A shared place keeps the camera until this elapsed-real-time moment (ms). */
    val keepCameraUntilMs: Long
    /** The dot on the route where a finger is on the elevation profile (null = none). */
    fun highlight(at: GeoPoint?)
    /** The list of route spots closes. */
    fun closeSpots()
    /** The amber piece the map shows. */
    fun spotLine(piece: List<GeoPoint>)
    /** The app is on screen again: the map data is told (T3 in Plan: the next fix checks the own area), sync is asked. */
    fun appReturned(inPlan: Boolean)
    /** The app left the screen (map areas still loading keep going). */
    fun appLeft()
}

/**
 * The map's camera state and what moves it (cut out of `AppViewModel`, rebuild phase 2 piece 5): following the position,
 * free look, compass, locate, the profile scrub in Navigate, the fits ("Show whole route", plan and me, the plan at a
 * moment, the first route, a piece) and the start camera's flags. Every camera effect leaves through [camera] in the order
 * the view model had; MapLibre and the `UiEvent` list stay in `:app` (CLAUDE.md "Map camera": while the map follows, the
 * camera is never moved from code – every fit stops following first).
 */
class MapViewSlice(
    private val notices: NoticeBoard,
    private val camera: Camera,
    private val position: Position,
    private val host: MapViewHost,
    private val scope: CoroutineScope,
    /** Milliseconds since boot without deep sleep (the touch stamps and the settle waits). */
    private val uptimeMs: () -> Long,
    /** Milliseconds of real time since boot (the time spent in the background). */
    private val elapsedMs: () -> Long,
) {
    private val mode get() = host.mode

    // --- Navigate orientation + next turn ---
    /** Bumped to turn the map north (MapScreen → MapController.northUp). */
    var northTick by mutableIntStateOf(0)
        private set
    /** Bumped when the camera must follow the position again although [followMe] did not change. */
    var recentreTick by mutableIntStateOf(0)
        private set

    /**
     * Locate button (GPS on, permission given): follow the position again. In Navigate also back to the standard
     * follow zoom of the activity (owner 2026-10-31, [AppMode.locateZoom]) – also when the map still follows but was
     * pinched; the map takes it while tracking (MapController.ensureStartZoom, no camera move – CLAUDE.md camera rule).
     * Plan: unchanged, following resumes at the user's zoom.
     */
    fun locate() {
        val zoom = AppMode.locateZoom(mode, host.activity)
        followMe = true
        if (zoom != null) { host.navStartZoom = zoom; recentreTick++ }
    }

    /** Plan's neutral GPS note after a tap of the user's ([PlanGps]); null = none. [planGpsNoteTick] restarts its time. */
    var planGpsNote by mutableStateOf<PlanGps.Note?>(null)
    var planGpsNoteTick by mutableStateOf(0)
        private set

    /**
     * An edit during a tour needs my position and there is none: in Plan the neutral note (Plan is quiet about GPS, owner
     * 2026-11-06 23:35), elsewhere the error box as before.
     */
    fun noPositionForEdit(note: PlanGps.Note, text: org.jetbrains.compose.resources.StringResource) {
        if (mode == AppMode.PLAN) showPlanGpsNote(note) else notices.sayError(Txt.s(text), NoticePlace.MAP)
    }

    fun showPlanGpsNote(note: PlanGps.Note) {
        Problems.trace("gps", "Plan: neutral note ${note.name} after a tap (no GPS warning in Plan)")
        planGpsNote = note; planGpsNoteTick++
    }

    fun onCompass(mapTurned: Boolean) {
        // one orientation for the session (owner 2026-11-07, MapOrientation): only this tap changes it
        val o = com.onpath.app.domain.map.MapOrientation.of(host.headingUp)
        val hasFix = position.here() != null
        val tap = com.onpath.app.domain.map.MapOrientation.tap(o, mapTurned, followMe, hasFix)
        Problems.trace("map", "Compass tap: $o → ${tap.orientation}" + (if (mapTurned) " (map was turned)" else ""))
        if (tap.noFix) {
            // a tap must not do nothing; Plan says it neutrally, no red box (owner 2026-11-06 23:35 – PlanGps)
            PlanGps.compassWithoutFix(mode == AppMode.PLAN)?.let { showPlanGpsNote(it) }
                ?: notices.sayError(Txt.s(Res.string.msg_no_position_turn), NoticePlace.MAP)
            return
        }
        host.setHeadingUp(tap.orientation == com.onpath.app.domain.map.Orientation.TRAVEL)
        // following north up turns the map to north itself; an extra camera move from the app would make MapLibre drop
        // the tracking (it ends tracking on every app camera move) – so either follow again or turn north, never both
        if (tap.follow == true) { followMe = true; recentreTick++ }
        if (tap.turnNorth) northTick++
    }

    private var scrubWasFollowing = false
    private var scrubEndJob: Job? = null

    /**
     * Navigate: a finger on the elevation profile (owner): a dot marks that spot on the route and the map shows it
     * (following pauses). After the finger leaves, the dot stays [SCRUB_HOLD_MS], then the map returns to the position.
     */
    fun navScrub(p: GeoPoint) {
        scrubEndJob?.cancel(); scrubEndJob = null
        if (followMe) { scrubWasFollowing = true; followMe = false; Problems.trace("map", "Following paused: finger on the elevation profile") }
        host.highlight(p)
        camera.panTo(p)
    }

    fun navScrubEnd() {
        scrubEndJob?.cancel()
        scrubEndJob = scope.launchSafe("app", "navScrubEnd") {
            delay(SCRUB_HOLD_MS)
            host.highlight(null)
            if (scrubWasFollowing && mode == AppMode.NAVIGATE) { followMe = true; recentreTick++ }
            scrubWasFollowing = false
        }
    }

    /** The app is on screen again (e.g. taken out of the pocket): in Navigate jump back to the own position (owner). */
    fun onReturnToApp() {
        if (mode == AppMode.NAVIGATE && position.here() != null) { followMe = true; recentreTick++ }
        appOnScreen = true
        host.appReturned(inPlan = mode == AppMode.PLAN) // T3 in Plan: the next usable fix checks the own area
    }

    /**
     * The app left the screen. Map areas still on their way keep loading through MapLoadService (owner),
     * started when they began; otherwise nothing is polled in the background (map-cells.md §8).
     */
    fun onLeaveApp() { appOnScreen = false; host.appLeft() }

    /** The app is on screen (Compose state: MapHost catches up with newer map data on return). */
    var appOnScreen by mutableStateOf(true)
        private set

    // --- position / camera ---
    var followMe by mutableStateOf(false)

    /**
     * Navigate: where the user turned / moved the map by hand (free look, e.g. to match a mountain range; owner); after
     * walking on [FREE_LOOK_BACK_M] the map follows again by itself, compass / locate at once. null = not looking around.
     */
    var freeLookAt: GeoPoint? = null

    /** Last finger-down on the map (MapHost); a "gesture" without a recent touch is not one. */
    var lastMapTouchMs = 0L

    /**
     * Finger down on the map. Plan with walking direction up: stop turning / following at once, so a route point stays
     * exactly under the finger (a point cannot be held to delete it while the map keeps turning).
     */
    fun onMapTouch() {
        lastMapTouchMs = uptimeMs()
        if (mode == AppMode.PLAN && followMe && host.headingUp) followMe = false
    }

    /**
     * MapLibre stopped following. It does that for finger gestures and also for every camera move of the app itself
     * (fly to a search result, show a route, profile scrub) – both mean "not following any more". Only a stop right
     * after a real touch starts free look (back to following after walking on); a stop without a touch is not ignored –
     * that would undo the app's own camera moves.
     */
    fun onMapGesture() {
        followMe = false
        val touched = uptimeMs() - lastMapTouchMs <= 1_500
        val loc = position.here()
        freeLookAt = if (touched && mode == AppMode.NAVIGATE && loc != null) GeoPoint(loc.lat, loc.lon) else null
    }

    /** Every fix: back to following after free look once the walker moved on. */
    fun checkFreeLook(lat: Double, lon: Double) {
        val at = freeLookAt ?: return
        if (followMe || mode != AppMode.NAVIGATE) { freeLookAt = null; return }
        if (Geo.distance(at.lat, at.lon, lat, lon) < FREE_LOOK_BACK_M) return
        freeLookAt = null
        followMe = true
        recentreTick++
    }
    /** The start mode (Plan / Navigate) is decided (database checked for an unfinished tour). */
    var startModeKnown by mutableStateOf(false)
    /** The user moved or held the map since the app started (StartCamera never jumps after that). */
    var userMovedMap = false
    /** StartCamera ran (centred or gave up) – reset each time the app comes to the front ([onAppStart]). */
    var startCameraDone = false
    /** Bumped by [onAppStart]: restarts the start-camera check in MapScreen. */
    var startCameraGen by mutableStateOf(0)
        private set

    private var backgroundSince = 0L

    /** App goes to the background (Activity.onStop). */
    fun onAppStop() { backgroundSince = elapsedMs() }

    /**
     * App comes to the front (Activity.onStart): centre on the position again unless something is running – on a
     * real start, or after more than 10 min away (a quick look at Google Maps must not move the map).
     */
    fun onAppStart() {
        val away = if (backgroundSince == 0L) Long.MAX_VALUE else elapsedMs() - backgroundSince
        if (away < 10 * 60_000L) return
        // back after a while with a plan: the plan in front, also during a tour (owner 2026-11-06, PlanCamera)
        if (backgroundSince != 0L && mode == AppMode.PLAN && host.waypoints.isNotEmpty()) {
            showPlanWhole(PlanCamera.Moment.BACK_IN_APP); return
        }
        if (host.touring || mode != AppMode.PLAN) return
        // A place was just shared into the app: the map shows it, not the position.
        if (elapsedMs() < host.keepCameraUntilMs) return
        startCameraDone = false; userMovedMap = false; followMe = false; startCameraGen++
    }

    // --- fits ---
    fun hasPlanLine() = (host.routePoints?.size ?: 0) >= 2
    fun hasShownLine() = (host.shown?.segments?.sumOf { it.size } ?: 0) >= 2
    fun hasFollowLine() = (host.followLine?.size ?: 0) >= 2

    /**
     * "Show whole route" (map button, owner 2026-10-31): following stops first (also free look – it would follow
     * again by itself), then the map shows the whole line and its points north up; in Navigate also where you are.
     * Following comes back with the locate button only.
     */
    fun showWholeRoute() {
        val src = WholeRoute.source(mode, hasPlanLine(), hasShownLine(), hasFollowLine()) ?: return
        val here = position.here()?.takeIf { mode == AppMode.NAVIGATE }?.let { GeoPoint(it.lat, it.lon) }
        val pts = when (src) {
            WholeRoute.Source.SHOWN -> host.shown?.let { t -> WholeRoute.bounds(t.segments, t.waypoints.map { GeoPoint(it.lat, it.lon) }, null) }
            WholeRoute.Source.PLANNED -> host.routePoints?.let { r -> WholeRoute.bounds(listOf(r), host.waypoints.toList(), null) }
            // with the original while a new route is followed (drawn thin grey)
            WholeRoute.Source.FOLLOWED -> WholeRoute.bounds(listOfNotNull(host.followLine, host.navOriginal), emptyList(), here)
        } ?: return
        followMe = false
        freeLookAt = null
        camera.fit(pts)
    }

    /**
     * Locate while I am off the map in Plan ([LocateDistance], owner 2026-11-07): the plan (or the shown track) and my
     * position together, in the room [FitPaddingRule] leaves. Following stops first; the bearing follows the session's one
     * orientation (owner 2026-11-07: north up when that is set, else the current bearing – MapController.northLocked).
     */
    fun showPlanAndMe() {
        val me = position.here()?.let { GeoPoint(it.lat, it.lon) } ?: run { locate(); return }
        val pts = host.shown?.let { t -> WholeRoute.bounds(t.segments, t.waypoints.map { GeoPoint(it.lat, it.lon) }, me) }
            ?: WholeRoute.bounds(listOfNotNull(host.routePoints), host.waypoints.toList(), me)
        followMe = false
        freeLookAt = null
        camera.fit(pts) // the session's orientation decides the bearing (MapController.northLocked)
        Problems.trace("map", "Locate off the map: plan and position shown together (${pts.size} points)")
    }

    /**
     * The whole plan in front at a [PlanCamera.Moment] (owner 2026-11-06): once the sheet has settled, the fit of "Show
     * whole route" over the route line and the points (one point: centred) – not when a finger touched the map meanwhile,
     * Plan was left or a track is shown. Following stops first (CLAUDE.md "Map camera").
     */
    fun showPlanWhole(moment: PlanCamera.Moment) {
        val asked = uptimeMs()
        scope.launchSafe("app", "showPlanWhole") {
            delay(WholeRoute.SETTLE_MS)
            val pts = host.waypoints.toList()
            val what = if (mode != AppMode.PLAN) PlanCamera.Show.NOTHING
                else PlanCamera.show(pts.size, touchedSince = lastMapTouchMs > asked, trackShown = host.shown != null)
            if (what == PlanCamera.Show.NOTHING) {
                Problems.trace("map", "Plan not shown whole (${moment.name.lowercase()}): the map was touched, Plan left or nothing to show")
                return@launchSafe
            }
            followMe = false
            freeLookAt = null
            if (what == PlanCamera.Show.ONE_POINT) camera.centerOn(pts[0], PlanCamera.ONE_POINT_ZOOM)
                else camera.fit(WholeRoute.bounds(listOfNotNull(host.routePoints), pts, null))
            Problems.trace("map", "Plan shown whole (${moment.name.lowercase()}, ${pts.size} points)")
        }
    }

    /**
     * The plan's first route is there: the map shows it whole – the same fit as "Show whole route" (north up, the
     * [FitPaddingRule] padding), once the sheet has grown to its route size. Not when the user took the map meanwhile,
     * left Plan or the route changed again. Following stops first (CLAUDE.md "Map camera").
     */
    fun fitFirstRoute(line: List<GeoPoint>, pts: List<GeoPoint>, extraMs: Long = 0L) {
        val asked = uptimeMs()
        scope.launchSafe("app", "fitFirstRoute") {
            delay(WholeRoute.SETTLE_MS + extraMs)
            if (!WholeRoute.fitsFirstRoute(lastMapTouchMs > asked, mode, host.shown != null, host.routePoints == line)) {
                Problems.trace("map", "First route not fitted: the map was touched or the plan moved on")
                return@launchSafe
            }
            followMe = false
            freeLookAt = null
            camera.fit(WholeRoute.bounds(listOf(line), pts, null))
        }
    }

    /** One section on the map (a spot of the list, the way to a point not reached): drawn amber, the map goes there. */
    fun showPiece(piece: List<GeoPoint>, center: Pair<GeoPoint, Double>?) {
        host.closeSpots()
        followMe = false
        host.spotLine(piece)
        if (center != null) camera.centerOn(center.first, center.second) else camera.fit(piece)
    }

    private companion object {
        /** Navigate profile scrubbing: how long the dot and the map stay at the spot after the finger leaves (ms). */
        const val SCRUB_HOLD_MS = 4000L
        /** Free look in Navigate ends after walking this far from where the map was turned / moved by hand. */
        const val FREE_LOOK_BACK_M = 25.0
    }
}
