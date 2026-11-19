package com.onpath.app.debug

/** Summary of frame times in ms (pure, unit tested: FrameStatsTest). */
data class FrameStats(
    val frames: Int, val over16: Int, val over33: Int, val overBudget: Int, val budgetMs: Double,
    val p95Ms: Double, val maxMs: Double, val hz: Int,
) {
    /** "Sheet drag: 74 frames, 3 janky > 16 ms, 0 > 33 ms (p95 9.1 ms, max 18.2 ms; 120 Hz: 5 over 8.3 ms)". */
    fun line(what: String): String =
        "$what: $frames frames, $over16 janky > 16 ms, $over33 > 33 ms (p95 ${fmt(p95Ms)} ms, max ${fmt(maxMs)} ms; " +
            "$hz Hz: $overBudget over ${fmt(budgetMs)} ms)"

    companion object {
        fun of(ms: List<Double>, refreshHz: Float = 60f): FrameStats? {
            if (ms.isEmpty()) return null
            val sorted = ms.sorted()
            val hz = refreshHz.coerceIn(30f, 240f)
            val budget = 1000.0 / hz
            // nearest-rank 95th percentile
            val p95 = sorted[(kotlin.math.ceil(0.95 * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)]
            return FrameStats(ms.size, ms.count { it > 16.0 }, ms.count { it > 33.0 }, ms.count { it > budget + 0.5 },
                budget, p95, sorted.last(), kotlin.math.round(hz).toInt())
        }

        private fun fmt(v: Double) = String.format(java.util.Locale.ROOT, "%.1f", v)
    }
}
