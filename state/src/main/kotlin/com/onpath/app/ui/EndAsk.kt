package com.onpath.app.ui

/** What "End tour" asks: the short question, or the ride summary when a bus / car / lift was found. */
sealed interface EndAsk {
    data object Plain : EndAsk
    data class Rides(val tourId: Long, val summary: com.onpath.app.rides.RideView.Summary) : EndAsk
}
