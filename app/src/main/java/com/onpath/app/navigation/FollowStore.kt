package com.onpath.app.navigation

import com.onpath.app.data.FollowFile
import com.onpath.app.routing.GeoPoint
import java.io.File

/**
 * What is followed, on disk: `nav-session.json` ([NavSession]: the original line, the stops, a re-route) and
 * `follow.json` ([FollowFile]: the navigated line and its name) – the one object both writers go through (cleanup
 * piece 5, code audit 2026-11-12 point c).
 *
 * **Two writers stay, by design.** The app writes when the user chooses what to follow ([appFollows]) or ends it
 * ([appEnds]); the service writes what it changed itself – a re-route taken, a stop visited, "reached"
 * ([serviceSaves]). The service cannot be the only writer without a change a user could notice:
 * - most of the app's writes happen while **no tour runs** (a route is followed in Plan, then "Los geht's"): there is
 *   no service to send them to – it would have to be started for a file write, which Android only allows as a
 *   foreground service with its notification;
 * - the view model reads its own write back at once (`followLine`, the plan screen's "followed" state, the start that
 *   comes right after) and `clearFollow` counts on the file being gone **before** its FOLLOW_CHANGED arrives
 *   ([NavSession.mayOverwrite]: no file + a token = dropped) – through an Intent the write would land a main-loop
 *   turn later;
 * - a session with a long line does not fit an Intent (the reason the activity proposal travels as a file, too).
 *
 * So the rule is: **every write of the two files is one locked step in a fixed order**, and the service's writes are
 * conditional on the file still holding its session ([NavSessionStore.saveIfMine]). The lock is the session file's
 * (one per file in the process), so a plain [NavSessionStore] on the same file waits as well. A reader that needs both
 * files takes them in one look ([load]) – never the line of one session with the stops of another.
 * Today both writers run on the main thread; the lock is what makes this hold when the recording gets its own thread.
 */
class FollowStore(dir: File) {
    private val sessions = NavSessionStore(File(dir, NavSession.FILE))
    private val line = FollowFile(File(dir, FollowFile.NAME))
    private val proposal = NavSessionStore(File(dir, ActivityCheck.PROPOSAL_FILE))

    /** Both files as they were at one moment. */
    data class Followed(val follow: Pair<String, List<GeoPoint>>?, val session: NavSession?)

    fun load(): Followed = sessions.locked { Followed(line.load(), sessions.load()) }
    /** Only the line and its name (the app at start). */
    fun loadLine(): Pair<String, List<GeoPoint>>? = line.load()
    fun loadSession(): NavSession? = sessions.load()

    /**
     * The app follows [points] as [name] with [session] (null = the session could not be started: the plain line is
     * followed without one). The session first, then the line – one step for every other writer and for [load].
     */
    fun appFollows(session: NavSession?, name: String, points: List<GeoPoint>) = sessions.locked {
        sessions.save(session)
        line.save(name, points)
    }

    /** The app follows nothing any more: the line goes first, then the session. */
    fun appEnds() = sessions.locked {
        line.save("", null)
        sessions.save(null)
    }

    /**
     * The service's write of the session it changed, and – when the navigated line changed – that line as [lineAs]
     * (null = the line is as it was). Only while the file still holds that session ([NavSessionStore.saveIfMine]);
     * false = nothing written, the app follows something else. Look, session and line are one step: the app's new
     * route cannot land between the session and the line and be left with the old line under it.
     */
    fun serviceSaves(s: NavSession, lineAs: String? = null): Boolean = sessions.locked {
        if (!sessions.saveIfMine(s)) return@locked false
        if (lineAs != null) line.save(lineAs, s.active)
        true
    }

    /** Activity check: the app hands the route for another activity to the service (too big for an Intent) … */
    fun propose(s: NavSession) = proposal.save(s)
    /** … and the service takes it (the file is gone after). */
    fun takeProposal(): NavSession? = proposal.load().also { proposal.delete() }
}
