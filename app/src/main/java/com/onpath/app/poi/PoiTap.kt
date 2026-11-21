package com.onpath.app.poi

import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.onpath.app.debug.Problems
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.geojson.Feature
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Which POI a tap or hold at a screen point means – pure, so it is unit tested. [Candidate]s are the POIs the map
 * shows (screen position, symbol drawn or not). A POI is hit only through its drawn symbol: the finger's box ([pick]
 * boxPx, 24 dp) touches its icon; the nearest POI point wins. A name the map drew without its symbol (an older style,
 * or any case the style check missed) is no POI – the hold is a plain map hold (owner 2026-11-15, Hilden: a bus stop's
 * bare name „Dorothea-Erxleben-Straße" read like a street name and a hold on it opened the place menu). Since
 * 2026-11-17 the style draws POI names only from z17, beside their icon in the same symbol (owner); a name is never a
 * tap target of its own – only the symbol's finger box counts.
 */
object PoiHit {
    class Candidate(val poi: Poi, val x: Float, val y: Float, val icon: Boolean)

    /** Half the icon's size in dp (badge 24 px at icon-size ~1). */
    const val ICON_HALF_DP = 11f

    /**
     * Whether the style layer [id] draws the POI symbol: every `poi-` layer – the icon bands `poi-<x>` and, from z17,
     * `poi-<x>-named` (icon + name in one symbol, tools/make_style.py pois()) – except a text-only `-name` layer (an
     * older style's naming), whose name is never a hit of its own.
     */
    fun symbolLayer(id: String): Boolean = id.startsWith(POI_LAYER_PREFIX) && !id.endsWith("-name")

    fun pick(cands: List<Candidate>, fx: Float, fy: Float, boxPx: Float, dpPx: Float): Poi? {
        val reach = boxPx / 2f + ICON_HALF_DP * dpPx
        return cands.filter { c -> c.icon && abs(fx - c.x) <= reach && abs(fy - c.y) <= reach }
            .minByOrNull { c -> (fx - c.x) * (fx - c.x) + (fy - c.y) * (fy - c.y) }?.poi
    }

    /**
     * Whether the map is idle now: it became idle ([became], the did-become-idle event, only with its style loaded) and
     * neither the camera nor the style changed since ([moved]). Not idle = a did-become-idle is still on its way, so a
     * press that waits for one needs no repaint.
     */
    class IdleTracker {
        private var idle = false
        fun became() { idle = true }
        fun moved() { idle = false }
        fun idleNow(): Boolean = idle
    }

    /** How long a press the list has no POI for waits for the map's next idle; after that it is a plain press. */
    const val PRESS_WAIT_MS = 150L

    /** The field log's words for a drawn POI that was missing from the list (the E2E tests read them too). */
    const val LIST_MISSED = "A tap or hold found a drawn POI the list did not have"

    /**
     * The one press waiting for the map's next idle – it is never asked inside the press, because a query there can
     * wait for a busy render thread (1447 ms, owner's Pixel 2026-11-13). Whoever takes it finishes it, exactly once.
     */
    class PressWait<P : Any> {
        private var press: P? = null
        private var token = 0

        /** [p] waits now; returns its token and the press it replaced (to be finished first, as a plain press). */
        fun put(p: P): Pair<Int, P?> { val old = press; press = p; return ++token to old }

        fun take(): P? = press.also { press = null }

        /** The wait of [t] ran out: its press, unless it was already finished (or a newer one waits). */
        fun expire(t: Int): P? = if (t == token) take() else null
    }

    /**
     * Whether one repaint may be asked for: not while the camera follows the position (it repaints by itself, and a
     * repaint per follow step would keep the map busy), and not while one is on its way. An asked repaint counts as on
     * its way until the next idle, or [staleMs] if none comes (a stopped map), so the flag can never stick.
     */
    class RepaintGate(private val staleMs: Long = 1_000) {
        private var askedAt = -1L
        fun mayAsk(nowMs: Long, following: Boolean): Boolean {
            if (following || (askedAt >= 0 && nowMs - askedAt < staleMs)) return false
            askedAt = nowMs
            return true
        }
        fun idleCame() { askedAt = -1L }
    }

    /**
     * When [ShownPois] reads what the map shows: once, on the first map idle (all tiles in, render thread done) after
     * the camera stopped or the map changed (style / data reload, POI settings) – never on a timer while nothing moves
     * (owner's Pixel 2026-11-13: a read every 2 s on a still map cost battery). The map's own idle events (the
     * position dot redraws it every second) read nothing by themselves.
     */
    class ReadTrigger {
        private var wanted = true

        /** The camera stopped (a pan, zoom, fly or follow step ended). */
        fun cameraIdle() { wanted = true }

        /** What the map shows changed without a camera move: a new style or map data, other POI settings. */
        fun changed() { wanted = true }

        /**
         * The map became idle: true = read now (once). [ready] = the style is fully loaded, so a read can work: an idle
         * while it is not (the new style still loading after a reload) keeps the wish for the next idle – it must not
         * use it up and leave the list empty until the next camera move (owner's Pixel 2026-11-21, 0.18.6: a tap on
         * a drawn symbol showed nothing).
         */
        fun mapIdle(ready: Boolean = true): Boolean = (wanted && ready).also { if (it) wanted = false }

        /** A read is still wanted: the list is known to be behind what the map shows. */
        val pending: Boolean get() = wanted
    }
}

/**
 * The POIs the map shows, read from MapLibre while the map is idle ([onMapIdle], [PoiHit.ReadTrigger]), so a tap or
 * hold looks them up ([at]) without queryRenderedFeatures: that call waits for the render thread – a hold waited
 * 1447 ms for a busy frame in central London (owner's Pixel 2026-11-13); a hold must never block the main thread.
 * The read: queryRenderedFeatures has to run on the main thread (MapLibre Android), on an idle map it does not wait;
 * turning the features into POIs runs on a background thread, the list is swapped in on the main thread. Until a read
 * is done a hold uses the last list. Positions are kept as coordinates and projected at the hold (no render thread
 * involved). A press the list has no POI for waits for the map's next idle ([askMapAtNextIdle]) and asks the map there,
 * so a stale list cannot lose a drawn symbol (owner's Pixel 2026-11-21, cause unproven). Main thread only. Never throws.
 */
class ShownPois {
    private class Entry(val poi: Poi, val icon: Boolean)
    private class WaitingPress(val x: Float, val y: Float, val boxDp: Float, val done: (Poi?) -> Unit)

    @Volatile private var entries: List<Entry> = emptyList()
    private val trigger = PoiHit.ReadTrigger()
    private var seq = 0
    private var doneSeq = 0
    private val idle = PoiHit.IdleTracker()
    private val repaint = PoiHit.RepaintGate()
    private val waiting = PoiHit.PressWait<WaitingPress>()
    private var readAt = 0L
    private var styleLoadsSinceRead = 0

    /** The camera starts or goes on moving: busy until the next idle, and a waiting press is a plain press now. */
    fun cameraMoving() { idle.moved(); finishWaitingPress() }

    /**
     * The camera stopped: the next map idle reads. One repaint is asked for, so that idle really comes AFTER this
     * call: when the last frame was already drawn and its idle came before the camera's idle event (a fast phone with
     * cached tiles), a still map would never idle again and the list would stay that of the previous view.
     */
    fun cameraIdle(map: MapLibreMap) { trigger.cameraIdle(); repaintOnce(map, "camera stop") }

    /** The new style is loaded: read at the next idle – and make sure one comes on a still map. */
    fun styleLoaded(map: MapLibreMap) { idle.moved(); trigger.changed(); repaintOnce(map, "style loaded") }

    private fun repaintOnce(map: MapLibreMap, why: String) {
        if (!repaint.mayAsk(SystemClock.uptimeMillis(), following(map))) return
        try { map.triggerRepaint() } catch (e: Exception) { Problems.warn("poi", "Repaint after $why failed", e) }
    }

    /** The camera follows the position, or that is not readable: no repaint from here. */
    private fun following(map: MapLibreMap): Boolean = try {
        val c = map.locationComponent
        c.isLocationComponentActivated && c.cameraMode != CameraMode.NONE
    } catch (e: Exception) {
        Problems.warn("poi", "Camera mode not readable, no repaint", e)
        true
    }

    /** A new style / map data: forget the old list (it belongs to another style); the next map idle reads. */
    fun styleChanged() {
        idle.moved(); finishWaitingPress()
        entries = emptyList(); seq++; styleLoadsSinceRead++; trigger.changed()
    }

    /** Other POI settings (kinds shown): the next map idle reads. */
    fun changed() = trigger.changed()

    /** The map became idle (render thread done, all tiles in): a waiting press asks it, and it is read when due. */
    fun onMapIdle(map: MapLibreMap, view: View) {
        repaint.idleCame()
        val ready = try { map.style?.isFullyLoaded == true } catch (e: Exception) { Problems.warn("poi", "Style state not readable", e); false }
        if (ready) idle.became()
        if (ready) resolveWaitingPress(map) else finishWaitingPress()
        if (trigger.mapIdle(ready)) read(map, view)
    }

    /** The POI under [x], [y] (pixels), from what the map showed at the last read; null when none. */
    fun at(map: MapLibreMap, x: Float, y: Float, boxDp: Float = 24f): Poi? = try {
        val t0 = SystemClock.uptimeMillis()
        val dp = android.content.res.Resources.getSystem().displayMetrics.density
        val list = entries
        val cands = list.map { e ->
            val s = map.projection.toScreenLocation(LatLng(e.poi.lat, e.poi.lon))
            PoiHit.Candidate(e.poi, s.x, s.y, e.icon)
        }
        PoiHit.pick(cands, x, y, boxDp * dp, dp).also {
            val ms = SystemClock.uptimeMillis() - t0
            if (ms > 50) Problems.trace("poi", "POI lookup at the finger took $ms ms (${list.size} shown POIs)")
        }
    } catch (e: Exception) {
        Problems.warn("poi", "POI lookup at the finger failed, treated as no POI", e)
        null
    }

    /**
     * A tap / hold [at] found nothing for: the press waits for the map's next idle and [done] gets the POI drawn there,
     * or null (a plain press) if none is drawn, the camera or style changes first, or [pressTimedOut] comes. Never asks
     * the map here. Returns the wait's token, or null when it cannot wait (no style yet): the caller goes on at once.
     */
    fun askMapAtNextIdle(map: MapLibreMap, x: Float, y: Float, boxDp: Float = 24f, done: (Poi?) -> Unit): Int? {
        val ready = try { map.style?.isFullyLoaded == true } catch (e: Exception) { Problems.warn("poi", "Style state not readable", e); false }
        if (!ready) return null
        val (token, older) = waiting.put(WaitingPress(x, y, boxDp, done))
        older?.let { finish(it, null) }
        if (idle.idleNow()) repaintOnce(map, "list miss") // not idle: its idle is on the way
        return token
    }

    /** The wait of [token] ran out (a busy map): the press goes on as a plain press. */
    fun pressTimedOut(token: Int) {
        waiting.expire(token)?.let {
            Problems.trace("poi", "No map idle within ${PoiHit.PRESS_WAIT_MS} ms of a press (${listInfo()}), plain press")
            finish(it, null)
        }
    }

    /** A press still waiting goes on as a plain press (a newer press, a camera move, a new style). */
    fun finishWaitingPress() { waiting.take()?.let { finish(it, null) } }

    /** The map host goes away: a waiting press is dropped, nothing is left to act on. */
    fun dropWaitingPress() { waiting.take() }

    private fun finish(p: WaitingPress, poi: Poi?) {
        try { p.done(poi) } catch (e: Exception) { Problems.error("poi", "A press on the map failed after waiting for the map", e) }
    }

    private fun listInfo(): String {
        val ageS = if (readAt == 0L) -1L else (SystemClock.uptimeMillis() - readAt) / 1000
        return "${entries.size} listed POIs, last read ${if (ageS < 0) "never" else "$ageS s ago"}, style loads since: $styleLoadsSinceRead"
    }

    /** On the map's idle only ([onMapIdle]): the list again (a read may have landed), else one small query at the finger. */
    private fun resolveWaitingPress(map: MapLibreMap) {
        val p = waiting.take() ?: return
        val listed = at(map, p.x, p.y, p.boxDp)
        finish(p, listed ?: queryAtFinger(map, p))
    }

    private fun queryAtFinger(map: MapLibreMap, p: WaitingPress): Poi? {
        val behind = trigger.pending || doneSeq != seq // a read is still due or running: a miss is expected
        val info = listInfo()
        return try {
            val dp = android.content.res.Resources.getSystem().displayMetrics.density
            val style = map.style
            if (style == null || !style.isFullyLoaded) return null
            val ids = style.layers.map { it.id }.filter { PoiHit.symbolLayer(it) }
            if (ids.isEmpty()) return null
            val reach = p.boxDp * dp / 2f + PoiHit.ICON_HALF_DP * dp
            val feats = map.queryRenderedFeatures(RectF(p.x - reach, p.y - reach, p.x + reach, p.y + reach), *ids.toTypedArray())
            val cands = feats.mapNotNull { Poi.fromFeature(it) }.distinctBy { it.id }.map { c ->
                val s = map.projection.toScreenLocation(LatLng(c.lat, c.lon))
                PoiHit.Candidate(c, s.x, s.y, true)
            }
            val hit = PoiHit.pick(cands, p.x, p.y, p.boxDp * dp, dp)
            if (hit == null) Problems.trace("poi", "No POI at the finger, in the list or on the map ($info)")
            else {
                val line = "${PoiHit.LIST_MISSED} ($info)"
                if (behind) Problems.trace("poi", "$line – its read was still due")
                else Problems.warn("poi", "$line – the list was stale, read again")
                trigger.changed() // read at this same idle
            }
            hit
        } catch (e: Exception) {
            Problems.warn("poi", "POI query at the finger failed, treated as no POI", e)
            null
        }
    }

    private fun read(map: MapLibreMap, view: View) {
        try {
            val t0 = SystemClock.uptimeMillis()
            val style = map.style ?: return
            if (!style.isFullyLoaded) return
            // symbol layers by their id (PoiHit.symbolLayer): reading a layer's icon image at runtime said "no icon" for
            // every POI layer on the owner's Pixel (2026-11-16 23:35, no POI hit any more) – the id rule is the style's own
            val (iconIds, nameIds) = style.layers.map { it.id }.filter { it.startsWith(POI_LAYER_PREFIX) }
                .partition { PoiHit.symbolLayer(it) }
            val box = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
            fun query(layers: List<String>): List<Feature> =
                if (layers.isEmpty()) emptyList() else map.queryRenderedFeatures(box, *layers.toTypedArray())
            // main thread (MapLibre's rule) – only the two queries
            val iconFeatures = query(iconIds)
            val nameFeatures = query(nameIds)
            val ms = SystemClock.uptimeMillis() - t0
            if (ms > 100) Problems.trace("poi", "Shown POIs read in $ms ms on the main thread (map idle)")
            val mine = ++seq
            worker.execute {
                val list = try {
                    val icons = iconFeatures.mapNotNull { Poi.fromFeature(it) }.distinctBy { it.id }
                    val named = nameFeatures.mapNotNull { Poi.fromFeature(it) }.associateBy { it.id }
                    val iconIds2 = icons.map { it.id }.toSet()
                    icons.map { Entry(it, true) } + named.values.filter { it.id !in iconIds2 }.map { Entry(it, false) }
                } catch (e: Exception) {
                    Problems.warn("poi", "Shown POIs not read, a tap or hold uses the last list", e); null
                }
                if (list != null) view.post { if (mine == seq) { entries = list; doneSeq = mine; readAt = SystemClock.uptimeMillis(); styleLoadsSinceRead = 0 } }
            }
        } catch (e: Exception) {
            Problems.warn("poi", "Shown POIs not read (map idle), a tap or hold uses the last list", e)
        }
    }

    companion object {
        /** One background thread for all maps: turning the read features into POIs. */
        private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "shown-pois").apply { isDaemon = true } }
    }
}
