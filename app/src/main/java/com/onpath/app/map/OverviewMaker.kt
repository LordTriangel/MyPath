package com.onpath.app.map

import com.onpath.app.data.Tour
import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.routing.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Makes the large overview pictures ([OverviewPictures]) in the background (owner 2026-11-15: swiping through the tours is
 * smooth from the start) for [OverviewRules.wanted] – the newest 20 tours and every favourite – at the size the overview
 * had at its last opening (none before the first opening). Called where the row pictures are made ([ThumbMaker.kick]:
 * a save, a tour's end, a sync, My routes opening). One at a time, a pause between two, never while [busy]; a picture
 * not made is an INFO and the overview draws it when opened. The folder is kept at [OverviewRules.CAP].
 */
class OverviewMaker(
    private val pics: OverviewPictures,
    private val scope: CoroutineScope,
    /** The recycle bin's uuids (their pictures stay). */
    private val binned: () -> Set<String>,
    /** The key of a tour's line ([ThumbRules.key] with TourDb.lineKey); off the main thread. */
    private val lineKey: (Tour) -> String,
    /** The followed plan's line under a recorded tour (empty = none). */
    private val under: suspend (Tour) -> List<GeoPoint>,
    /** The tour's line as the overview draws it. */
    private val line: suspend (Tour) -> List<List<GeoPoint>>,
    /** The map theme now (part of the key). */
    private val theme: suspend () -> String,
    /** The picture of a tour (segments, tour, under, w, h, padding) – the overview's own snapshot; null = not made. */
    private val snap: suspend (List<List<GeoPoint>>, Tour, List<GeoPoint>, Int, Int, Int) -> RouteSnapshot.Shot?,
    /** Navigating or recording: no picture now. */
    private val busy: () -> Boolean,
    private val io: CoroutineContext = Dispatchers.IO,
    private val pauseMs: Long = ThumbMaker.PAUSE_MS,
) {
    private val running = AtomicBoolean(false)
    @Volatile private var again = false
    @Volatile private var latest: List<Tour> = emptyList()
    /** uuid → the row signature + size + theme already found up to date in this run (no database work for it again). */
    private val checked = ConcurrentHashMap<String, String>()

    /** Look for pictures to make among [tours] (My routes' list). Returns at once. */
    fun kick(tours: List<Tour>) {
        latest = tours
        if (!running.compareAndSet(false, true)) { again = true; return }
        scope.launchSafe("routes", "Overview pictures", io) {
            try {
                do { again = false; pass(latest) } while (again)
            } finally { running.set(false) }
        }
    }

    private suspend fun pass(tours: List<Tour>) {
        if (busy() || tours.isEmpty()) return
        val wanted = OverviewRules.wanted(tours)
        tidy(tours, wanted)
        val (w, h, pad) = pics.size() ?: return // the overview was never opened: its size is not known yet
        val th = theme()
        for (t in wanted) {
            if (busy()) { Problems.trace("routes", "Overview pictures wait: a tour runs"); return }
            val sig = ThumbRules.sig(t) + " ${w}x$h $th"
            if (checked[t.uuid] == sig) continue
            val u = under(t)
            val key = OverviewRules.key(lineKey(t), u, w, h, th)
            if (pics.meta(t.uuid)?.key == key) { checked[t.uuid] = sig; continue }
            val segs = line(t).filter { it.size >= 2 }
            if (segs.isEmpty()) { checked[t.uuid] = sig; continue } // nothing to draw: the overview shows its sign
            val t0 = System.currentTimeMillis()
            val shot = snap(segs, t, u, w, h, pad)
            if (shot == null) {
                checked[t.uuid] = sig // not again in this run; the overview tries when opened
                Problems.info("routes", "Overview picture of a tour not made in the background – drawn when opened")
                continue
            }
            try {
                if (store(pics, t.uuid, key, shot)) {
                    checked[t.uuid] = sig
                    Problems.trace("routes", "Overview picture made in the background in ${System.currentTimeMillis() - t0} ms")
                }
            } finally { shot.bitmap.recycle() }
            delay(pauseMs) // low priority: the app's own work goes first
        }
        tidy(tours, wanted)
    }

    private fun tidy(tours: List<Tour>, wanted: List<Tour>) {
        val keep = tours.mapTo(HashSet()) { it.uuid } + binned()
        for (uuid in OverviewRules.evict(pics.usage(), keep, wanted.mapTo(HashSet()) { it.uuid })) { pics.remove(uuid); checked.remove(uuid) }
    }

    companion object {
        /** Encodes [shot] and stores it for [key]; false = not stored (reported). Does not recycle the bitmap. */
        fun store(pics: OverviewPictures, uuid: String, key: String, shot: RouteSnapshot.Shot): Boolean {
            val bytes = try { ThumbMaker.encode(shot.bitmap) } catch (e: Exception) {
                Problems.info("routes", "Overview picture not encoded – drawn again next time", e); return false
            }
            return pics.write(uuid, OverviewPictures.Meta(key, shot.startX, shot.startY, shot.goalX, shot.goalY), bytes)
        }
    }
}
