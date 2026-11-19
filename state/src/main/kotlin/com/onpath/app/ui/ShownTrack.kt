package com.onpath.app.ui

import com.onpath.app.gpx.GpxWaypoint
import com.onpath.app.routing.GeoPoint

/** A track shown on the map (imported GPX, recorded or saved tour). */
data class ShownTrack(
    val tourId: Long?,
    val name: String,
    val segments: List<List<GeoPoint>>,
    val waypoints: List<GpxWaypoint>,
    val distanceM: Double,
    val ascentM: Double,
    val descentM: Double,
    val durationS: Double,
    /** Rides not counted (vehicle detection): drawn grey dashed (bus / car) or purple dashed (lift) beside the track. */
    val rides: List<Pair<List<GeoPoint>, com.onpath.app.rides.RideKind>> = emptyList(),
    /**
     * Grey INFO line in the track panel when opening the track changed the activity ("Activity set to MTB from the
     * file"); stays until the track is closed / replaced or the user picks an activity.
     */
    val note: String? = null,
    /** Grey INFO line after a GPX import that skipped parts of the file (the details are in Diagnostics); stays with the track. */
    val skippedNote: String? = null,
) {
    val points: List<GeoPoint> get() = segments.flatten()
}
