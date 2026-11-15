package com.onpath.app.ui

import com.onpath.app.data.Geo
import com.onpath.app.routing.GeoPoint
import com.onpath.strings.*

/**
 * What the menu of a held place (POI, found pin, shared place) offers to add it (owner 2026-11-07 11:20 / 11:25 / 11:30,
 * 2026-11-15). Pure.
 *
 * - No point yet: "Hier starten" · "Hierhin" – during a tour only "Hierhin" (the tour already runs from here).
 * - One point (or no route ahead): today's wording, "Als Ziel setzen" (one entry).
 * - Two or more: always both, „Einfügen“ (the best place on the route ahead, the goal stays) and „Neues Ziel“ (the route
 *   goes on there, the old goal stays as the last stop while planning; during a tour it replaces the old goal – owner
 *   2026-11-14 [keepsOldGoal]; Undo brings the plan before back). The owner's words (2026-11-15), never reworded.
 *   The likelier one ([zone]) comes first and is emphasised; nothing hides under „⋯ Mehr“ any more (owner 2026-11-15
 *   after a hike: "man weiß sonst nicht, was das System tut"). Each row says where the point goes and about how far
 *   ([Line]: „zwischen Kirche und Hütte“ · „ca. +0,6 km“) – names, never "Stopp 2" (that confused the owner).
 */
object PlaceAddMenu {

    enum class Item { START_HERE, GO_HERE, SET_DESTINATION, ADD_STOP, NEW_DESTINATION }

    /**
     * "Als neues Ziel" (owner 2026-11-14: „Beim Planen anhängen, unterwegs ersetzen“): while planning the old goal stays as
     * the last stop (one plans forward, step by step); during a tour the new place replaces it (no detour back).
     */
    fun keepsOldGoal(touring: Boolean): Boolean = !touring

    /** How clear the case is: only stop · both, stop first · both, goal first · only goal. */
    enum class Zone { STOP, STOP_FIRST, GOAL_FIRST, GOAL }

    /**
     * [main] = the menu's rows in order – with 2+ points both add rows, the first one emphasised ([emphasised]);
     * [lines] = what each add row says (where the point goes, about how far), filled in by the app ([AppViewModel.placeMenu]).
     */
    data class Menu(val main: List<Item>, val lines: Map<Item, Line> = emptyMap()) {
        /** The likelier row (shown strong): the first of the two add rows; null with one point or none. */
        val emphasised: Item? get() = main.firstOrNull()?.takeIf { main.size == 2 && it in ADD_ITEMS }
    }

    /** What an add row says: [where] under its label („zwischen Kirche und Hütte“), [km] beside it („ca. +0,6 km“), or null. */
    data class Line(val where: String, val km: String?)

    private val ADD_ITEMS = setOf(Item.ADD_STOP, Item.NEW_DESTINATION)

    // Thresholds on the detour d = (route ahead with the place as its best stop) − (route ahead), straight lines. They are
    // conservative: a case near a border is DOUBTFUL (both entries) rather than one entry that is wrong.

    /** A clear stop: d at most this share of the route ahead … */
    const val CLEAR_STOP_SHARE = 0.2

    /** … or at most this many metres (a short rest: a place a few steps aside is still a stop). */
    const val CLEAR_STOP_M = 100.0

    /** Doubtful, but "Als neues Ziel" first: d more than this share of the route ahead. */
    const val GOAL_FIRST_SHARE = 0.5

    /** A clear goal: d more than this share of the route ahead (the detour more than doubles the rest) … */
    const val CLEAR_GOAL_SHARE = 1.0

    /** … and more than this many metres (a few hundred metres to go never make a place nearby "only a goal"). */
    const val CLEAR_GOAL_M = 300.0

    /** "Past the goal": farther from the last leg's start than the goal, within this angle of the leg → only goal … */
    const val PAST_GOAL_DEG = 30.0

    /** … within this wider angle → both, goal first. */
    const val PAST_GOAL_DOUBT_DEG = 60.0

    fun menu(points: Int, touring: Boolean, zone: Zone?): Menu = when {
        points <= 0 -> Menu(if (touring) listOf(Item.GO_HERE) else listOf(Item.START_HERE, Item.GO_HERE))
        points == 1 || zone == null -> Menu(listOf(Item.SET_DESTINATION))
        zone == Zone.STOP || zone == Zone.STOP_FIRST -> Menu(listOf(Item.ADD_STOP, Item.NEW_DESTINATION))
        else -> Menu(listOf(Item.NEW_DESTINATION, Item.ADD_STOP))
    }

    /**
     * [ahead] = the route still to go as points (during a tour: my position, then the points ahead; else the whole plan),
     * [p] = the held place, [loop] = a round trip (its goal is its start: nothing lies "past" it, and the loop is never
     * dropped by one tap – at most "goal first"). Null when there is no route ahead (fewer than 2 points).
     */
    fun zone(ahead: List<GeoPoint>, p: GeoPoint, loop: Boolean = false): Zone? {
        if (ahead.size < 2) return null
        var length = 0.0
        var detour = Double.MAX_VALUE
        for (i in 0 until ahead.size - 1) {
            val a = ahead[i]; val b = ahead[i + 1]
            val leg = Geo.distance(a, b)
            length += leg
            detour = minOf(detour, Geo.distance(a, p) + Geo.distance(p, b) - leg)
        }
        // "past the goal" is measured on the last leg, so a route that goes out and comes back (the place on the way out)
        // is not taken for one that ends short of it
        val legStart = ahead[ahead.size - 2]; val goal = ahead.last()
        val past = !loop && Geo.distance(goal, legStart) > 0.0 && Geo.distance(legStart, p) > Geo.distance(legStart, goal)
        val angle = if (past) kotlin.math.abs(Geo.angleDiff(Geo.bearing(legStart, goal), Geo.bearing(legStart, p))) else 180.0
        val zone = when {
            angle <= PAST_GOAL_DEG -> Zone.GOAL
            detour > maxOf(CLEAR_GOAL_SHARE * length, CLEAR_GOAL_M) -> Zone.GOAL
            angle <= PAST_GOAL_DOUBT_DEG || detour > maxOf(GOAL_FIRST_SHARE * length, CLEAR_STOP_M) -> Zone.GOAL_FIRST
            detour <= maxOf(CLEAR_STOP_SHARE * length, CLEAR_STOP_M) -> Zone.STOP
            else -> Zone.STOP_FIRST
        }
        return if (loop && zone == Zone.GOAL) Zone.GOAL_FIRST else zone
    }

    // ---- what a row says (owner 2026-11-15) ----

    /** A name longer than this is shortened at a word with „…“ (two long names still fit the menu in two lines). */
    const val NAME_MAX = 22

    /** Route length / straight-line length of the plan, kept between these: paths are longer than straight lines. */
    const val FACTOR_MIN = 1.0
    const val FACTOR_MAX = 3.0

    /** Under this the km are left out (a few steps aside need no number). */
    const val KM_MIN_M = 100.0

    /**
     * A plan point in a row's where-line: its [name] (shortened), else „Start“ / „Ziel“, an unnamed middle stop its circled
     * number as on the map (②) – never "Stopp 2".
     */
    fun pointName(i: Int, n: Int, name: String?): String = name?.trim()?.takeIf { it.isNotEmpty() }?.let { shorten(it) } ?: when {
        i == 0 -> Txt.s(Res.string.plan_point_start)
        i == n - 1 -> Txt.s(Res.string.plan_point_destination)
        else -> circled(i + 1)
    }

    /** ① … ⑳ as on the map's markers; beyond 20 the plain number. */
    fun circled(k: Int): String = if (k in 1..20) (0x2460 + k - 1).toChar().toString() else k.toString()

    /** [name] at most [max] characters: cut at the last space before it (or hard) with „…“. */
    fun shorten(name: String, max: Int = NAME_MAX): String {
        if (name.length <= max) return name
        val cut = name.substring(0, max - 1)
        val word = cut.lastIndexOf(' ').takeIf { it >= max / 2 } ?: cut.length
        return cut.substring(0, word).trimEnd() + "…"
    }

    /** How much longer the real way is than straight lines: [routeM] / the plan's straight length, 1 without a route. */
    fun detourFactor(routeM: Double?, points: List<GeoPoint>): Double {
        val straight = points.zipWithNext { a, b -> Geo.distance(a, b) }.sum()
        if (routeM == null || routeM <= 0.0 || straight <= 0.0) return 1.0
        return (routeM / straight).coerceIn(FACTOR_MIN, FACTOR_MAX)
    }

    /** „ca. +0,6 km“ / „ca. −0,4 km“ for [m] metres more (less); one decimal under 10 km, whole km above; null under [KM_MIN_M]. */
    fun km(m: Double?): String? {
        if (m == null || kotlin.math.abs(m) < KM_MIN_M) return null
        val k = kotlin.math.abs(m) / 1000
        val num = if (k < 10) String.format(com.onpath.app.data.Format.locale(), "%.1f", k) else Math.round(k).toString()
        return Txt.s(Res.string.search_add_about, (if (m < 0) "−" else "+") + num + " km")
    }

    /** Straight-line detour of [p] put between [a] and [b] (0 when it is on the way). */
    fun detourM(a: GeoPoint, b: GeoPoint, p: GeoPoint): Double =
        (Geo.distance(a, p) + Geo.distance(p, b) - Geo.distance(a, b)).coerceAtLeast(0.0)

    /**
     * „Einfügen“: [names] = the plan's points as [pointName] gives them, [at] = where the point goes in the plan (it comes
     * before the point now at [at]); [nextAhead] = during a tour it goes right after my position, before the next point
     * („vor Kirche“).
     */
    fun stopLine(names: List<String>, at: Int, nextAhead: Boolean, extraM: Double?): Line {
        val where = when {
            names.isEmpty() -> ""
            nextAhead || at <= 0 -> Txt.s(Res.string.plan_add_before, names[at.coerceIn(0, names.lastIndex)])
            at >= names.size -> Txt.s(Res.string.plan_add_after, names.last())
            else -> Txt.s(Res.string.plan_add_between, names[at - 1], names[at])
        }
        return Line(where, km(extraM))
    }

    /** „Neues Ziel“: while planning it comes after the goal („nach Hütte“), on a tour instead of it („statt Hütte“). */
    fun goalLine(goal: String, touring: Boolean, extraM: Double?): Line =
        Line(Txt.s(if (keepsOldGoal(touring)) Res.string.plan_add_after else Res.string.plan_add_instead, goal), km(extraM))
}
