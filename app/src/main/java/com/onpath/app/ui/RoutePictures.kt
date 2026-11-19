package com.onpath.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onpath.app.data.AppSettings
import com.onpath.app.data.DataFiles
import com.onpath.app.data.Tour
import com.onpath.app.data.TourDb
import com.onpath.app.data.TourKind
import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.domain.AppMode
import com.onpath.app.domain.RouteShape
import com.onpath.app.recording.RecState
import com.onpath.app.routing.GeoPoint
import com.onpath.app.ui.routes.RoutesState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** What the map pictures read from the view model (phase 2, piece 6). */
interface RoutePicturesHost {
    val mode: AppMode
    val recState: StateFlow<RecState?>
    val settings: AppSettings
    val dataFiles: DataFiles
    val devToolsShown: Boolean
    fun detailedAreas(): List<DoubleArray>
    fun packageAreas(): List<DoubleArray>
    suspend fun followedPlanLine(t: Tour): List<GeoPoint>
}

/**
 * My routes' row pictures and the large overviews' map pictures: the files, the two background makers, the snapshot of
 * the app's own map style. Stays in `:app` (bitmaps, the MapLibre snapshotter); its decisions already live in
 * `map/ThumbRules`, `map/OverviewRules` and the makers. Moved out of `AppViewModel` as they were (piece 6 of the
 * rebuild); the view model's same-name members forward here. Nothing is read at construction.
 */
class RoutePictures(
    private val app: Application,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val db: TourDb,
    private val routes: RoutesState,
    private val host: RoutePicturesHost,
) {
    /**
     * My routes' map pictures (owner 2026-11-13, L1: a square map + the route left of each row): files by uuid
     * ([com.onpath.app.map.RouteThumbs], `files/thumbs/`, not backed up), made in the background by [thumbMaker] after
     * every change of the list ([refreshTours]) and at a tour's end – never while navigating or recording. [thumbVersion]
     * moves on when one is stored, so the rows read it again.
     */
    val thumbs by lazy { com.onpath.app.map.RouteThumbs(java.io.File(app.filesDir, "thumbs")) }
    var thumbVersion by mutableStateOf(0)
        private set
    private val thumbMaker by lazy {
        com.onpath.app.map.ThumbMaker(thumbs, scope,
            binned = { db.listDeleted().mapTo(HashSet()) { it.uuid } },
            lineKey = { t -> db.lineKey(t.id) },
            line = { t -> routes.line(t) { emptyList() } },
            style = { snapshotStyle() },
            snap = { json, segs, t -> com.onpath.app.map.ThumbMaker.render(app, json, segs, argbOf(RouteShape.tint(t.kind))) },
            busy = { host.mode == AppMode.NAVIGATE || host.recState.value != null },
            made = { scope.launchSafe("routes", "Map picture shown", Dispatchers.Main) { thumbVersion++ } })
    }

    /**
     * The large overview's map pictures kept on the phone (owner 2026-11-15, swiping through My routes' overviews):
     * `files/overviews/` ([com.onpath.app.map.OverviewPictures], not backed up) – made when an overview opens
     * ([overviewShot]) and in the background for the newest 20 tours + the favourites ([overviewMaker], kicked with the
     * row pictures), never while navigating or recording.
     */
    val overviewPictures by lazy { com.onpath.app.map.OverviewPictures(java.io.File(app.filesDir, "overviews")) }
    private val overviewMaker by lazy {
        com.onpath.app.map.OverviewMaker(overviewPictures, scope,
            binned = { db.listDeleted().mapTo(HashSet()) { it.uuid } },
            lineKey = { t -> com.onpath.app.map.ThumbRules.key(t, db.lineKey(t.id)) },
            under = { t -> host.followedPlanLine(t) },
            line = { t -> withContext(io) { routes.line(t) { emptyList() } } },
            theme = { withContext(Dispatchers.Main) { host.settings.mapTheme } },
            snap = { segs, t, under, w, h, pad -> routeSnapshot(segs, RouteShape.tint(t.kind), w, h, pad, under) },
            busy = { host.mode == AppMode.NAVIGATE || host.recState.value != null })
    }

    /**
     * The overview's map picture of [t]: the stored one at once when it was made for this line, plan, size and theme
     * ([com.onpath.app.map.OverviewRules.key]), else a new snapshot ([routeSnapshot]) that is stored for the next time.
     * Null = no picture (the overview keeps the drawn shape). The plan on the map (no uuid) is never stored.
     */
    suspend fun overviewShot(t: Tour, segments: List<List<GeoPoint>>, tint: RouteShape.Tint, widthPx: Int, heightPx: Int,
                             paddingPx: Int, under: List<GeoPoint> = emptyList()): com.onpath.app.map.RouteSnapshot.Shot? {
        val store = t.uuid.isNotEmpty() && t.id >= 0
        val theme = withContext(Dispatchers.Main) { host.settings.mapTheme }
        val key = if (!store) null else withContext(io) {
            // the background makes pictures of this size – a done tour's look back has its own (owner 2026-11-15)
            overviewPictures.setSize(widthPx, heightPx, paddingPx, done = t.kind == TourKind.RECORDED)
            runCatching { com.onpath.app.map.OverviewRules.key(com.onpath.app.map.ThumbRules.key(t, db.lineKey(t.id)), under, widthPx, heightPx, theme) }
                .onFailure { Problems.info("routes", "Overview picture key not read – drawn without keeping it", it) }.getOrNull()
        }
        if (key != null) withContext(io) {
            overviewPictures.bytes(t.uuid, key)?.let { (m, b) ->
                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { com.onpath.app.map.RouteSnapshot.Shot(it, m.startX, m.startY, m.goalX, m.goalY) }
                    ?: null.also { Problems.info("routes", "Overview picture of ${t.uuid} not decoded – drawn again") }
            }
        }?.let { return it }
        val shot = routeSnapshot(segments, tint, widthPx, heightPx, paddingPx, under) ?: return null
        if (key != null) withContext(io) { com.onpath.app.map.OverviewMaker.store(overviewPictures, t.uuid, key, shot) }
        return shot
    }

    /** How long the last map pictures took (ms each) – diagnostics and the emulator test. */
    val thumbTimings: List<Long> get() = thumbMaker.timings.toList()

    /**
     * The overview's map picture ([com.onpath.app.map.RouteSnapshot]): the app's own style (offline cells, overview map
     * where there is no detail) with the line fitted. Null when the map cannot be drawn (reported) – the overview then
     * keeps the drawn shape.
     */
    suspend fun routeSnapshot(segments: List<List<GeoPoint>>, tint: RouteShape.Tint, widthPx: Int, heightPx: Int,
                              paddingPx: Int, under: List<GeoPoint> = emptyList()): com.onpath.app.map.RouteSnapshot.Shot? {
        val json = snapshotStyle() ?: return null
        val color = argbOf(tint)
        val planBlue = RouteShape.Tint.PLAN.color().let { c ->
            android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
        }
        return com.onpath.app.map.RouteSnapshot.make(app, json, segments, color, widthPx, heightPx, paddingPx,
            app.resources.displayMetrics.density, under, planBlue)
    }

    /** The map style a snapshot draws (the app's own: offline cells, the overview map elsewhere); null = failed (reported). */
    private suspend fun snapshotStyle(): String? {
        val (dataDir, theme, mapDir) = withContext(Dispatchers.Main) { Triple(host.dataFiles.dir, host.settings.mapTheme, host.dataFiles.mapDir()) }
        val (cover, dev, packages) = withContext(Dispatchers.Main) { Triple(host.detailedAreas(), host.devToolsShown, host.packageAreas()) }
        return withContext(io) {
            runCatching { com.onpath.app.map.StyleLoader.load(app, dataDir, theme, mapDir, cover, dev, packages) }
                .onFailure { Problems.warn("routes", "Overview: map style could not be built – the shape is shown alone", it) }
                .getOrNull()
        }
    }

    private fun argbOf(tint: RouteShape.Tint): Int = tint.color().let { c ->
        android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }

    /** Looks for map pictures to make among My routes' list (background, one at a time); [firstUuid] first. */
    fun kickThumbs(firstUuid: String? = null) {
        try { thumbMaker.kick(routes.tours, firstUuid) } catch (e: Exception) { Problems.info("routes", "Map pictures not started – the rows keep their lines", e) }
        try { overviewMaker.kick(routes.tours) } catch (e: Exception) { Problems.info("routes", "Overview pictures not started – drawn when opened", e) }
    }
}
