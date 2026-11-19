package com.onpath.app.ui.tour

import com.onpath.app.recording.RecordingClient

/** The four tour commands of the app's typed service client ([com.onpath.app.recording.RecordingClient]). */
fun tourRecorder(client: RecordingClient): TourRecorder = object : TourRecorder {
    override fun start(activity: String, sac: Int) = client.start(activity, sac)
    override fun continueTour(tourId: Long) = client.continueTour(tourId)
    override fun pause(pause: Boolean) = client.pause(pause)
    override fun stop() = client.stop()
}
