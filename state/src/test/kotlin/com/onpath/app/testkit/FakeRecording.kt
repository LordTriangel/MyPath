package com.onpath.app.testkit

import com.onpath.app.data.Geo
import com.onpath.app.data.TourKind
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.recording.RecState
import com.onpath.app.routing.GeoPoint
import com.onpath.app.ui.tour.TourRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The recording service as a state holder sees it (test kit): the commands it is sent ([TourRecorder] – what
 * `RecordingService.send` carries) and the state it publishes ([state], what `RecordingService.state` gives: null =
 * no tour runs). Behind it the real tables ([JdbcTours]): a start writes the tour row as "active", every fix a point at
 * once, the end marks it "done" – so a test can kill the process mid-tour ([kill]) and look at what a restart finds.
 *
 * It keeps the service's rules a state holder relies on, nothing more: one tour at a time (a second start is
 * ignored), nothing recorded while paused, a new piece after [gap], a point that cannot be written is said
 * ([RecState.saveFailed]) and never thrown. Navigation is not here ([FakeNavService]).
 */
class FakeRecording(val tours: JdbcTours, private val clock: () -> Long = System::currentTimeMillis) : TourRecorder {
    private val _state = MutableStateFlow<RecState?>(null)
    val state: StateFlow<RecState?> get() = _state

    /** Every command received, in order ("start HIKE 2", "continue 7", "pause", "resume", "stop"). */
    val commands = ArrayList<String>()
    /** The tour the last [stop] finished (the service's "finished" event); null = none yet. */
    var finished: Long? = null
        private set

    private var tourId = -1L
    private var paused = false
    private var seq = 0
    private var seg = 0
    private var distance = 0.0
    private var startedMs = 0L
    private var last: GeoPoint? = null
    private var saveFailed = false
    private val track = ArrayList<MutableList<GeoPoint>>()

    val running: Boolean get() = tourId >= 0

    override fun start(activity: String, sac: Int) {
        commands += "start $activity $sac"
        if (running) return // the service ignores a second start while recording
        begin(tours.insertTour(TourKind.RECORDED, "Tour", activity, sac, state = "active", createdMs = clock()), emptyList())
        tours.finishOtherActive(tourId)
    }

    override fun continueTour(tourId: Long) {
        commands += "continue $tourId"
        if (running) return
        begin(tourId, tours.loadSegments(tourId))
    }

    private fun begin(id: Long, stored: List<List<TrackPoint>>) {
        tourId = id; paused = false; saveFailed = false; startedMs = clock()
        track.clear(); stored.forEach { s -> track += s.map { GeoPoint(it.lat, it.lon, it.ele) }.toMutableList() }
        seq = stored.sumOf { it.size }; seg = stored.size // continuing: a new piece after the break
        distance = tours.getTour(id)?.distanceM ?: 0.0
        last = null
        publish()
    }

    override fun pause(pause: Boolean) {
        commands += if (pause) "pause" else "resume"
        if (!running) return
        if (paused && !pause) gap() // resumed: a new piece
        paused = pause
        publish()
    }

    override fun stop() {
        commands += "stop"
        if (!running) return
        try { tours.updateStats(tourId, distance, 0.0, 0.0, (clock() - startedMs) / 1000.0, "done") } catch (e: Exception) { saveFailed = true }
        finished = tourId
        tourId = -1
        _state.value = null
    }

    /** A GPS fix: stored at once and counted, unless paused or no tour runs. */
    fun fix(p: GeoPoint, accuracyM: Float = 5f) {
        if (!running || paused) return
        try {
            tours.appendPoint(tourId, seq, seg, TrackPoint(p.lat, p.lon, p.ele, clock()), accuracyM)
            seq++
            saveFailed = false
        } catch (e: Exception) { saveFailed = true } // said in the state, the tour goes on
        last?.let { distance += Geo.distance(it, p) }
        last = p
        if (track.size <= seg) track += ArrayList<GeoPoint>()
        track.last() += p
        try { tours.updateStats(tourId, distance, 0.0, 0.0, (clock() - startedMs) / 1000.0, "active") } catch (e: Exception) { saveFailed = true }
        publish()
    }

    /** GPS lost for long / a jump: the next fix starts a new piece, nothing is counted across the gap. */
    fun gap() { if (running && track.lastOrNull()?.isNotEmpty() == true) { seg++; last = null } }

    /** The process dies: no state any more, nothing finished – the tour stays "active" in the database. */
    fun kill() { tourId = -1; _state.value = null }

    private fun publish() {
        _state.value = RecState(tourId = tourId, paused = paused, distanceM = distance, ascentM = 0.0, descentM = 0.0,
            elapsedS = (clock() - startedMs) / 1000, gpsLost = false, offRouteM = null, offRouteAlert = false, followName = null,
            track = track.map { it.toList() }, saveFailed = saveFailed)
    }
}
