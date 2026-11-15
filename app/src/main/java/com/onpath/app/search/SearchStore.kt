package com.onpath.app.search

import android.content.Context
import com.onpath.app.debug.Problems
import com.onpath.app.routing.GeoPoint
import java.util.Locale

/**
 * Small persisted search state: the last 20 picked places ("Recent", owner 2026-11-15: 8 was too few) and the names of route points
 * that were added from a place (so stops can be listed as "Leiter Alm" instead of "Point 3").
 * Only OSM / Photon data is stored – never anything from Google, except a place the user shared into OnPath
 * themselves (owner 2026-11-02: "zuletzt habe ich es ja gesucht"): it goes to "Recent" with its share sign.
 */
class SearchStore(context: Context) {
    private val sp = context.getSharedPreferences("onpath-search", 0)
    private val places = context.getSharedPreferences(PLACES_PREFS, 0)

    fun recent(): List<Place> = sp.getString("recent", null).orEmpty().lines().mapNotNull { decode(it) }

    fun addRecent(p: Place) {
        val list = (listOf(p) + recent().filter { it.id != p.id && !SearchMerge.samePlace(it, p) }).take(MAX_RECENT)
        sp.edit().putString("recent", list.joinToString("\n") { encode(it) }).apply()
    }

    fun clearRecent() { sp.edit().remove("recent").apply() }

    /**
     * Zuhause and the favourite places ([SavedPlaces], owner 2026-11-15) – their own file `onpath-places`, which Android's
     * backup takes along (res/xml backup rules); the search prefs (recent, names) stay out of it. Never throws: an
     * unreadable entry is skipped (reported), an unreadable file = nothing kept.
     */
    fun saved(): SavedPlaces = try {
        SavedPlaces(home = places.getString("home", null)?.let { decode(it) },
            favourites = places.getString("favourites", null).orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { l ->
                decode(l) ?: null.also { Problems.warn("search", "A favourite place could not be read – left out") }
            })
    } catch (e: Exception) {
        Problems.warn("search", "Favourite places / Zuhause unreadable – none shown", e); SavedPlaces()
    }

    fun save(s: SavedPlaces) {
        try {
            places.edit().putString("home", s.home?.let { encode(it) })
                .putString("favourites", s.favourites.joinToString("\n") { encode(it) }).apply()
        } catch (e: Exception) { Problems.error("search", "Favourite places / Zuhause could not be saved", e) }
    }

    fun names(): Map<GeoPoint, String> = sp.getString("names", null).orEmpty().lines().mapNotNull { l ->
        val p = l.split('\t'); if (p.size < 3) return@mapNotNull null
        val lat = p[0].toDoubleOrNull() ?: return@mapNotNull null; val lon = p[1].toDoubleOrNull() ?: return@mapNotNull null
        GeoPoint(lat, lon) to p[2]
    }.toMap()

    fun saveNames(names: Map<GeoPoint, String>) {
        sp.edit().putString("names", names.entries.toList().takeLast(200)
            .joinToString("\n") { (g, n) -> "${g.lat}\t${g.lon}\t${clean(n)}" }).apply()
    }

    companion object {
        const val MAX_RECENT = 20
        /** Zuhause + favourites: in Android's backup (backup_rules.xml / data_extraction_rules.xml). */
        const val PLACES_PREFS = "onpath-places"
        private fun clean(s: String?) = s.orEmpty().replace('\t', ' ').replace('\n', ' ')

        fun encode(p: Place): String = listOf(p.id, clean(p.name), p.kind, p.sub.orEmpty(),
            String.format(Locale.ROOT, "%.6f", p.lat), String.format(Locale.ROOT, "%.6f", p.lon),
            p.ele?.toString().orEmpty(), clean(p.address), p.source.name,
            clean(p.town), clean(p.village), if (p.shared) "s" else "").joinToString("\t")

        fun decode(l: String): Place? {
            val p = l.split('\t'); if (p.size < 9) return null
            return Place(p[0], p[1].ifEmpty { null }, p[2], p[4].toDoubleOrNull() ?: return null, p[5].toDoubleOrNull() ?: return null,
                p[6].toIntOrNull(), p[3].ifEmpty { null }, p[7].ifEmpty { null },
                // expected: unknown/old source name → POI
                runCatching { Place.Source.valueOf(p[8]) }.getOrDefault(Place.Source.POI),
                // fields 10–11 (town, village) are missing in older entries
                town = p.getOrNull(9)?.ifEmpty { null }, village = p.getOrNull(10)?.ifEmpty { null },
                // field 12: shared into OnPath (missing in older entries)
                shared = p.getOrNull(11) == "s")
        }
    }
}
