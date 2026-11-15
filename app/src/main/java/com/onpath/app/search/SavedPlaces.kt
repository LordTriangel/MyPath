package com.onpath.app.search

import com.onpath.app.data.Geo

/**
 * Places the user keeps (owner 2026-11-15, „Orte merken“, sketches B4a5St5QFxAi7dKfG6SLXN + LqFT7h7KhEmKRdGJUVtrG9):
 * [home] („⌂ Zuhause“, one place or none) and [favourites] („★ Favoriten“, newest first). Set in the held place's menu,
 * shown at the top of the empty search. Unlike „Zuletzt gesucht“ (20, the oldest drop out) favourites never fall out.
 * On the phone only – in the app's backups, not synced to the browser planner. Pure.
 */
data class SavedPlaces(val home: Place? = null, val favourites: List<Place> = emptyList()) {

    fun isFavourite(p: Place): Boolean = favourites.any { same(it, p) }

    fun isHome(p: Place): Boolean = home?.let { same(it, p) } == true

    /** Merkt / Gemerkt: a favourite goes (every copy of it), any other place comes in first. */
    fun toggleFavourite(p: Place): SavedPlaces =
        if (isFavourite(p)) copy(favourites = favourites.filterNot { same(it, p) }) else copy(favourites = listOf(p) + favourites)

    /** „Als Zuhause festlegen“ (replaces the old home) / „Zuhause entfernen“ (null). */
    fun withHome(p: Place?): SavedPlaces = copy(home = p)

    /**
     * A restored backup joins what the phone has: the phone's home wins when it has one, favourites of the backup that
     * the phone lacks come after the phone's own.
     */
    fun merge(restored: SavedPlaces): SavedPlaces = SavedPlaces(
        home = home ?: restored.home,
        favourites = favourites + restored.favourites.filter { r -> favourites.none { same(it, r) } }
            .fold(emptyList<Place>()) { acc, r -> if (acc.any { same(it, r) }) acc else acc + r },
    )

    /** What the empty search shows, top to bottom ([home], [favourites] ≤ [SHOWN_FAVOURITES] unless [all], [recent]). */
    data class Start(val home: Place?, val favourites: List<Place>, val moreFavourites: Int, val recent: List<Place>)

    /**
     * The empty search's lists: Zuhause, then the favourites (at most [SHOWN_FAVOURITES]; [all] = every one, after
     * „Alle Favoriten ›“), then „Zuletzt gesucht“ without the places already shown above (a place is listed once).
     */
    fun start(recent: List<Place>, all: Boolean = false): Start {
        val favs = if (all) favourites else favourites.take(SHOWN_FAVOURITES)
        val above = listOfNotNull(home) + favourites
        return Start(home, favs, favourites.size - favs.size, recent.filter { r -> above.none { same(it, r) } })
    }

    companion object {
        /** The search shows this many favourites, then „Alle Favoriten ›“. */
        const val SHOWN_FAVOURITES = 5

        /** Two unnamed places this close are the same spot. */
        const val SAME_SPOT_M = 5.0

        /**
         * The same place: the same id, or the same name nearby ([SearchMerge.samePlace] – a POI held on the map and the
         * same POI from the search), or two unnamed places on the same spot.
         */
        fun same(a: Place, b: Place): Boolean = a.id == b.id || SearchMerge.samePlace(a, b) ||
            (a.name == null && b.name == null && Geo.distance(a.lat, a.lon, b.lat, b.lon) < SAME_SPOT_M)
    }
}
