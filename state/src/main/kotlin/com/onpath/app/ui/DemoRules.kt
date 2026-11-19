package com.onpath.app.ui

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.routing.GeoPoint
import java.util.Locale

/**
 * The pure decisions of the developer-tools demo rides ([DemoRides] in `:app` rides them): which line "Demo: ride"
 * takes, which recorded tours are offered to ride again, and how they are labelled. Bodies as they were in `AppViewModel`
 * (inputs passed instead of read from the view model; [demoPrefix] = `DemoLocation.TOUR_PREFIX`).
 */
object DemoRules {
    /** What "Demo: ride" would ride: the planned route, else the shown track (a GPX file / tour); null = nothing. */
    fun demoLine(routePoints: List<GeoPoint>?, shown: ShownTrack?): Pair<String, List<GeoPoint>>? {
        routePoints?.takeIf { it.size >= 2 }?.let { return "planned route" to it }
        shown?.let { t -> t.segments.flatten().takeIf { it.size >= 2 }?.let { return "track \"${t.name}\"" to it } }
        return null
    }

    /** Developer tools → Demo: the last recorded tours (not demo ones) to ride again at their recorded times. */
    fun recentRecordedTours(tours: List<Tour>, demoPrefix: String): List<Tour> = tours.filter {
        it.kind == TourKind.RECORDED && !it.name.startsWith(demoPrefix) &&
            it.distanceM >= 500.0   // not the empty / few-metre starts
    }.sortedByDescending { it.id }.take(8)

    fun demoTourLabel(t: Tour): String = "ride tour \"${t.name}\" (${"%.1f".format(Locale.ROOT, t.distanceM / 1000)} km, recorded times)"
}
