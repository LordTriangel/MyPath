package com.onpath.app

import com.onpath.app.data.DataFiles
import com.onpath.app.data.DataStorage
import com.onpath.app.plansync.FakeTourStore
import com.onpath.app.data.FollowFile
import com.onpath.app.data.LegFiles
import com.onpath.app.data.MapPrefs
import com.onpath.app.data.Prefs
import com.onpath.app.data.Rd5PartStore
import com.onpath.app.data.Rd5Remote
import com.onpath.app.data.TourDb
import com.onpath.app.data.TourPlan
import com.onpath.app.debug.Problems
import com.onpath.app.gpx.GpxWaypoint
import com.onpath.app.gpx.TrackPoint
import com.onpath.app.map.OverviewPictures
import com.onpath.app.map.RouteThumbs
import com.onpath.app.plansync.PairScan
import com.onpath.app.plansync.PlanSyncPass
import com.onpath.app.plansync.SyncFiles
import com.onpath.app.plansync.SyncLinks
import com.onpath.app.plansync.SyncMark
import com.onpath.app.rides.RideChoice
import com.onpath.app.rides.RideRecord
import com.onpath.app.rides.TourStats
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.LegCache
import com.onpath.app.routing.Rd5PartsTest
import com.onpath.app.routing.RouteResult
import com.onpath.app.routing.Share
import com.onpath.app.search.OfflineIndex
import com.onpath.app.search.SearchStore
import com.onpath.app.sync.AesGcm
import com.onpath.app.sync.MemoryServer
import com.onpath.app.sync.SyncFormat
import com.onpath.app.sync.SyncItem
import com.onpath.app.sync.SyncKeys
import com.onpath.app.sync.SyncLedger
import com.onpath.app.sync.SyncRound
import com.onpath.app.sync.SyncTrackTest
import com.onpath.app.testkit.BrokenRows
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.random.Random

/**
 * Safety net for every stored file / column whose reader still lives in `:app`: follow.json, the stored plan in the
 * preferences, a tile's parts list and package.json, a stored leg, the remembered data folder, the place cache,
 * overview picture keys, route thumbnail keys, the tour database's JSON columns and the plan sync's own files.
 * Missing, empty, cut, garbage, wrong-typed or huge: never a throw, the plain fallback, a line in `Problems` where
 * it matters, and the next write works. One row per (file kind x damage); a failing row is named in the message.
 * The `:core` readers are in `core/…/BrokenFilesTest`.
 *
 * Replaces BrokenFollowFileTest, BrokenPlanPrefsTest, BrokenRoutingFilesTest, BrokenSmallFilesTest,
 * BrokenTourJsonTest and BrokenSyncFilesTest (audit group G01). The pass over broken sync files (C11 / C16: no route
 * lost or binned) is its own named test below.
 */
class BrokenAppFilesTest {
    private fun dir(): File = Files.createTempDirectory("broken-app").toFile()

    @Before fun reset() { Problems.init(dir()); Problems.clear() }

    private fun said(area: String) = Problems.snapshot().filter { it.area == area }

    /** Damages of a binary-ish file with [good] content (the small files: leg, data-dir, place cache, picture keys). */
    private fun broken(good: String): List<Pair<String, ByteArray>> = listOf(
        "empty" to ByteArray(0),
        "blank" to "\n".toByteArray(),
        "cut in half" to good.take(good.length / 2).toByteArray(),
        "cut after the first line" to (good.substringBefore('\n') + "\n").toByteArray(),
        "garbage text" to "\u0000ÿ{{garbage\n1\t2\t3\n".toByteArray(),
        "random bytes" to Random(11).nextBytes(2048),
    )

    @Test fun everyBrokenStoredFileIsSurvived() {
        val t = BrokenRows()
        followFile(t); planPrefs(t); routingFiles(t); smallFiles(t); tourJson(t); syncFiles(t)
        assertTrue("the table is not empty", t.size > 250)
        t.runAll()
    }

    // --- follow.json (the followed line; reloaded after a restart) and the parsed preferences ---

    private fun followFile(t: BrokenRows) {
        val good = """{"name":"Alm","pts":[46.5,11.2,46.6,11.3],"ele":[1200.0,null]}"""
        val cases = listOf(
            "empty" to "", "blank" to "\n", "garbage" to "\u0000ÿ{{garbage", "truncated" to good.take(good.length / 2),
            "an array at the top" to "[46.5,11.2]", "pts a string" to """{"name":"x","pts":"y"}""",
            "name a number" to """{"name":5,"pts":[46.5,11.2]}""", "a coordinate a word" to """{"name":"x","pts":[46.5,"a"]}""",
            "a null coordinate" to """{"name":"x","pts":[46.5,null]}""", "no name" to """{"pts":[46.5,11.2]}""",
        )
        for ((name, text) in cases) t.row("follow.json", name) {
            val d = dir(); Problems.init(d); Problems.clear()
            val f = FollowFile(File(d, FollowFile.NAME))
            File(d, "follow.json").writeText(text)
            assertNull(f.load())
            assertTrue("reported", Problems.snapshot().any { it.area == "data" && it.message.contains("unreadable") })
            f.save("Alm", listOf(GeoPoint(46.5, 11.2, 1200.0), GeoPoint(46.6, 11.3))) // the next save works and reloads
            val back = f.load()
            assertEquals("Alm", back!!.first)
            assertEquals(listOf(GeoPoint(46.5, 11.2, 1200.0), GeoPoint(46.6, 11.3)), back.second)
        }
        t.row("follow.json", "odd point count / heights of the wrong type / very long / missing") {
            val d = dir(); Problems.init(d); Problems.clear()
            val f = FollowFile(File(d, FollowFile.NAME))
            File(d, "follow.json").writeText("""{"name":"x","pts":[46.5,11.2,46.6]}""") // an odd number: the last half point is dropped
            assertEquals(1, f.load()!!.second.size)
            File(d, "follow.json").writeText("""{"name":"x","pts":[46.5,11.2],"ele":"z"}""") // heights of the wrong type: ignored
            assertNull(f.load()!!.second.single().ele)
            File(d, "follow.json").writeText("""{"name":"x","pts":[46.5,11.2],"ele":["q"]}""")
            assertNull(f.load()!!.second.single().ele)
            val big = buildString { append("{\"name\":\"long\",\"pts\":["); repeat(100_000) { append("46.5,11.2,") }; append("46.6,11.3]}") } // a 300 km track
            File(d, "follow.json").writeText(big)
            assertEquals(100_001, f.load()!!.second.size)
            Problems.clear()
            File(d, "follow.json").delete() // missing: nothing to restore, no report
            assertNull(f.load())
            assertTrue(Problems.snapshot().isEmpty())
            f.save("x", null) // deleting what is not there is fine
            assertNull(f.load())
        }
        t.row("preferences (hidden POI kinds, voice, recent places)", "broken values fall back to defaults") {
            val d = dir(); Problems.init(d)
            val sp = MapPrefs(mutableMapOf<String, Any?>("poiHidden" to mutableSetOf("no-such-kind", "", "\u0000"), "voiceFix" to "\u0000garbage"))
            val p = Prefs(sp, d)
            assertNotNull(p.settings.hiddenPoiKinds) // unknown names are simply unknown, never a throw
            assertNull(p.voiceFix)
            for (l in listOf("", "\t\t\t", "\u0000garbage", "a\tb\tc\td\te\tf\tg\th\ti", "a\tb\tc\td\t46.5\t11.2\tx\th\tNOPE",
                             "a\tb\tc\td\t46.5\t11.2\t\th\tPOI\tt\tv\ts\textra")) SearchStore.decode(l) // a cut or garbage line is skipped, never a throw
            assertNull(SearchStore.decode("a\tb\tc\td\te\tf\tg\th\ti"))
            assertNotNull(SearchStore.decode("a\tb\tc\td\t46.5\t11.2\tx\th\tNOPE"))
        }
    }

    // --- the plan the app restores at every start (preferences plan / planPinned / planSavedPoints / planLinkPoints) ---

    private fun planPrefs(t: BrokenRows) {
        val pts = listOf(GeoPoint(46.67171, 11.15003), GeoPoint(46.68512, 11.1347), GeoPoint(46.6902, 11.1611))
        val good: String = run { val sp = MapPrefs(); Prefs(sp, dir()).planWaypoints = pts; sp.getString("plan", null)!! }
        val cases = listOf(
            "empty" to "", "blank" to " \n", "cut in half" to good.take(good.length / 2), "cut inside the first point" to good.take(8),
            "one bracket" to "[", "garbage" to "\u0000ÿ{{garbage", "an object, not a list" to "{\"lat\":46.5,\"lon\":11.2}",
            "a list of numbers" to "[46.5,11.2]", "a point without lon" to "[{\"lat\":46.5}]",
            "a coordinate a word" to "[{\"lat\":\"north\",\"lon\":11.2}]", "a null point" to "[null]",
        )
        for ((what, text) in cases) for (key in listOf("plan", "planPinned", "planSavedPoints", "planLinkPoints")) t.row("plan pref $key", what) {
            val d = dir(); Problems.init(d); Problems.clear()
            val sp = MapPrefs(mutableMapOf<String, Any?>(key to text))
            val p = Prefs(sp, d)
            val got: Collection<GeoPoint>? = try {
                when (key) {
                    "plan" -> p.planWaypoints
                    "planPinned" -> p.planPinned
                    "planSavedPoints" -> p.planSavedPoints
                    else -> p.planLinkPoints
                }
            } catch (e: Throwable) { throw AssertionError("reading threw", e) }
            assertTrue("no half plan ($got)", got.isNullOrEmpty())
            if (text.isNotBlank()) assertTrue("reported", Problems.snapshot().any { it.area == "db" && it.message.contains("waypoint list unreadable") })
            p.planWaypoints = pts; p.planPinned = setOf(pts[1]); p.planSavedPoints = pts; p.planLinkPoints = pts // the next save works
            assertEquals(pts, Prefs(sp, d).planWaypoints)
            assertEquals(setOf(pts[1]), Prefs(sp, d).planPinned)
            assertEquals(pts, Prefs(sp, d).planSavedPoints)
            assertEquals(pts, Prefs(sp, d).planLinkPoints)
        }
        t.row("plan prefs", "nothing stored is an empty plan and says nothing") {
            val d = dir(); Problems.init(d); Problems.clear()
            val p = Prefs(MapPrefs(), d)
            assertTrue(p.planWaypoints.isEmpty()); assertTrue(p.planPinned.isEmpty())
            assertNull(p.planSavedPoints); assertNull(p.planLinkPoints); assertNull(p.planSavedName)
            assertEquals(-1L, p.planSavedId)
            assertTrue(Problems.snapshot().isEmpty())
        }
        t.row("plan prefs", "a good plan beside a broken part stays") {
            val d = dir(); Problems.init(d)
            val sp = MapPrefs(mutableMapOf<String, Any?>("plan" to good, "planPinned" to good.take(good.length / 2), "planSavedPoints" to "garbage"))
            val p = Prefs(sp, d)
            assertEquals(pts, p.planWaypoints)
            assertTrue(p.planPinned.isEmpty())
            assertTrue(p.planSavedPoints!!.isEmpty())
        }
    }

    // --- routing data: a tile's *.parts.json and the package's package.json ---

    private object NoRemote : Rd5Remote {
        override fun head(tile: String): Rd5Remote.Head = throw IllegalStateException("offline")
        override fun open(tile: String, from: Long, toExclusive: Long, etag: String?): InputStream = throw IllegalStateException("offline")
    }

    private fun routingFiles(t: BrokenRows) {
        val tile = "E10_N45.rd5"
        val (remoteFile, _, _) = Rd5PartsTest.fakeRemoteFile(dir(), Rd5PartsTest.sizes())
        val len = remoteFile.length()
        val good = """{"fileLength":$len,"parts":[1,6],"etag":"e","lastModified":1,"fetchedAt":2,"index":"QUJD","tail":"QUJD"}"""
        val cases = listOf(
            "empty" to "", "garbage" to "\u0000ÿ{{", "truncated" to good.take(good.length / 2), "array" to "[1]",
            "fileLength a word" to """{"fileLength":"x"}""",
            "parts a string" to """{"fileLength":$len,"parts":"x","fetchedAt":1,"index":"QUJD","tail":"QUJD"}""",
            "parts not numbers" to """{"fileLength":$len,"parts":["a"],"fetchedAt":1,"index":"QUJD","tail":"QUJD"}""",
            "index not base64" to """{"fileLength":$len,"parts":[1],"fetchedAt":1,"index":"@@@","tail":"!!"}""",
            "fields missing" to """{"fileLength":$len,"parts":[1]}""",
            "very large" to buildString { append("{\"fileLength\":$len,\"parts\":["); repeat(200_000) { append("1,") }; append("1]}") },
        )
        for ((name, text) in cases) t.row("*.parts.json", name) {
            val d = dir(); Problems.init(d); Problems.clear()
            val seg = dir()
            remoteFile.copyTo(File(seg, tile))
            File(seg, "E10_N45.parts.json").writeText(text)
            val s = Rd5PartStore(seg, NoRemote, now = { 5_000L }).state(tile) // never throws
            assertNotNull(s)
            assertTrue("falls back to the file's own index", s!!.parts.isNotEmpty())
            assertTrue("the rd5 is untouched", File(seg, tile).length() == len)
            assertTrue("reported", Problems.snapshot().any { it.area == "routing" && it.message.contains("unreadable") })
        }
        t.row("*.parts.json", "no list at all counts as a whole old download; a cut rd5 is nothing") {
            val seg = dir()
            remoteFile.copyTo(File(seg, tile))
            assertTrue(Rd5PartStore(seg, NoRemote).state(tile)!!.legacy)
            File(seg, tile).writeBytes(ByteArray(10)) // a cut rd5 (shorter than its index): nothing on the phone, no throw
            assertNull(Rd5PartStore(seg, NoRemote).state(tile))
        }
        val sizes = mapOf("base.pmtiles" to 10L)
        fun sizeOf(n: String) = sizes[n] ?: -1L
        val wrong = listOf(
            """{"files":"x","bbox":"y","region":5,"osm_date":7}""", """{"files":{"base.pmtiles":"x"},"bbox":["a",1],"region":[]}""",
            """{"files":[1],"bbox":{"a":1}}""", """{"files":{"base.pmtiles":null}}""", "{}",
        )
        for ((i, json) in wrong.withIndex()) t.row("package.json", "wrong types #${i + 1}") {
            val pc = DataFiles.PackageCheck.parse(json, ::sizeOf) // never throws for an object
            assertNotNull(pc.area); assertNotNull(pc.region)
        }
        t.row("package.json", "a file of the wrong size is still named") {
            assertEquals(listOf("base.pmtiles"), DataFiles.PackageCheck.parse("""{"files":{"base.pmtiles":"x"}}""", ::sizeOf).mismatched)
        }
        for (json in listOf("", "\u0000garbage", "[1]", "{\"files\":{")) t.row("package.json", "not an object: parse fails by design ('$json')") {
            // DataFiles.packageCheck catches and reports it, the check is skipped
            try { DataFiles.PackageCheck.parse(json, ::sizeOf) } catch (_: Exception) { return@row } // expected: the guard in packageCheck
            throw AssertionError("parse must fail so that packageCheck reports it")
        }
    }

    // --- the small files: a stored leg, data-dir.txt, the place cache, overview keys, thumbnail keys ---

    private fun smallFiles(t: BrokenRows) {
        val from = GeoPoint(46.60, 11.10); val to = GeoPoint(46.62, 11.11)
        val key = LegCache.key(from, to, Activity.HIKE, 2)
        val leg = RouteResult(listOf(from.copy(ele = 400.0), GeoPoint(46.61, 11.105, 410.0), to.copy(ele = 405.0)), 2345.6, 10.0, 5.0, 900.0,
            listOf(Share("path", 2345.6)), listOf(Share("gravel", 2345.6)), listOf(from, to))
        fun legs(root: File) = LegFiles(File(root, "legs"), { "r1-0123456789ab" }, { "1730000000000" })

        val legGood: String = run { val r = dir(); legs(r).put(key, from, to, leg); File(r, "legs/${LegFiles.name(key)}").readText() }
        for ((what, bytes) in broken(legGood)) t.row("stored leg", what) {
            val root = dir(); Problems.init(dir()); Problems.clear()
            legs(root).put(key, from, to, leg)
            val f = File(root, "legs/${LegFiles.name(key)}")
            assertNotNull("the good leg loads", legs(root).get(key))
            f.writeBytes(bytes)
            val store = legs(root)
            val got = try { store.get(key) } catch (e: Throwable) { throw AssertionError("reading threw", e) }
            assertNull("no half leg", got)
            assertFalse("the unreadable file is gone", f.exists())
            assertTrue("reported", said("routing").any { it.message.contains("stored leg") })
            store.put(key, from, to, leg) // routed again and stored: it loads
            assertEquals(leg.points, legs(root).get(key)!!.leg.points)
        }
        t.row("stored leg", "another leg's content under this name / a folder in its place / a missing folder") {
            val root = dir()
            legs(root).put(key, from, to, leg)
            val f = File(root, "legs/${LegFiles.name(key)}")
            val other = LegCache.key(to, from, Activity.HIKE, 2)
            legs(root).put(other, to, from, leg)
            File(root, "legs/${LegFiles.name(other)}").copyTo(f, overwrite = true)
            assertNull("a file copied / renamed by hand is not taken for this leg", legs(root).get(key))
            f.delete(); f.mkdirs()
            assertNull(legs(root).get(key))
            assertNull(LegFiles(File(root, "no-such-folder"), { "x" }, { "1" }).get(key))
        }

        for ((what, bytes) in broken("/storage/emulated/0/Android/data/com.onpath.app/files/data\n")) t.row("data-dir.txt", what) {
            val f = File(dir(), "data-dir.txt")
            val r = DataStorage.RememberedFile(f)
            assertNull("missing", r.read())
            f.writeBytes(bytes)
            val got = try { r.read() } catch (e: Throwable) { throw AssertionError("reading threw", e) }
            // whatever text is there is only a path to try – it never throws; nothing readable = the standard folder
            if (what == "empty" || what == "blank") assertNull(got)
            r.write("/data/next")
            assertEquals("/data/next", DataStorage.RememberedFile(f).read())
        }
        t.row("data-dir.txt", "a folder in its place is reported when written, never thrown") {
            Problems.init(dir()); Problems.clear()
            val f = File(dir(), "data-dir.txt"); f.mkdirs()
            val r = DataStorage.RememberedFile(f)
            assertNull(r.read())
            r.write("/data/x")
            assertTrue(said("data").any { it.message.contains("not remembered") })
        }

        fun placeSetup(): Triple<File, File, Pair<String, String>> {
            val root = dir()
            val cacheDir = File(root, "cache").apply { mkdirs() }
            val base = File(root, "base.pmtiles").apply { writeBytes(ByteArray(300) { 7 }) } // not a PMTiles file: no places, no throw
            assertTrue(OfflineIndex.baseMapPlaces(base, cacheDir).isEmpty())
            val cacheKey = "${base.length()}:${base.lastModified()}"
            val line = OfflineIndex.cacheLine(com.onpath.app.search.BaseMapPlaces.Item("Meran", listOf("Merano"), "place", "town", 46.67, 11.16, 325))
            File(cacheDir.listFiles()!!.single().path).writeText("$cacheKey\n$line\n")
            return Triple(base, cacheDir, cacheKey to line)
        }
        t.row("place cache", "a good cache is used") {
            val (base, cacheDir, _) = placeSetup()
            assertEquals(listOf("Meran"), OfflineIndex.baseMapPlaces(base, cacheDir).map { it.name })
        }
        val cacheGood = run { val (_, _, kl) = placeSetup(); "${kl.first}\n${kl.second}\n${kl.second}\n" }
        for ((what, bytes) in broken(cacheGood)) t.row("place cache", what) {
            val (base, cacheDir, _) = placeSetup()
            cacheDir.listFiles()!!.single().writeBytes(bytes)
            try { OfflineIndex.baseMapPlaces(base, cacheDir) } catch (e: Throwable) { throw AssertionError("reading threw", e) }
        }
        t.row("place cache", "right key, then cut or garbage lines: those are left out, the good one stays") {
            val (base, cacheDir, kl) = placeSetup()
            val (k, line) = kl
            cacheDir.listFiles()!!.single().writeText("$k\n$line\n${line.take(line.length / 2)}\n\u0000\t\t\t\t\t\t\nplace\ttown\tnorth\teast\t\tX\t\n")
            assertEquals(listOf("Meran"), OfflineIndex.baseMapPlaces(base, cacheDir).map { it.name })
        }
        t.row("place cache", "a base map cut off inside its header gives no places") {
            val (base, cacheDir, _) = placeSetup()
            base.writeBytes("PMTiles".toByteArray()); cacheDir.listFiles()!!.forEach { it.delete() }
            val got = try { OfflineIndex.baseMapPlaces(base, cacheDir) } catch (e: Exception) { null } // or an exception the index catches – OfflineIndex.safe
            assertTrue(got.isNullOrEmpty())
        }

        val keyGood: String = run {
            val root = dir(); val pics = OverviewPictures(File(root, "overview"))
            pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3)); File(root, "overview/u1.key").readText()
        }
        t.row("overview picture", "nothing stored") {
            val pics = OverviewPictures(File(dir(), "overview"))
            assertNull(pics.meta("u1")); assertNull(pics.bytes("u1", "k")); assertNull(pics.size()); assertNull(pics.size(done = true))
        }
        for ((what, bytes) in broken(keyGood)) t.row("overview picture key + size", what) {
            val root = dir()
            val pics = OverviewPictures(File(root, "overview"))
            assertTrue(pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3)))
            pics.setSize(800, 600, 24)
            val keyFile = File(root, "overview/u1.key"); val sizeFile = File(root, "overview/size")
            assertEquals(Triple(800, 600, 24), pics.size())
            keyFile.writeBytes(bytes); sizeFile.writeBytes(bytes)
            try {
                val m = pics.meta("u1")
                if (what != "cut in half") assertNull("key", m)
                assertNull("picture not taken for the key", pics.bytes("u1", "k").takeIf { m == null })
                assertNull("size", pics.size())
            } catch (e: Throwable) { throw AssertionError("reading threw", e) }
            assertTrue(pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3)))
            assertEquals("k", pics.meta("u1")!!.key)
            pics.setSize(800, 600, 24); assertEquals(Triple(800, 600, 24), pics.size())
        }
        t.row("overview picture key", "a folder in the key's place") {
            val root = dir(); val pics = OverviewPictures(File(root, "overview"))
            pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3))
            val keyFile = File(root, "overview/u1.key"); keyFile.delete(); keyFile.mkdirs()
            assertNull(pics.meta("u1"))
        }
        t.row("route thumbnail key", "nothing stored") { assertNull(RouteThumbs(File(dir(), "thumbs")).storedKey("u1")) }
        for ((what, bytes) in broken("line-key")) t.row("route thumbnail key", what) {
            val root = dir()
            val thumbs = RouteThumbs(File(root, "thumbs"))
            assertTrue(thumbs.write("u1", "line-key", byteArrayOf(1)))
            File(root, "thumbs/u1.key").writeBytes(bytes)
            val k = try { thumbs.storedKey("u1") } catch (e: Throwable) { throw AssertionError("reading threw", e) }
            // any other text is only "another line's key": the picture is made again
            if (what != "cut after the first line") assertTrue(k != "line-key") // a one-line file: that cut is the whole key
            assertTrue(thumbs.write("u1", "line-key", byteArrayOf(1)))
            assertEquals("line-key", thumbs.storedKey("u1"))
        }
        t.row("route thumbnail key", "a folder in the key's place") {
            val root = dir(); val thumbs = RouteThumbs(File(root, "thumbs"))
            thumbs.write("u1", "line-key", byteArrayOf(1))
            val tk = File(root, "thumbs/u1.key"); tk.delete(); tk.mkdirs()
            assertNull(thumbs.storedKey("u1"))
        }
    }

    // --- the tour database's JSON columns and a backup record's rides ---

    private fun tourJson(t: BrokenRows) {
        val garbage = listOf(
            "", " ", "\u0000ÿ{{", "[[46.5", "{\"a\":1}", "[1,2]", "[\"a\"]", "[null]", "[[46.5]]", "[[46.5,\"x\"]]", "[[46.5,11.2,\"x\"]]",
            "[{\"lat\":\"x\",\"lon\":1}]", "[{\"lon\":1}]", "[{\"lat\":1,\"lon\":2,\"ele\":\"e\"}]", "[1.5]", "5", "true",
        )
        for (s in garbage) t.row("tour JSON line / waypoints / pinned", "'$s'") {
            Problems.init(dir()); Problems.clear()
            TourPlan.lineFromJson(s); TourDb.jsonToWaypoints(s); TourDb.jsonToPinned(s) // only: no throw
        }
        t.row("tour JSON line / waypoints / pinned", "wrong types give the empty value and a report") {
            Problems.init(dir()); Problems.clear()
            assertEquals(emptyList<TrackPoint>(), TourPlan.lineFromJson("[[46.5,11.2,\"x\"]]"))
            assertEquals(emptyList<GpxWaypoint>(), TourDb.jsonToWaypoints("[{\"lat\":\"x\",\"lon\":1}]"))
            assertEquals(emptySet<Int>(), TourDb.jsonToPinned("[\"a\"]"))
            assertTrue(Problems.snapshot().any { it.area == "db" && it.message.contains("unreadable") })
        }
        t.row("tour JSON line / pinned", "huge values still load") {
            val line = buildString { append('['); repeat(200_000) { append("[46.5,11.2,1200.0],") }; append("[46.6,11.3]]") }
            assertEquals(200_001, TourPlan.lineFromJson(line).size)
            val pinned = buildString { append('['); repeat(100_000) { append("$it,") }; append("1]") }
            assertEquals(100_000, TourDb.jsonToPinned(pinned).size)
        }
        for (s in listOf("{\"name\":5}", "{\"waypoints\":5}", "{\"waypoints\":[1]}", "{\"source\":[]}", "[1]", "\"x\"")) t.row("tour plan record", "wrong type '$s'") {
            val o = try { JSONObject(s) } catch (_: Exception) { return@row } // expected: not an object at all – the column is unreadable before this
            assertNull(TourPlan.fromJson(o))
        }
        t.row("tour plan record", "two good points, the rest of the wrong type: kept without the broken parts") {
            val o = JSONObject("""{"waypoints":[{"lat":46.5,"lon":11.2},{"lat":46.6,"lon":11.3}],"line":"x","pinned":"y","sac":"x","distance":"d","updated":"u","activity":5}""")
            val plan = TourPlan.fromJson(o)!!
            assertEquals(2, plan.waypoints.size)
            assertTrue(plan.line.isEmpty()); assertTrue(plan.pinned.isEmpty())
            assertEquals(2, plan.sac); assertEquals(0.0, plan.distanceM, 0.0); assertEquals(0L, plan.updatedMs)
        }
        val bad = listOf(
            "\u0000garbage", "[1]", "{\"rides\":\"x\"}", "{\"rides\":[{\"from\":\"a\"}],\"choice\":\"NOPE\",\"recorded\":{}}",
            "{\"rides\":[],\"choice\":\"AS_RECORDED\",\"recorded\":{\"distance\":\"x\"}}",
            "{\"rides\":[{\"from\":1,\"to\":2,\"kind\":\"BOAT\"}],\"choice\":\"AS_RECORDED\",\"recorded\":{\"distance\":1,\"ascent\":1,\"descent\":1,\"duration\":1}}",
            "{\"rides\":[],\"choice\":\"AS_RECORDED\",\"recorded\":{\"distance\":1,\"ascent\":1,\"descent\":1,\"duration\":1},\"end\":\"x\"}",
        )
        for ((i, s) in bad.withIndex()) t.row("ride record", "wrong type #${i + 1}") {
            Problems.init(dir()); Problems.clear()
            assertNull(RideRecord.fromJson(s))
            assertTrue("reported", Problems.snapshot().any { it.area == "rides" })
        }
        t.row("ride record", "null / empty is no record; a good one round-trips") {
            assertNull(RideRecord.fromJson(null)); assertNull(RideRecord.fromJson(""))
            val r = RideRecord(emptyList(), RideChoice.AS_RECORDED, TourStats(1.0, 2.0, 3.0, 4.0), 5L)
            assertNotNull(RideRecord.fromJson(r.toJson().toString()))
        }
    }

    // --- the plan sync's own files (files/sync/) ---

    private fun id(n: Int) = SyncFormat.newId(ByteArray(16) { n.toByte() })
    private val dev = SyncFormat.newDevice(ByteArray(8) { 3 })
    private val secret = ByteArray(32) { (it * 3 + 5).toByte() }

    /** Every sync file as the app writes it. */
    private fun goodSync(dir: File): Map<String, String> {
        val f = SyncFiles(dir)
        f.savePairing(secret, 1234)
        f.device { n -> ByteArray(n) { 1 } }
        val ledger = SyncLedger.empty().apply {
            save(id(1), SyncTrackTest.PLAN, "Waalweg", 10, dev); save(id(2), SyncTrackTest.PLAN, "Leiter Alm", 11, dev); hello(dev, "Pixel 8", 12)
        }
        f.saveLedger(ledger)
        f.saveLinks(SyncLinks.empty().apply { link(id(1), "uuid-a"); link(id(2), "uuid-b"); markPending(id(2), agreedStar = true) })
        f.saveLastOk(99); f.saveEnded(77)
        f.marks.arrived("uuid-a")
        f.saveScan(PairScan.of(ledger, dev, 5000))
        return NAMES.associateWith { File(dir, it).readText() }
    }

    private fun brokenSync(good: String): List<Pair<String, ByteArray>> = listOf(
        "empty" to ByteArray(0),
        "blank" to "\n\n".toByteArray(),
        "cut in half" to good.take(good.length / 2).toByteArray(),
        "cut after the first line" to good.substringBefore('\n').toByteArray(),
        "one byte" to good.take(1).toByteArray(),
        "garbage text" to "\u0000ÿ{{garbage\nmore garbage 1 2 3\n".toByteArray(),
        "random bytes" to Random(7).nextBytes(512),
        "the header and garbage" to (good.substringBefore('\n') + "\n\u0000\u0001 x y z\n!!!\n\n- - -\n").toByteArray(),
        "another file's content" to "{\"v\":1,\"problems\":[]}".toByteArray(),
    )

    private val needRealHeader = setOf("cut after the first line", "one byte", "the header and garbage")

    private fun readAll(f: SyncFiles): List<Any?> = listOf(f.pairing(), f.device { n -> ByteArray(n) { 2 } }, f.ledger().entries.size,
        f.links().all.size, f.links().pending.size, f.lastOk(), f.ended(), f.marks.all().size, f.scan())

    private fun syncFiles(t: BrokenRows) {
        val texts = goodSync(dir())
        for (name in NAMES) for ((what, bytes) in brokenSync(texts.getValue(name))) t.row("sync/$name", what) {
            Problems.init(dir()); Problems.clear()
            val d = dir() // the good folder copied (plain copies: the app's own writes sync to disk and are slow)
            for (other in NAMES) File(d, other).writeText(texts.getValue(other))
            File(d, name).writeBytes(bytes)
            val f = SyncFiles(d)
            val got = try { readAll(f) } catch (e: Throwable) { throw AssertionError("a reader threw", e) }
            assertTrue("the device id is always a valid one", SyncFormat.isDevice(got[1] as String))
            when (name) { // the next write of the broken file works and reads back
                "pair.txt" -> { f.savePairing(secret, 4321); assertEquals(4321L, f.pairing()!!.since) }
                "device.txt" -> assertEquals("made once, then kept", got[1], SyncFiles(d).device { k -> ByteArray(k) { 5 } })
                "ledger.txt" -> {
                    f.saveLedger(SyncLedger.empty().apply { save(id(9), SyncTrackTest.PLAN, "Neu", 20, dev) })
                    assertEquals(listOf("Neu"), f.ledger().plans().map { it.title })
                }
                "links.txt" -> { f.saveLinks(SyncLinks.empty().apply { link(id(9), "uuid-z") }); assertEquals("uuid-z", f.links().tourOf(id(9))) }
                "status.txt" -> { f.saveLastOk(5); assertEquals(5L, f.lastOk()) }
                "ended.txt" -> { f.saveEnded(6); assertEquals(6L, f.ended()) }
                "marks.txt" -> { f.marks.arrived("uuid-n"); assertEquals(SyncMark.NEW, SyncFiles(d).marks.of("uuid-n")) }
                "scan.txt" -> { f.saveScan(PairScan(7)); assertEquals(7L, f.scan()!!.at) }
            }
        }
        // every file broken at once gives each reader's plain fallback
        for ((what, bytes) in brokenSync("x\ny\nz\n")) if (what !in needRealHeader) t.row("sync/ (all files)", "$what: plain fallbacks") {
            Problems.init(dir()); Problems.clear()
            val d = dir()
            for (name in NAMES) File(d, name).writeBytes(bytes)
            val f = SyncFiles(d)
            assertNull("not paired", f.pairing())
            assertEquals("an empty ledger", 0, f.ledger().entries.size)
            assertTrue("no links", f.links().all.isEmpty() && f.links().pending.isEmpty())
            assertEquals("never synced", 0L, f.lastOk())
            assertEquals("not ended", 0L, f.ended())
            assertTrue("no marks", f.marks.all().isEmpty())
            assertNull("no scan waiting", f.scan())
        }
        t.row("sync/pair.txt + scan.txt", "unreadable is reported, a missing one is not") {
            Problems.init(dir()); Problems.clear()
            val d = dir()
            val f = SyncFiles(d)
            readAll(f)
            assertTrue("nothing there yet: nothing to report", Problems.snapshot().none { it.area == "sync" })
            File(d, "pair.txt").writeText("garbage"); File(d, "scan.txt").writeText("garbage")
            assertNull(f.pairing()); assertNull(f.scan())
            val said = Problems.snapshot().filter { it.area == "sync" }.map { it.message }
            assertTrue("pairing: $said", said.any { it.contains("Pairing file unreadable") })
            assertTrue("scan: $said", said.any { it.contains("scan file unreadable") })
        }
        for (name in listOf("ledger.txt", "links.txt", "marks.txt")) for ((what, bytes) in brokenSync("x\ny\n")) if (what !in needRealHeader) t.row("sync/$name", "$what: reported, not silently empty") {
            Problems.init(dir()); Problems.clear()
            val d = dir()
            goodSync(d)
            Problems.clear()
            File(d, name).writeBytes(bytes)
            readAll(SyncFiles(d))
            assertTrue("reported (${Problems.snapshot().map { it.message }})", Problems.snapshot().any { it.area == "sync" && it.message.contains(name) })
        }
        t.row("sync/ (good files)", "good files and a ledger of a newer app (kept as it is) report nothing") {
            Problems.init(dir()); Problems.clear()
            val d = dir()
            goodSync(d); Problems.clear()
            readAll(SyncFiles(d))
            File(d, "ledger.txt").writeText("${SyncLedger.HEADER} ${SyncLedger.VERSION + 1} 0\nsomething new\n")
            readAll(SyncFiles(d))
            assertTrue("${Problems.snapshot().map { it.message }}", Problems.snapshot().none { it.area == "sync" })
        }
        t.row("sync/ (all files)", "a folder in each file's place reads as missing") {
            val d = dir()
            for (name in NAMES) File(d, name).mkdirs()
            val f = SyncFiles(d)
            assertNull(f.pairing()); assertNull(f.scan())
            assertEquals(0, f.ledger().entries.size); assertTrue(f.links().all.isEmpty())
            assertEquals(0L, f.lastOk()); assertEquals(0L, f.ended()); assertTrue(f.marks.all().isEmpty())
        }
    }

    // --- C11 / C16: a whole pass over broken sync files never loses a route on the phone and never bins one ---

    private val a = GeoPoint(46.67171, 11.15003)
    private val b = GeoPoint(46.68512, 11.13470)
    private val c = GeoPoint(46.69020, 11.16110)

    private inner class World(dir: File) {
        val keys = SyncKeys(secret)
        val server = MemoryServer()
        val store = FakeTourStore()
        var clock = 1_793_000_000_000L
        val rnd = Random(42)
        val files = SyncFiles(dir).also { it.savePairing(secret, clock) }
        val phone = PlanSyncPass(files, store, { server.box(it) }, { n -> rnd.nextBytes(n) }, { clock }, "Pixel 8",
            { if (it == SyncItem.TRACK) "Track" else "Route" }, AesGcm)
        val web = SyncFormat.newDevice(ByteArray(8) { 9 })
        val browser = SyncLedger.empty()
        var n = 0
        val browserRound = SyncRound(keys, server.box(keys), { ByteArray(12) { (n++).toByte() } })
        fun phoneSync() = phone.run { false }.also { clock += 1000 }
        fun browserSync() = browserRound.run(browser).also { clock += 1000 }
    }

    @Test fun aPassOverABrokenLedgerOrBrokenLinksNeverLosesOrBinsARoute() {
        var n = 0
        // the two files a pass decides by, and both at once; empty, cut in half, garbage text, random bytes
        for (name in listOf("ledger.txt", "links.txt", "both")) for (kind in listOf(0, 2, 5, 6)) {
            val d = dir()
            val w = World(d)
            w.store.savePlanned("Waalweg", listOf(a, b, c), Activity.HIKE, 3)
            w.store.savePlanned("Leiter Alm", listOf(a, c), Activity.MTB)
            w.browser.save(id(1), SyncTrackTest.PLAN, "Vom Browser", w.clock, w.web)
            w.phoneSync(); w.browserSync(); w.phoneSync(); w.browserSync()
            val before = w.store.live().map { it.name }.sorted()
            assertEquals(listOf("Leiter Alm", "Vom Browser", "Waalweg"), before)
            assertEquals(3, w.browser.plans().size)

            val targets = if (name == "both") listOf("ledger.txt", "links.txt") else listOf(name)
            var what = ""
            for (tg in targets) {
                val f = File(d, tg)
                val (w0, bytes) = brokenSync(if (f.isFile) f.readText() else "x\n")[kind]
                what = "$name $w0"
                f.writeBytes(bytes)
            }
            // a new process reads the broken files; two rounds each way
            val again = PlanSyncPass(SyncFiles(d), w.store, { w.server.box(it) }, { k -> w.rnd.nextBytes(k) }, { w.clock }, "Pixel 8",
                { if (it == SyncItem.TRACK) "Track" else "Route" }, AesGcm)
            repeat(2) {
                try { assertNotNull(what, again.run { false }) } catch (t: Throwable) { throw AssertionError("$what: the pass threw", t) }
                w.clock += 1000
                w.browserSync()
            }
            val live = w.store.live().map { it.name }
            assertTrue("$what: no route in the recycle bin (${w.store.list.filter { it.deletedMs != null }.map { it.name }})",
                w.store.list.none { it.deletedMs != null })
            for (tn in before) assertTrue("$what: \"$tn\" is still on the phone ($live)", tn in live)
            assertEquals("$what: no route changed its points", listOf(a, b, c),
                w.store.list.first { it.name == "Waalweg" }.waypoints.map { GeoPoint(it.lat, it.lon) })
            for (tn in before) assertTrue("$what: \"$tn\" is still in the browser (${w.browser.plans().map { it.title }})",
                w.browser.plans().any { it.title == tn })
            n++
        }
    }

    private companion object {
        val NAMES = listOf("pair.txt", "device.txt", "ledger.txt", "links.txt", "status.txt", "ended.txt", "marks.txt", "scan.txt")
    }
}
