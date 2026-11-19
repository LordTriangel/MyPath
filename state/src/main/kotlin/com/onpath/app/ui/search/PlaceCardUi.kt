package com.onpath.app.ui.search

import com.onpath.app.search.Place

/** What the card shows besides the place: its role in the route now, or what adding it would do. */
data class PlaceCardUi(
    val place: Place,
    /** Index in the waypoint list when the place already is a route point. */
    val routeIndex: Int? = null,
    val waypointCount: Int = 0,
    /** "fits best there · about +0.9 km, +15 min" (AddPreview) or "is stop 2". */
    val subtitle: String = "",
    /** What the main button does: "Set as destination", "Add as stop 2", … (AddPreview.label). */
    val addLabel: String = "",
    /** The main button already sets the start / the destination: the small button for it is left out. */
    val addIsStart: Boolean = false,
    val addIsDestination: Boolean = false,
)
