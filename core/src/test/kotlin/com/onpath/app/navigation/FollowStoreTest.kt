package com.onpath.app.navigation

import com.onpath.app.data.FollowFile
import com.onpath.app.navigation.Trace.line
import com.onpath.app.navigation.Trace.pt
import com.onpath.app.routing.Activity
import com.onpath.app.testkit.ProblemsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The writer rule of `nav-session.json` + `follow.json` (cleanup piece 5, code audit 2026-11-12 point c): the app and
 * the service both write, each write is one locked step in a fixed order ([FollowStore]), the service's write is
 * conditional on the file still holding its session. What must never happen: the app's new route in the session file
 * and the old route's line in `follow.json` under it (the service then follows the old line as a plain track with
 * the new session's token), or a reader that sees the line of one session beside the other.
 */
class FollowStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val problems = ProblemsRule()

    private val store get() = FollowStore(tmp.root)
    private val sessionFile get() = File(tmp.root, NavSession.FILE)
    private val followFile get() = File(tmp.root, FollowFile.NAME)

    /** A plan session [lenM] long; its name carries the token, so line, name and session can be checked to belong together. */
    private fun session(token: Long, lenM: Double = 1000.0 + 10.0 * token): NavSession =
        NavSession.start(NavKind.PLAN, "Plan $token", Activity.HIKE, 3, line(0.0 to 0.0, lenM to 0.0), listOf(pt(0.0, 0.0), pt(lenM, 0.0)))
            .copy(token = token, reached = true)

    /** The service's re-route of [s]: a new current line, same token. */
    private fun rerouted(s: NavSession) = s.copy(current = line(0.0 to 50.0, 400.0 to 50.0), version = s.version + 1)

    private fun assertBelongTogether(f: FollowStore.Followed) {
        val session = f.session; val follow = f.follow
        if (session == null) { assertNull("no session: no line of an ended guidance", follow); return }
        assertEquals("the line's name is its session's", session.name, follow?.first)
        assertEquals("the line is the session's navigated line", session.active.size, follow?.second?.size)
    }

    @Test fun theAppsWriteIsReadBackAtOnceAndEndsWithBothFilesGone() {
        val s = session(1)
        store.appFollows(s, s.name, s.active)
        assertEquals(s.name to s.active.size, store.loadLine()!!.let { it.first to it.second.size })
        assertEquals(1L, store.loadSession()!!.token)
        assertBelongTogether(store.load())
        store.appEnds()
        assertEquals(FollowStore.Followed(null, null), store.load())
        assertEquals("nothing left behind", emptyList<String>(), tmp.root.list()!!.toList())
    }

    @Test fun aSessionThatCouldNotBeStartedLeavesThePlainLineAndNoOldSession() {
        store.appFollows(session(1), "Plan 1", session(1).active)
        store.appFollows(null, "Track", line(0.0 to 0.0, 500.0 to 0.0))
        assertNull("the old session is not left under the new line", store.loadSession())
        assertEquals("Track", store.loadLine()!!.first)
    }

    @Test fun theServicesReRouteWritesSessionAndLineTogether() {
        val s = session(1)
        store.appFollows(s, s.name, s.active)
        val r = rerouted(s)
        assertTrue(store.serviceSaves(r, lineAs = s.name))
        assertTrue(store.loadSession()!!.rerouted)
        assertEquals(r.active.size, store.loadLine()!!.second.size)
        assertBelongTogether(store.load())
        // a session change without a new line (a stop visited, "reached"): the line file is not touched
        val before = followFile.lastModified(); val text = followFile.readText()
        assertTrue(store.serviceSaves(r.copy(visited = setOf(0))))
        assertEquals(text, followFile.readText()); assertEquals(before, followFile.lastModified())
        assertEquals(setOf(0), store.loadSession()!!.visited)
    }

    @Test fun aReRouteOfTheOldRouteWritesNeitherFileOnceTheAppFollowsANewOne() {
        val old = session(1)
        store.appFollows(old, old.name, old.active)
        val edited = session(2)
        store.appFollows(edited, edited.name, edited.active)       // the plan edited in the app; FOLLOW_CHANGED on the way
        assertFalse("the old line's way back is dropped", store.serviceSaves(rerouted(old), lineAs = old.name))
        assertEquals(2L, store.loadSession()!!.token)
        assertFalse(store.loadSession()!!.rerouted)
        assertEquals("the new route's line stays in follow.json", edited.active.size, store.loadLine()!!.second.size)
        assertBelongTogether(store.load())
        // guidance ended in the app: nothing comes back either
        store.appEnds()
        assertFalse(store.serviceSaves(rerouted(edited), lineAs = edited.name))
        assertFalse(sessionFile.exists()); assertFalse(followFile.exists())
    }

    /**
     * The interleaving, forced: the service wants to write its re-route while another writer of the same file is in
     * the middle of its step (here: a plain [NavSessionStore] on the file holds the file's lock and puts the app's new
     * session there). The service's look-and-write must wait for it – and then find the new session and write nothing;
     * a reader of both files waits as well and gets the new pair.
     * Without the one lock around look + session + line the service's write ran in between: it saw the old token,
     * wrote its session, and its old line landed in follow.json after the app's.
     */
    @Test fun theServicesWriteWaitsForAWriterInTheMiddleOfItsStepAndThenSeesTheNewSession() {
        val old = session(1)
        store.appFollows(old, old.name, old.active)
        val edited = session(2)
        val appStore = NavSessionStore(sessionFile)
        val inside = CountDownLatch(2); val serviceDone = CountDownLatch(1); val readerDone = CountDownLatch(1)
        val wrote = AtomicBoolean(true)
        val pool = Executors.newFixedThreadPool(2)
        var seen: FollowStore.Followed? = null
        try {
            appStore.locked {
                pool.execute { inside.countDown(); seen = FollowStore(tmp.root).load(); readerDone.countDown() }
                pool.execute { inside.countDown(); wrote.set(FollowStore(tmp.root).serviceSaves(rerouted(old), lineAs = old.name)); serviceDone.countDown() }
                assertTrue(inside.await(5, TimeUnit.SECONDS))
                assertFalse("the service's write waits while another writer is in its step", serviceDone.await(300, TimeUnit.MILLISECONDS))
                assertFalse("a look at both files waits too", readerDone.await(100, TimeUnit.MILLISECONDS))
                assertEquals("nothing of the service's on disk yet", old.active.size, FollowFile(followFile).load()!!.second.size)
                appStore.save(edited)
                FollowFile(followFile).save(edited.name, edited.active)
            }
            assertTrue(serviceDone.await(5, TimeUnit.SECONDS)); assertTrue(readerDone.await(5, TimeUnit.SECONDS))
        } finally { pool.shutdownNow() }
        assertFalse("the re-route of the replaced route is not written", wrote.get())
        assertEquals("the reader saw the new session with its own line, not half of each", 2L, seen!!.session!!.token)
        assertBelongTogether(seen!!)
        assertEquals(2L, store.loadSession()!!.token)
        assertBelongTogether(store.load())
    }

    /**
     * Two threads, each with its own store (as the view model and the service have): the app follows route after
     * route and ends the guidance now and then, the service keeps writing re-routes of whatever it last read; a third
     * thread reads. Every look at the two files shows a line and a session that belong together.
     */
    @Test fun appAndServiceFromTwoThreadsNeverLeaveALineUnderAnotherSession() {
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val stop = AtomicBoolean(false)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        fun thread(body: () -> Unit) = pool.execute { start.await(); try { body() } catch (e: Throwable) { errors += e } }
        thread { // the app
            val app = FollowStore(tmp.root)
            try {
                for (i in 1..300) {
                    val s = session(i.toLong())
                    app.appFollows(s, s.name, s.active)
                    if (i % 7 == 0) app.appEnds()
                }
            } finally { stop.set(true) }
        }
        thread { // the service
            val service = FollowStore(tmp.root)
            while (!stop.get()) {
                Thread.sleep(1) // a monitor is not fair: without a breath the two loops here keep the app's thread out
                val mine = service.load().session ?: continue
                service.serviceSaves(rerouted(mine), lineAs = mine.name)
            }
        }
        thread { // a reader (the service's loadFollow after FOLLOW_CHANGED, a restart)
            val reader = FollowStore(tmp.root)
            while (!stop.get()) { assertBelongTogether(reader.load()); Thread.sleep(1) }
        }
        start.countDown()
        pool.shutdown()
        assertTrue("threads finished", pool.awaitTermination(120, TimeUnit.SECONDS))
        assertEquals(emptyList<Throwable>(), errors)
        assertBelongTogether(store.load())
        assertTrue("no problem reported", problems.none())
    }

    @Test fun theActivityProposalIsHandedOverOnce() {
        val p = session(5)
        store.propose(p)
        assertTrue(File(tmp.root, ActivityCheck.PROPOSAL_FILE).isFile)
        assertEquals(5L, store.takeProposal()!!.token)
        assertFalse("the hand-over file is gone", File(tmp.root, ActivityCheck.PROPOSAL_FILE).exists())
        assertNull(store.takeProposal())
        assertNull("the followed session is another file", store.loadSession())
    }
}
