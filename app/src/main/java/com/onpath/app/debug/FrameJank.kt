package com.onpath.app.debug

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window

/**
 * Frame times while something moves (the plan sheet on a 120 Hz phone). [start] listens to the window's FrameMetrics
 * (API 24+, no extra library), [stop] returns the summary of the frames since. The plan sheet writes it into the
 * Problems events trace ("Sheet drag: N frames, J janky (p95 X ms)"), so a swipe gives numbers in the diagnostics
 * export. The map host measures each map gesture with it too (pan / zoom → map/ReliefAuto, "Map pan: …"). Never throws.
 */
class FrameJankMeter(private val window: Window) {
    private val durationsNs = ArrayList<Long>()
    private var listener: Window.OnFrameMetricsAvailableListener? = null

    fun start() {
        if (listener != null) return
        synchronized(durationsNs) { durationsNs.clear() }
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val ns = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            synchronized(durationsNs) { durationsNs.add(ns) }
        }
        try {
            window.addOnFrameMetricsAvailableListener(l, handler)
            listener = l
        } catch (e: Exception) {
            Problems.warn("ui", "Frame timing not available; no frame numbers for sheet moves or map gestures", e)
        }
    }

    /** Stops listening; null when it was not running or no frame was drawn. */
    fun stop(): FrameStats? {
        val l = listener ?: return null
        listener = null
        try {
            window.removeOnFrameMetricsAvailableListener(l)
        } catch (e: Exception) {
            Problems.warn("ui", "Frame timing listener could not be removed", e)
        }
        val ns = synchronized(durationsNs) { durationsNs.toList().also { durationsNs.clear() } }
        return FrameStats.of(ns.map { it / 1_000_000.0 }, refreshHz())
    }

    private fun refreshHz(): Float = try {
        window.decorView.display?.refreshRate ?: 60f
    } catch (e: Exception) {
        Problems.info("ui", "Display refresh rate unknown; 60 Hz assumed", e); 60f
    }

    private companion object {
        val handler: Handler by lazy { Handler(HandlerThread("onpath-frames").apply { start() }.looper) }
    }
}
