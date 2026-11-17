package com.onpath.app.ui

import com.onpath.app.data.Geo
import com.onpath.app.routing.GeoPoint

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
 *   after a hike: "man weiß sonst nicht, was das System tut"). One line each, only the label – where it goes shows on the
 *   map (owner 2026-11-15: „nur einzeilig … das sehe ich ja auf der Karte“); no stop numbers ("Stopp 2" confused the owner).
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

    /** [main] = the menu's rows in order – with 2+ points both add rows, the first one emphasised ([emphasised]). */
    data class Menu(val main: List<Item>) {
        /** The likelier row (shown strong): the first of the two add rows; null with one point or none. */
        val emphasised: Item? get() = main.firstOrNull()?.takeIf { main.size == 2 && it in ADD_ITEMS }
    }

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

    /**
     * A hold on the empty map with 2+ points (owner 2026-11-15, planning on his Pixel: „ich werde überhaupt nie gefragt“ –
     * „bei eindeutigen Sachen auch nicht schlecht“): only a doubtful case ([Zone.STOP_FIRST], [Zone.GOAL_FIRST]) asks with
     * this menu's two rows; a clear one, or no route ahead, is set at once where it fits best (WaypointInsertion).
     */
    fun asksOnHold(zone: Zone?): Boolean = zone == Zone.STOP_FIRST || zone == Zone.GOAL_FIRST

    /**
     * What a hold on the empty map does while planning ([planningHold]): [ON_LINE] = the smart insertion into that leg,
     * [STOP] = „Einfügen“ (stop-only, never behind the goal), [NEW_GOAL] = „Neues Ziel“ (appended), [ASK] = the menu.
     */
    enum class HoldAction { ON_LINE, STOP, NEW_GOAL, ASK }

    /** "On the line": this close to the planned line on screen (the hold's smart insertion uses the same ~24 dp). */
    const val LINE_SNAP_DP = 24.0

    /**
     * The ask band, the same both ways (owner 2026-11-16, field case Hilden Süd: appending 32 m, inserting 62 m – „ich
     * wurde nicht gefragt, ob es ein Zwischenpunkt sein soll“): one is taken at once only when its detour is at most
     * 1/[ASK_BAND] of the other's; between, both are similar – the app asks.
     */
    const val ASK_BAND = 2.0

    /**
     * „Straight on behind the goal“ (owner 2026-11-17, way 3 of the step-back page: „anhängen, außer es liegt erkennbar an
     * der Route“): a spot at most this many degrees off the direction the route arrives at the goal continues the way –
     * the new goal at once. The owner's field cases: St.-Konrad-Allee 19:51 and four holds 2026-11-16 23:42 (0–40°, he
     * wanted the new goal), Hilden Süd 23:27 (~75°, „hätte ein Zwischenpunkt sein können“) and the Färberweg junction
     * (~70°) ask.
     */
    const val AHEAD_DEG = 60.0

    /** The arrival direction is read over the route's last this many metres before the goal. */
    const val ARRIVAL_M = 40.0

    /** The costs of a new point while planning, metres – straight lines ([detours]) or on the paths ([pathDetours]). */
    data class Detours(
        /** The cheapest insertion detour over the legs: d(a,s) + d(s,b) − d(a,b) (as [zone]). */
        val insertM: Double,
        /** What appending costs: d(goal, s). */
        val appendM: Double,
        /** The cheapest insertion detour over the legs before the last one; null with one leg. */
        val earlyInsertM: Double? = null,
        /** Degrees between the direction the route arrives at the goal and goal → spot; null when not known. */
        val aheadDeg: Double? = null,
    )

    /**
     * [Detours] of a spot [s] for a plan of 2+ [points]; null with fewer. [line] = the drawn route (its last [ARRIVAL_M]
     * give the arrival direction); without it the last leg's straight line does.
     */
    fun detours(points: List<GeoPoint>, s: GeoPoint, line: List<GeoPoint>? = null): Detours? {
        if (points.size < 2) return null
        val legs = points.zipWithNext().map { (a, b) -> Geo.distance(a, s) + Geo.distance(s, b) - Geo.distance(a, b) }
        val goal = points.last()
        val from = arrivalFrom(line, goal) ?: points[points.size - 2]
        val ahead = if (Geo.distance(from, goal) < 1.0 || Geo.distance(goal, s) < 1.0) null
            else kotlin.math.abs(Geo.angleDiff(Geo.bearing(from, goal), Geo.bearing(goal, s)))
        return Detours(legs.min(), Geo.distance(goal, s), legs.dropLast(1).minOrNull(), ahead)
    }

    /**
     * „Always real paths“ (owner 2026-11-17, field case Hilden Feldstraße: straight lines said insert +68 m, append +63 m
     * – asked; on the paths inserting is ≈ +120 m, from point 12 there is no direct way, it goes past the goal; appending
     * ≈ 60 m): this many legs with the smallest straight-line detour are measured on the paths, the last leg always on top
     * ([pathCandidates]) – it caps the router's work.
     */
    const val PATH_CANDIDATES = 3

    /**
     * The legs (index of the leg's start point) [pathDetours] measures for a spot [s] in a plan of 2+ [points]: the last leg
     * first (it decides most holds), then the [PATH_CANDIDATES] legs with the smallest straight-line detour; empty with
     * fewer than 2 points.
     */
    fun pathCandidates(points: List<GeoPoint>, s: GeoPoint): List<Int> {
        if (points.size < 2) return emptyList()
        val last = points.size - 2
        val byDetour = (0..last).sortedBy { i ->
            Geo.distance(points[i], s) + Geo.distance(s, points[i + 1]) - Geo.distance(points[i], points[i + 1])
        }.take(PATH_CANDIDATES)
        return listOf(last) + byDetour.filter { it != last }
    }

    /**
     * [Detours] on the real paths (owner 2026-11-17 „Always real paths“): [length] (a, b) = the routed length a → b in
     * metres for the plan's activity; it throws when there is no route (the caller then keeps the straight lines,
     * [detours]). Appending = length(goal, s); inserting into a candidate leg a → b ([pathCandidates]) = length(a, s) +
     * length(s, b) − length(a, b), never below 0; [Detours.earlyInsertM] = the smallest among the candidates before the last
     * leg (null when none is measured). [Detours.aheadDeg] stays the drawn route's ([straight]): straight on behind the goal
     * the paths alone would ask (there and back is about twice the way on).
     */
    fun pathDetours(points: List<GeoPoint>, s: GeoPoint, straight: Detours, length: (GeoPoint, GeoPoint) -> Double): Detours {
        require(points.size >= 2) { "a plan needs two points, got ${points.size}" }
        val goal = points.last()
        val append = length(goal, s)
        val last = points.size - 2
        var insert = Double.MAX_VALUE
        var early: Double? = null
        for (i in pathCandidates(points, s)) {
            val a = points[i]; val b = points[i + 1]
            val d = maxOf(0.0, length(a, s) + length(s, b) - length(a, b))
            insert = minOf(insert, d)
            if (i < last) early = minOf(early ?: d, d)
        }
        return Detours(insert, append, early, straight.aheadDeg)
    }

    /** The point [ARRIVAL_M] before the end of [line] (walking back from its last point); null without a usable line. */
    private fun arrivalFrom(line: List<GeoPoint>?, goal: GeoPoint): GeoPoint? {
        if (line == null || line.size < 2 || Geo.distance(line.last(), goal) > 2 * ARRIVAL_M) return null
        var walked = 0.0
        for (i in line.size - 1 downTo 1) {
            walked += Geo.distance(line[i], line[i - 1])
            if (walked >= ARRIVAL_M) return line[i - 1]
        }
        return line.first()
    }

    /**
     * A hold on the empty map with 2+ points while PLANNING – no tour, no round trip (those keep [asksOnHold] on the zone).
     * The owner's rule (2026-11-17, way 3 after comparing Komoot, Strava, OsmAnd …: „anhängen, außer es liegt erkennbar
     * an der Route“), in order: on the line ([lineDp] ≤ [snapDp]) → [HoldAction.ON_LINE]; beside an earlier part of the
     * route, inserting there at most 1/[ASK_BAND] of appending → [HoldAction.STOP]; straight on behind the goal (≤
     * [AHEAD_DEG] off the arrival) → [HoldAction.NEW_GOAL]; else the band the same both ways (2026-11-16): appending at most
     * 1/[ASK_BAND] of inserting → [HoldAction.NEW_GOAL], inserting at most 1/[ASK_BAND] of appending → [HoldAction.STOP],
     * else [HoldAction.ASK]. [lineDp] null = no line yet. Since 2026-11-17 („Always real paths“) the app gives [d] on the
     * paths ([pathDetours]) when its router measured them in time, else the straight lines ([detours]) – the rule is the same.
     */
    fun planningHold(lineDp: Double?, d: Detours, snapDp: Double = LINE_SNAP_DP): HoldAction = when {
        lineDp != null && lineDp <= snapDp -> HoldAction.ON_LINE
        d.earlyInsertM != null && d.earlyInsertM * ASK_BAND <= d.appendM -> HoldAction.STOP
        d.aheadDeg != null && d.aheadDeg <= AHEAD_DEG -> HoldAction.NEW_GOAL
        d.appendM * ASK_BAND <= d.insertM -> HoldAction.NEW_GOAL
        d.insertM * ASK_BAND <= d.appendM -> HoldAction.STOP
        else -> HoldAction.ASK
    }

    /**
     * The add rows while planning (a held place, or a hold that asks): both, the cheaper first and tinted – the same
     * decision a plain hold takes ([planningHold]), one rule everywhere. A held place orders its rows on straight lines (its
     * menu opens at once); a hold that asks orders them on the numbers it decided with (the paths when measured).
     */
    fun planningMenu(d: Detours): Menu =
        Menu(if (d.insertM < d.appendM) listOf(Item.ADD_STOP, Item.NEW_DESTINATION) else listOf(Item.NEW_DESTINATION, Item.ADD_STOP))

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
}
