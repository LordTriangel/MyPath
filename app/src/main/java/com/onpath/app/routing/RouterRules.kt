package com.onpath.app.routing

import com.onpath.strings.*

/** The pure rules of the BRouter engine (profile per activity, via correction, wait counter) – no Android, no btools. */
object RouterRules {
    /** How long the last request on this thread waited for the engine lock (ms) – for the per-route log line. */
    internal val lastLockWait = ThreadLocal<Long>()

    /** Ms the last limited request on the calling thread waited for another route to finish (0 = none / unknown). */
    fun lastLockWaitMs(): Long = lastLockWait.get() ?: 0L

    val CANCELLED: String get() = Txt.s(Res.string.plan_err_cancelled)

    /**
     * Longest out-and-back spike (one way) BRouter cuts at a via point – and the Planner at a leg join (its
     * trimSpike, PLANNER_VERSION 4). Much shorter for hiking, where a via point on a spur is usually a deliberate
     * goal (summit, viewpoint): 250 m cut off a point 100 m up a spur. 50 m still trims small snapping spikes.
     */
    internal fun viaCorrectionM(activity: Activity): Double =
        if (activity == Activity.HIKE) VIA_CORRECTION_HIKE_M else VIA_CORRECTION_M
    private const val VIA_CORRECTION_M = 400.0
    private const val VIA_CORRECTION_HIKE_M = 50.0

    /**
     * Profile per activity. Bike (BIKE_TOURING) and E-bike = touring.brf, OnPath's copy of trekking.brf for a normal bike
     * (owner: paved bike ways and quiet ways, traffic and rough gravel avoided). E-bike changes only power, speed and
     * mass (keyValues).
     */
    internal fun profileFile(activity: Activity): String = when (activity) {
        Activity.BIKE_TOURING, Activity.EBIKE -> "touring.brf"
        Activity.BIKE_TREKKING -> "trekking.brf"
        Activity.MTB, Activity.EMTB -> "mtb.brf"
        Activity.HIKE -> "hiking-mountain.brf"
    }
}
