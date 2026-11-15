package com.onpath.web

import com.onpath.app.data.Format
import com.onpath.app.data.Geo
import com.onpath.app.debug.Problems
import com.onpath.app.search.Place
import com.onpath.app.search.SavedPlaces
import com.onpath.app.search.SearchMerge

/**
 * Kept places in the planner (owner 2026-11-15, „Orte merken“, F-PLAN-14; docs/design/web-planner.md §32): the app's
 * own rules ([SavedPlaces], shared code) over what the page keeps in this browser (web/app/src/keptplaces.ts).
 *
 * A place travels between page and engine as ONE line – the app's SearchStore line (id, name, kind, sub, lat, lon,
 * ele, address, source, town, village, shared; tab-separated, 6 decimals), mirrored here because SearchStore is
 * Android code. The page stores these lines untouched, so a later phone ⇄ browser sync can carry them as the phone
 * stores them. Lists of places = lines joined with "\n"; home = one line or "".
 */
object WebKept {
    /** The app's SearchStore.MAX_RECENT (owner 2026-11-15: 20). */
    const val MAX_RECENT = 20

    private fun clean(s: String?) = s.orEmpty().replace('\t', ' ').replace('\n', ' ')

    /** Six decimals without a locale (SearchStore: String.format(Locale.ROOT, "%.6f")). */
    internal fun fixed6(v: Double): String {
        val neg = v < 0
        val micro = Math.round(kotlin.math.abs(v) * 1_000_000.0)
        val whole = micro / 1_000_000; val frac = (micro % 1_000_000).toString().padStart(6, '0')
        return (if (neg && micro != 0L) "-" else "") + whole + "." + frac
    }

    /** The app's SearchStore.encode. */
    fun encode(p: Place): String = listOf(p.id, clean(p.name), p.kind, p.sub.orEmpty(), fixed6(p.lat), fixed6(p.lon),
        p.ele?.toString().orEmpty(), clean(p.address), p.source.name, clean(p.town), clean(p.village),
        if (p.shared) "s" else "").joinToString("\t")

    /** The app's SearchStore.decode: null for a line it cannot read. */
    fun decode(l: String): Place? {
        val p = l.split('\t'); if (p.size < 9) return null
        return Place(p[0], p[1].ifEmpty { null }, p[2], p[4].toDoubleOrNull() ?: return null, p[5].toDoubleOrNull() ?: return null,
            p[6].toIntOrNull(), p[3].ifEmpty { null }, p[7].ifEmpty { null },
            Place.Source.entries.firstOrNull { it.name == p[8] } ?: Place.Source.POI, // expected: unknown / old name → POI
            town = p.getOrNull(9)?.ifEmpty { null }, village = p.getOrNull(10)?.ifEmpty { null },
            shared = p.getOrNull(11) == "s")
    }

    private fun places(lines: String): List<Place> = lines.split('\n').filter { it.isNotBlank() }.mapNotNull { l ->
        decode(l) ?: null.also { Problems.warn("search", "A kept place could not be read – left out") }
    }

    private fun saved(home: String, favourites: String) = SavedPlaces(home = home.takeIf { it.isNotBlank() }?.let(::decode), favourites = places(favourites))

    /** Is [code] a favourite / the home place (the menu's „★ Gemerkt“ / „Zuhause entfernen“)? JSON {favourite, home}. */
    fun state(home: String, favourites: String, code: String): String {
        val p = decode(code) ?: return "{\"favourite\":false,\"home\":false}"
        val s = saved(home, favourites)
        return "{\"favourite\":" + s.isFavourite(p) + ",\"home\":" + s.isHome(p) + "}"
    }

    /**
     * One change ([op]: keep, unkeep, setHome, clearHome) by the app's rules: keep / unkeep = SavedPlaces.toggleFavourite
     * when the place is not / is a favourite (every copy of it goes), setHome / clearHome = withHome. JSON {home, favourites}.
     */
    fun apply(home: String, favourites: String, op: String, code: String): String {
        var s = saved(home, favourites)
        val p = decode(code)
        s = when (op) {
            "keep" -> if (p == null || s.isFavourite(p)) s else s.toggleFavourite(p)
            "unkeep" -> if (p == null || !s.isFavourite(p)) s else s.toggleFavourite(p)
            "setHome" -> if (p == null) s else s.withHome(p)
            "clearHome" -> s.withHome(null)
            else -> s
        }
        return "{\"home\":" + (s.home?.let { str(encode(it)) } ?: "null") + ",\"favourites\":" +
            s.favourites.joinToString(",", "[", "]") { str(encode(it)) } + "}"
    }

    /** The app's SearchStore.addRecent: [code] first, the same place not twice, at most [MAX_RECENT]. Lines. */
    fun addRecent(recent: String, code: String): String {
        val p = decode(code) ?: return recent
        return (listOf(p) + places(recent).filter { it.id != p.id && !SearchMerge.samePlace(it, p) }).take(MAX_RECENT)
            .joinToString("\n") { encode(it) }
    }

    /**
     * The empty search (the app's SearchSession.startLists + SearchScreen.recentSection): Zuhause, the favourites (five
     * unless [all] – „Alle Favoriten ›“), „Zuletzt gesucht“ without the places shown above. Each row: the place's
     * search-row fields (WebSearch's hit), its [code] and the grey part of the one-line row (SearchScreen.compactLine:
     * kind · height · distance; Zuhause: the place's name · distance), distance from ([lat], [lon]).
     */
    fun start(home: String, favourites: String, recent: String, all: Boolean, lat: Double, lon: Double): String {
        val s = saved(home, favourites).start(places(recent), all)
        fun row(p: Place, withTitle: Boolean = false): String {
            val d = Geo.distance(lat, lon, p.lat, p.lon)
            val line = buildList {
                add(if (withTitle) p.title() else p.kindInfo.label)
                if (!withTitle) p.ele?.let { add("$it m") }
                add(Format.distance(d))
            }.joinToString(" · ")
            return WebSearch.hitJson(p, line).dropLast(1) + ",\"shared\":" + p.shared + "}"
        }
        return "{\"home\":" + (s.home?.let { row(it, true) } ?: "null") +
            ",\"favourites\":" + s.favourites.joinToString(",", "[", "]") { row(it) } +
            ",\"more\":" + s.moreFavourites + ",\"recent\":" + s.recent.joinToString(",", "[", "]") { row(it) } + "}"
    }

    private fun str(v: String): String = buildString {
        append('"')
        for (c in v) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\t' -> append("\\t")
            else -> if (c < ' ') append(' ') else append(c)
        }
        append('"')
    }
}
