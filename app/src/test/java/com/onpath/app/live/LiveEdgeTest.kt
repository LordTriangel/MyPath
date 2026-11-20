package com.onpath.app.live

import com.onpath.app.data.TourKind
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.navigation.NavState
import com.onpath.app.recording.RecState
import com.onpath.app.routing.GeoPoint
import com.onpath.app.testkit.JdbcTours
import com.onpath.app.testkit.sourceFile
import com.onpath.app.time.TripTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * The live sharing's small Android-side parts, each on the JVM (docs/design/live-tracking.md §14.3): the track reader
 * over the tour tables, the state file, the HTTP box. The risks: a wrong or incomplete way on the family's page, a
 * share that is lost or doubled by a kill, a request problem that throws into the lane – and, pinned on the sources,
 * that the recording service's part of it stays a handful of hand-overs.
 */
class LiveEdgeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val closers = ArrayList<AutoCloseable>()
    @After fun close() = closers.forEach { it.close() }

    private val t0 = 1_800_000_000_000L
    private fun db(): Pair<JdbcTours, JdbcLivePoints> {
        val path = tmp.root.resolve("tours.db").path
        val rec = JdbcTours(path).also { closers += it }
        return rec to JdbcLivePoints(path, createdMs = t0 - 3_600_000, activity = "BIKE_TOURING").also { closers += it }
    }

    // ---- DbLiveTrack ----

    /** The cursor: only points above it, by number, across segments, at most `max` – what makes "no point twice, none missing" true. */
    @Test fun pointsAfterTheCursorComeInOrderAcrossSegments() {
        val (rec, points) = db()
        val tour = rec.insertTour(TourKind.RECORDED, "t", "HIKE", 2, state = "active")
        val other = rec.insertTour(TourKind.RECORDED, "other", "HIKE", 2, state = "done")
        // written out of order on purpose (the table has no order of its own), another tour's points in between
        for (i in listOf(4, 0, 3, 1, 2, 5)) rec.appendPoint(tour, i, if (i < 3) 0 else 2, TrackPoint(46.0 + i, 11.0, if (i == 1) null else 1000.4 + i, t0 + i * 1000L), 4f)
        rec.appendPoint(other, 0, 0, TrackPoint(1.0, 1.0, 1.0, 1L))
        val track = DbLiveTrack(points, { null }, { null }, { t0 })

        assertEquals(listOf(0, 1, 2, 3, 4, 5), track.pointsAfter(tour, -1, 100).map { it.seq })
        assertEquals(listOf(3, 4, 5), track.pointsAfter(tour, 2, 100).map { it.seq })
        assertEquals(listOf(3, 4), track.pointsAfter(tour, 2, 2).map { it.seq })
        assertEquals(emptyList<Int>(), track.pointsAfter(tour, 5, 100).map { it.seq })
        val all = track.pointsAfter(tour, -1, 100).map { it.point }
        assertEquals(listOf(0, 0, 0, 2, 2, 2), all.map { it.seg })
        assertEquals(listOf(1000, null, 1002, 1003, 1004, 1005), all.map { it.eleM })
        assertEquals(t0 + 5000, all.last().timeMs)
    }

    /** A point without a time takes the time before it (a chunk's times are deltas); a segment number never goes back. */
    @Test fun aPointWithoutATimeOrWithAnOlderSegmentIsStillSent() {
        val (rec, points) = db()
        val tour = rec.insertTour(TourKind.RECORDED, "t", "HIKE", 2, state = "active")
        rec.appendPoint(tour, 0, 1, TrackPoint(46.0, 11.0, 1.0, t0))
        rec.appendPoint(tour, 1, 0, TrackPoint(46.1, 11.0, 1.0, null))
        val got = DbLiveTrack(points, { null }, { null }, { t0 }).pointsAfter(tour, -1, 10).map { it.point }
        assertEquals(listOf(t0, t0), got.map { it.timeMs })
        assertEquals(listOf(1, 1), got.map { it.seg })
    }

    private fun line() = listOf(GeoPoint(46.0, 11.0), GeoPoint(46.0, 11.01), GeoPoint(46.0, 11.02))
    private fun nav(version: Int = 1, line: List<GeoPoint> = line(), along: Double? = 100.0) =
        NavState(line, version, null, null, null, 0.0, shownAlongM = along)
    private fun rec(tourId: Long, paused: Boolean = false, nav: NavState? = null, alert: Boolean = false, pausedSince: Long? = null,
                    gpsLost: Boolean = false, time: TripTime? = null) =
        RecState(tourId, paused, 12_345.6, 640.4, 10.0, 11_400, gpsLost, if (nav != null) 150.4 else null, alert, null, emptyList(), nav, time,
            arrived = false, pausedSinceMs = pausedSince)

    /** The head is what the page's status line and numbers are made of: each field from where the app itself shows it. */
    @Test fun theHeadSaysWhatTheAppShows() {
        val (r, points) = db()
        val tour = r.insertTour(TourKind.RECORDED, "t", "BIKE_TOURING", 2, state = "active")
        r.appendPoint(tour, 0, 0, TrackPoint(46.5, 11.1, 1522.6, t0 - 10_000), 4.4f)
        var state: RecState? = rec(tour, time = TripTime(movingS = 9_000.0, breakS = 2_400.0, toGoS = 3_600.0, arrivalMs = t0 + 4_000_000))
        var battery: Int? = 43
        val track = DbLiveTrack(points, { state }, { battery }, { t0 })

        var h = track.head(tour)!!
        assertEquals(LiveState.RECORDING, h.state)
        assertEquals(LiveFix(46.5, 11.1, 1523, t0 - 10_000, 4), h.fix)
        assertEquals(12_346, h.distanceM); assertEquals(640, h.ascentM); assertEquals(9_000, h.movingS)
        assertEquals(t0 - 3_600_000, h.startedMs); assertEquals("BIKE_TOURING", h.activity); assertEquals(43, h.batteryPct)
        assertNull("moving", h.stillS); assertNull("nothing followed", h.toGoM); assertNull(h.arrivalMs); assertNull(h.offRouteM)
        assertNull("no route without a followed line", track.route(tour))

        // following a plan: to go from the navigator's progress, arrival from the trip's estimate
        state = rec(tour, nav = nav(), time = TripTime(9_000.0, 0.0, 3_600.0, t0 + 4_000_000))
        h = track.head(tour)!!
        assertTrue("to go = the line's length minus the progress", h.toGoM!! in 1_300..1_500)
        assertEquals(t0 + 4_000_000, h.arrivalMs)
        assertNull("off the line by some metres is no alarm", h.offRouteM)
        // the app's own off-route alarm
        state = rec(tour, nav = nav(), alert = true)
        assertEquals(150, track.head(tour)!!.offRouteM)
        // paused: since when
        state = rec(tour, paused = true, pausedSince = t0 - 720_000)
        h = track.head(tour)!!
        assertEquals(LiveState.PAUSED, h.state); assertEquals(720, h.stillS)
        // another tour's state, no state: not known
        state = rec(tour + 1); assertNull(track.head(tour))
        state = null; assertNull(track.head(tour)); assertNull(track.route(tour))
        battery = null; state = rec(tour); assertNull(track.head(tour)!!.batteryPct)
    }

    /** Standing still (no kept point for two minutes, GPS there) slows the uploads – but lost GPS is not "standing". */
    @Test fun standingStillIsToldFromTheLastKeptPoint() {
        val (r, points) = db()
        val tour = r.insertTour(TourKind.RECORDED, "t", "HIKE", 2, state = "active")
        r.appendPoint(tour, 0, 0, TrackPoint(46.5, 11.1, 1.0, t0 - 300_000))
        var state = rec(tour)
        val track = DbLiveTrack(points, { state }, { 50 }, { t0 })
        assertEquals(300, track.head(tour)!!.stillS)
        state = rec(tour, gpsLost = true)
        assertNull(track.head(tour)!!.stillS)
        r.appendPoint(tour, 1, 0, TrackPoint(46.5, 11.1, 1.0, t0 - 30_000))
        state = rec(tour)
        assertNull("moving again", track.head(tour)!!.stillS)
    }

    /** The route goes up again exactly when it changed: a re-route, another plan – and its number is never the "none" mark. */
    @Test fun theRouteVersionChangesWithTheLine() {
        val (r, points) = db()
        val tour = r.insertTour(TourKind.RECORDED, "t", "HIKE", 2, state = "active")
        var state = rec(tour, nav = nav(1))
        val track = DbLiveTrack(points, { state }, { 50 }, { t0 })
        val v1 = track.route(tour)!!.version
        assertEquals("the same line, the same number", v1, track.route(tour)!!.version)
        assertEquals(3, track.route(tour)!!.line.size)
        state = rec(tour, nav = nav(2))
        assertNotEquals("a re-route", v1, track.route(tour)!!.version)
        state = rec(tour, nav = nav(1, line().map { GeoPoint(it.lat + 0.1, it.lon) }))
        assertNotEquals("another plan whose navigator counts from 1 again", v1, track.route(tour)!!.version)
        state = rec(tour, nav = nav(1, line().take(1)))
        assertNull("one point is no line", track.route(tour))
        for (v in -5..5) assertNotEquals(LiveShare.NO_ROUTE, DbLiveTrack.versionOf(nav(v)))
    }

    // ---- LiveFiles ----

    /** The share survives a kill as one text; a missing or unreadable file is "no share", never a throw. */
    @Test fun theStateFileKeepsTheShareAndNeverThrows() {
        val dir = File(tmp.root, "live")
        val files = LiveFiles(dir)
        assertNull("nothing there yet", files.read())
        files.write("onpath-live 1 abc")
        assertEquals("onpath-live 1 abc", LiveFiles(dir).read())
        files.write("onpath-live 1 def")
        assertEquals("onpath-live 1 def", files.read())
        assertEquals("no temp file stays behind", listOf(LiveFiles.FILE), dir.list()!!.toList())
        files.write(null)
        assertNull(files.read()); assertFalse(File(dir, LiveFiles.FILE).exists())
        files.write(null) // removing nothing is fine

        // the folder cannot be made (a file is in its place), the file is a folder: reported, not thrown
        val blocked = File(tmp.root, "blocked").apply { writeText("x") }
        LiveFiles(blocked).write("t"); assertNull(LiveFiles(blocked).read())
        val odd = File(tmp.root, "odd").apply { File(this, LiveFiles.FILE).mkdirs() }
        LiveFiles(odd).write("t"); assertNull(LiveFiles(odd).read())
    }

    // ---- HttpLiveBox ----

    private class Request(val line: String, val headers: Map<String, String>, val body: String)

    /** A tiny HTTP server: answers every request with the next of [answers] (status line + headers), or closes without one. */
    private inner class Server(private val answers: List<String?>) : AutoCloseable {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<Request>()
        val base get() = "http://127.0.0.1:${socket.localPort}/"
        init {
            closers += this
            thread(isDaemon = true) {
                var n = 0
                while (!socket.isClosed) {
                    val s = try { socket.accept() } catch (_: Exception) { break } // expected: closed at the end of the test
                    s.use { c ->
                        val input = c.getInputStream()
                        val head = StringBuilder()
                        while (!head.endsWith("\r\n\r\n")) { val b = input.read(); if (b < 0) break; head.append(b.toChar()) }
                        val lines = head.toString().trim().split("\r\n")
                        val headers = lines.drop(1).associate { it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim() }
                        val len = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = ByteArray(len).also { var o = 0; while (o < len) { val r = input.read(it, o, len - o); if (r < 0) break; o += r } }
                        requests += Request(lines.first(), headers, body.decodeToString())
                        val answer = answers.getOrNull(n++)
                        if (answer != null) c.getOutputStream().apply { write("HTTP/1.1 $answer\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray()); flush() }
                    }
                }
            }
        }
        override fun close() = socket.close()
    }

    /** The three requests are exactly what the server (web/host/src/live.js) reads: path, bearer write token, JSON body. */
    @Test fun theRequestsAreWhatTheServerReads() {
        val server = Server(listOf("201 Created", "200 OK", "200 OK", "200 OK"))
        val box = HttpLiveBoxes(server.base).open("BoxId_-123", "WriteTok_-9")
        assertEquals(LiveReply(201), box.claim("ab12".repeat(16), "cd34".repeat(16)))
        assertEquals(LiveReply(200), box.push(listOf(LivePush("r", byteArrayOf(1, 2, 3)), LivePush("t0007", byteArrayOf(-1, -2)), LivePush("h", byteArrayOf(0))), ended = false))
        assertEquals(LiveReply(200), box.push(listOf(LivePush("h", byteArrayOf(9))), ended = true))
        assertEquals(LiveReply(200), box.delete())

        val (claim, push, last, delete) = server.requests
        assertEquals("POST /live/v1/BoxId_-123 HTTP/1.1", claim.line)
        assertEquals("""{"read":"${"ab12".repeat(16)}"}""", claim.body)
        assertEquals("POST /live/v1/BoxId_-123/push HTTP/1.1", push.line)
        assertEquals("""{"items":[{"name":"r","blob":"AQID"},{"name":"t0007","blob":"__4"},{"name":"h","blob":"AA"}]}""", push.body)
        assertEquals("""{"items":[{"name":"h","blob":"CQ"}],"ended":true}""", last.body)
        assertEquals("DELETE /live/v1/BoxId_-123 HTTP/1.1", delete.line)
        for (r in server.requests) assertEquals("Bearer WriteTok_-9", r.headers["authorization"])
        assertEquals("application/json", push.headers["content-type"])
        assertFalse("the write token's hash is the server's own to make – and the token is never in a body",
            server.requests.any { it.body.contains("WriteTok") || it.body.contains("cd34") })
        // and the bodies are JSON a parser accepts
        assertEquals(3, org.json.JSONObject(push.body).getJSONArray("items").length())
        assertTrue(org.json.JSONObject(last.body).getBoolean("ended"))
    }

    /** Whatever the network does – busy, gone, no answer, nobody there – the box answers with a reply and never throws. */
    @Test fun everyFailureIsAReplyNeverAThrow() {
        val server = Server(listOf("429 Too Many Requests\r\nRetry-After: 120", "503 Service Unavailable", "410 Gone", "507 Insufficient Storage", null))
        val box = HttpLiveBox(server.base, "b", "w")
        val items = listOf(LivePush("h", byteArrayOf(1)))
        assertEquals(LiveReply(429, 120), box.push(items, false))
        assertEquals(LiveReply(503), box.push(items, false))
        assertTrue(box.push(items, false).gone)
        assertEquals(507, box.push(items, false).code)
        assertEquals("the connection closed without an answer", LiveReply(0), box.push(items, false))
        server.close()
        assertEquals("nobody listens", LiveReply(0), box.claim("a", "b"))
        assertEquals("no such host / not a URL", LiveReply(0), HttpLiveBox("not a url", "b", "w").delete())
        assertEquals(LiveReply(0), HttpLiveBox("http://host.invalid/", "b", "w").delete())
        assertEquals(120, HttpLiveBox.retryAfter(" 120 ")); assertNull(HttpLiveBox.retryAfter("Fri, 20 Nov 2026 10:00:00 GMT")); assertNull(HttpLiveBox.retryAfter(null))
    }

    // ---- the wiring, pinned on the sources ----

    /**
     * The recording service does not know the sharing at all – nothing of it can change what the recording does: the
     * lane reads the service's published state and the tour tables, and never writes to them.
     */
    @Test fun theRecordingServiceDoesNotKnowTheSharing() {
        val svc = sourceFile("recording/RecordingService.kt").readText()
        assertFalse("no live code in the recording service", Regex("""\bLive[A-Z]\w+|com\.onpath\.app\.live""").containsMatchIn(svc))
        // the lane's public calls only hand work to its thread
        val lane = sourceFile("live/LiveLane.kt").readText()
        for (f in listOf("attach()", "start(tourId: Long, name: String)", "stop()", "stateChanged()", "acknowledge()"))
            assertTrue("$f hands its work to the lane", lane.contains("fun $f = run("))
        assertFalse("no lock, no waiting in the lane", Regex("""synchronized|\.join\(|\.await\(|Thread\.sleep|runBlocking""").containsMatchIn(lane))
        // the live code never writes to the tour database
        for (f in listOf("live/DbLiveTrack.kt", "live/LiveEdge.kt", "live/LiveLane.kt", "live/HttpLiveBox.kt", "live/LiveFiles.kt"))
            assertFalse("$f only reads the tours", Regex("""writableDatabase|appendPoint|updateStats|insertTour|deleteTour|setDeleted|execSQL""").containsMatchIn(sourceFile(f).readText()))
        val tourDb = sourceFile("data/TourDb.kt").readText().substringAfter("// ---- live sharing").substringBefore("/** Returns (next seq")
        assertTrue("the two live reads use reader connections", tourDb.contains("readableDatabase") && !tourDb.contains("writableDatabase"))
    }
}
