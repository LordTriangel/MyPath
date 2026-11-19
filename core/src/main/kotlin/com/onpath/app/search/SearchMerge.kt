package com.onpath.app.search

import com.onpath.app.data.Geo

/** Merging offline and online results. */
object SearchMerge {
    const val SAME_PLACE_M = 100.0

    /** Same place: one of the names matches (normalised) and they are less than 100 m apart. */
    fun samePlace(a: Place, b: Place): Boolean {
        if (Geo.distance(a.lat, a.lon, b.lat, b.lon) >= SAME_PLACE_M) return false
        val an = (listOfNotNull(a.name) + a.alt).map { SearchText.normalize(it) }.toSet()
        return (listOfNotNull(b.name) + b.alt).any { SearchText.normalize(it) in an }
    }

    /** Online results not already among the offline ones (the offline copy wins: it has more details). */
    fun onlineExtras(offline: List<Place>, online: List<Place>): List<Place> =
        online.filter { o -> offline.none { samePlace(it, o) || sameStreet(it, o) } }

    /** An offline street (point anywhere on it) and Photon's piece of it: same name, under 2 km apart. */
    private fun sameStreet(off: Place, on: Place): Boolean {
        if (on.kind != "street" || off.kind != "street" || Geo.distance(off.lat, off.lon, on.lat, on.lon) >= 2000.0) return false
        val n = SearchText.normalize(on.name)
        return off.alt.any { SearchText.normalize(it) == n }
    }
}
