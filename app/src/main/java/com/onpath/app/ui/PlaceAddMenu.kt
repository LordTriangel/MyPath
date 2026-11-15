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
