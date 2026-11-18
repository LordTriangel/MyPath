package com.onpath.app.ui.suggest

import android.app.Application
import com.onpath.app.cells.AndroidNetworkStatus
import com.onpath.app.data.Prefs
import com.onpath.app.data.RoutingDataLoader
import com.onpath.app.debug.Problems
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCards
import com.onpath.app.suggest.app.AppSuggest
import com.onpath.app.suggest.app.SuggestFetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The app's [SuggestHost]: the runner and the fetch of `suggest/app/AppSuggest`, the routing data of the map flow
 * ([routing]); the camera and „Übernehmen“ come from the view model ([fit], [take]).
 */
class AppSuggestHost(
    private val app: Application,
    private val prefs: Prefs,
    private val routing: () -> RoutingDataLoader,
    private val fit: (List<GeoPoint>) -> Unit,
    private val take: (List<GeoPoint>, Activity, Int, (Boolean) -> Unit) -> Unit,
) : SuggestHost {
    override suspend fun suggest(ask: SuggestAsk): SuggestCards = AppSuggest.runner(app).suggest(ask)

    override fun ensureRouting(parts: List<String>): List<String> = routing().ensure(parts)

    override fun missingRouting(parts: List<String>): List<String> = routing().missing(parts)

    override suspend fun fetchCells(cells: List<IntArray>): Boolean {
        // the map setting's Wi-Fi rule (the files are small, ~0.3 MB each, but the user said Wi-Fi only)
        if (prefs.settings.mapsWifiOnly && !AndroidNetworkStatus(app).unmetered()) {
            Problems.info("suggest", "${cells.size} cells without a suggest file – not fetched (maps only on Wi-Fi)")
            return false
        }
        val fetch = AppSuggest.fetch(app) ?: return false
        val ctx = currentCoroutineContext()
        val out = withContext(Dispatchers.IO) { fetch.fetch(cells) { ctx.ensureActive() } }
        Problems.info("suggest", "Suggest files fetched: ${out.entries.joinToString { "${it.key.id} ${it.value}" }}")
        return out.values.any { it == SuggestFetch.Outcome.FETCHED }
    }

    override fun fit(points: List<GeoPoint>) = fit.invoke(points)

    override fun take(points: List<GeoPoint>, activity: Activity, sac: Int, done: (Boolean) -> Unit) =
        take.invoke(points, activity, sac, done)
}
