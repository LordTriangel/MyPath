package com.onpath.app.ui.sync

import com.onpath.app.debug.Problems
import com.onpath.app.debug.launchSafe
import com.onpath.app.domain.LinkOpen
import com.onpath.app.domain.RouteRules
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlanLinks
import com.onpath.app.routing.SharedPlan
import com.onpath.app.ui.NoticePlace
import com.onpath.strings.*
import kotlinx.coroutines.CoroutineScope

/** What [PlanLinkFlow] reads and asks of the view model: its screens, the plan on the map, pairing and the share sheet. */
interface PlanLinkHost {
    /** Back to the map: My routes, Settings, the sheets, the search and the map's overlays close. */
    fun backToMap()
    /** One red line ([place] = where the user looks now). */
    fun sayError(text: String, place: NoticePlace)
    /** A pairing link of the web planner: Settings → Browser sync asks first. */
    fun receivePairing(secret: ByteArray, via: String)
    /** Settings → Browser sync opens. */
    fun openSyncSettings()
    /** Returns once the start mode (Plan / Navigate, an unfinished tour) is decided – at once except on a cold start. */
    suspend fun startModeKnown()
    /** Navigate is shown or a tour records. */
    fun navigating(): Boolean
    /** The plan's points on the map now. */
    fun planPoints(): List<GeoPoint>
    /** The points of the saved route the plan belongs to; null = none. */
    fun savedPoints(): List<GeoPoint>?
    /** Puts [plan] on the map as a new, unsaved plan (a track shown closes first; the plan before goes under Zuletzt). */
    fun open(plan: SharedPlan, via: String)
    /** [link] into the share sheet, [title] as its subject. */
    fun share(link: String, title: String?)
    /** The points the last plan link brought, kept across app starts (a pref). */
    var storedLinkPoints: List<GeoPoint>?
}

/**
 * Plan links in and out (A-005: routing/PlanLinks, docs/core-features/C15-plan-links.md): a link opened in OnPath ([receive],
 * [pasted]) – broken, a pairing link, during a tour, or a plan to open –, what the last link brought ([title],
 * [opened], [snapped]: its name for the Save dialog and its points, so the next link needs no question while the plan
 * is unchanged), and a plan sent as a link ([share]). The plan itself stays in the view model ([PlanLinkHost.open]).
 * Android-free, testable on the JVM (PlanLinkFlowTest).
 */
class PlanLinkFlow(private val host: PlanLinkHost, private val scope: CoroutineScope) {

    /** Name a plan link brought, for the Save dialog while the plan still has the link's points. */
    private var linkTitle: Pair<String, List<GeoPoint>>? = null
    /** The points the last plan link brought (after snapping): while the plan is unchanged, the next link needs no question. */
    private var linkPoints: List<GeoPoint>? = host.storedLinkPoints
        set(v) { field = v; host.storedLinkPoints = v }

    /** The name the last link brought, while the plan still has exactly its points [pts]; null = none. */
    fun title(pts: List<GeoPoint>): String? = linkTitle?.takeIf { it.second == pts }?.first

    /** A link's plan was put on the map with [points] (after [PlanLinks.load]) and its [title]. */
    fun opened(title: String?, points: List<GeoPoint>) {
        linkTitle = title?.let { it to points }
        linkPoints = points
    }

    /** The router moved the plan's points [routed] onto their ways ([snapped]): what the link brought follows. */
    fun snapped(routed: List<GeoPoint>, snapped: List<GeoPoint>) {
        linkTitle?.let { (title, at) -> if (at == routed) linkTitle = title to snapped }
        linkPoints?.let { at -> if (at.map { it.lat to it.lon } == routed.map { it.lat to it.lon }) linkPoints = snapped }
    }

    /**
     * A plan link opened in OnPath (camera / QR code, a tapped link, shared text, pasted into the search box; [via] for
     * the log): back to the map in Plan and loaded like a saved route – the plan before goes under Zuletzt, no question.
     * During a tour it is not loaded (the followed route stays) and a line says so. A broken link changes nothing and
     * says so in one red line.
     */
    fun receive(found: PlanLinks.Found, via: String) {
        host.backToMap()
        val plan = when (found) {
            is PlanLinks.Found.Broken -> {
                Problems.warn("plan", "Plan link ($via) could not be read: cut off, damaged or a newer format")
                host.sayError(Txt.s(Res.string.msg_plan_link_unreadable), NoticePlace.MAP)
                return
            }
            is PlanLinks.Found.Plan -> found.plan
            // a pairing link of the web planner ("Pair phone" in its header): Settings → Browser sync asks first (A-007)
            is PlanLinks.Found.Pairing -> { host.receivePairing(found.secret, via); return }
            is PlanLinks.Found.BrokenPairing -> {
                Problems.warn("sync", "Pairing link ($via) could not be read: cut off or a newer version")
                host.openSyncSettings()
                host.sayError(Txt.s(Res.string.sync_link_broken), NoticePlace.SETTINGS)
                return
            }
        }
        scope.launchSafe("app", "receivePlanLink") {
            // Cold start: the start mode (Plan / Navigate, an unfinished tour) is decided after a database check
            host.startModeKnown()
            when (RouteRules.linkOpen(host.navigating(), host.planPoints(), host.savedPoints(), plan.points, linkPoints)) {
                LinkOpen.AFTER_TOUR -> {
                    Problems.info("plan", "Plan link ($via, ${plan.points.size} points) not opened: a tour is running")
                    host.sayError(Txt.s(Res.string.msg_plan_link_after_tour), NoticePlace.MAP)
                }
                // the current plan goes under "Zuletzt" (owner 2026-11-06) – no question any more
                LinkOpen.ASK_FIRST, LinkOpen.NOW -> host.open(plan, via)
            }
        }
    }

    /** Text pasted into the search box: a plan link opens the plan (true, after [clearSearch]); anything else is a search (false). */
    fun pasted(text: String, clearSearch: () -> Unit): Boolean {
        val found = PlanLinks.fromText(text) ?: return false
        clearSearch()
        receive(found, "pasted")
        return true
    }

    /** [plan] ([count] points) as a plan.onpath.earth link into the share sheet. */
    fun share(plan: SharedPlan, count: Int, title: String?) {
        val link = PlanLinks.link(plan)
        Problems.trace("plan", "Plan shared as a link: $count points, ${link.length} characters")
        host.share(link, title)
    }
}
