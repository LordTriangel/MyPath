package com.onpath.app.domain.routes

import com.onpath.app.data.Tour

/**
 * The tours the large overview swipes through (owner 2026-11-15, the swipe sketch with „3 von 27“): exactly the list My
 * routes shows, in its order – the Zuletzt row (when it is a tour, not the plan on the map) and then the Gespeichert /
 * Gemacht list with the chosen filter and order. A saved route under Zuletzt shows under Gespeichert too: it is one page.
 * Pure.
 */
object OverviewPages {
    /** The pages: [recent] (the Zuletzt row's tour, null = none) then [listed], each tour once (its first place). */
    fun of(recent: Tour?, listed: List<Tour>): List<Tour> = (listOfNotNull(recent) + listed).distinctBy { it.id }

    /**
     * The pages to show when [tapped] was opened: [pages] when it is among them, else that tour alone (a list that changed
     * under it, a screenshot).
     */
    fun around(pages: List<Tour>, tapped: Tour): List<Tour> = if (pages.any { it.id == tapped.id }) pages else listOf(tapped)

    /** The page of [tapped] in [pages] (0 when it is not there). */
    fun indexOf(pages: List<Tour>, tapped: Tour): Int = pages.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0)
}
