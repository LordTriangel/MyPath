package com.onpath.app.live

/**
 * Cuts recorded points into track chunks (docs/design/live-tracking.md §5): whole points only, each chunk within the
 * size an item may have. After hours without network the uploader calls [next] again and again from its cursor until
 * the way is caught up.
 */
object LiveChunker {
    /** Most points of one chunk (keeps a chunk's decode small on the viewer's phone). */
    const val MAX_POINTS = 4000

    /** A chunk and how many of the given points it holds. */
    class Cut(val bytes: ByteArray, val count: Int)

    /**
     * The first chunk of [points] – as many as fit into [maxBytes] (at most [MAX_POINTS]) – or null when there are none.
     * The chunk is exactly `LiveItem.chunk(points.take(count))`, so sending the same points again gives the same bytes.
     */
    fun next(points: List<LivePoint>, maxBytes: Int = LiveItem.MAX_PLAIN): Cut? {
        if (points.isEmpty()) return null
        val cap = minOf(points.size, MAX_POINTS)
        var n = fitting(points, cap, maxBytes, ele = points.take(cap).all { it.eleM != null })
        // elevation is written only when every point of the chunk has one: a shorter cut may gain the elevation and
        // grow, so measure the real chunk and step back until it fits
        while (n > 1 && LiveItem.chunk(points.subList(0, n)).size > maxBytes) n--
        return Cut(LiveItem.chunk(points.subList(0, n)), n)
    }

    /** How many of the first [cap] points fit (at least one: a single point is always sent). */
    private fun fitting(points: List<LivePoint>, cap: Int, maxBytes: Int, ele: Boolean): Int {
        val out = LiveOut()
        val w = LiveItem.PointWriter(out, ele)
        val header = 3 + 3 // version, kind, flags + the count's varint (≤ MAX_POINTS → 2 bytes, one spare)
        var n = 0
        while (n < cap) {
            w.add(points[n])
            if (header + out.size > maxBytes) break
            n++
        }
        return maxOf(1, n)
    }
}
