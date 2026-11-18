package com.onpath.app

import com.onpath.app.backup.BackupIndex
import com.onpath.app.cells.CellFixtures
import com.onpath.app.cells.CellId
import com.onpath.app.cells.CellStore
import com.onpath.app.cells.MapCells
import com.onpath.app.cells.NetworkStatus
import com.onpath.app.debug.EventLog
import com.onpath.app.debug.Problems
import com.onpath.app.testkit.BrokenRows
import com.onpath.app.time.BreakDetector
import com.onpath.app.time.Factors
import com.onpath.app.time.PaceModel
import com.onpath.app.time.PlanTimesStore
import com.onpath.app.time.TourTimes
import com.onpath.app.time.TourTimesStore
import com.onpath.app.time.TripPace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Safety net for every stored file of the `:core` packages that the app reads at start: a missing, empty, cut,
 * garbage, wrong-typed or huge file never throws, is reported to `Problems` where it matters, never touches what must
 * stay (backup files, an installed map generation), and the next write / the next start works. One row per
 * (file kind x damage); a failing row is named in the message ("backup-index.json / truncated"). The readers that
 * still live in `:app` are in `BrokenAppFilesTest`.
 *
 * Replaces BrokenBackupIndexTest, BrokenCellFilesTest, BrokenProblemsFileTest and BrokenTimesFilesTest (audit group G01).
 */
class BrokenFilesTest {
    private fun dir(): File = Files.createTempDirectory("broken").toFile()

    /** A fresh folder with a fresh, empty problem log. */
    private fun fresh(): File = dir().also { Problems.init(it); Problems.clear() }

    private class Net : NetworkStatus {
        override fun connected() = false
        override fun unmetered() = false
    }

    @Test fun everyBrokenStoredFileIsSurvived() {
        val t = BrokenRows()
        backupIndex(t); cellFiles(t); problemsFiles(t); timesFiles(t)
        assertTrue("the table is not empty", t.size > 60)
        t.runAll()
    }

    // --- backup-index.json: which backup file belongs to which tour. Reading never rewrites it; the next save does. ---

    private fun backupIndex(t: BrokenRows) {
        val good = """{"u-1":{"store":"documents","handle":"h1","file":"2026-11-01 Alm.gpx","deleted":false}}"""
        val large = buildString { append('{'); repeat(30_000) { append("\"u-$it\":{\"store\":\"documents\",\"handle\":\"h$it\",\"file\":\"f$it.gpx\"},") }; append("\"last\":{\"store\":\"documents\",\"handle\":\"h\",\"file\":\"f.gpx\"}}") }
        val cases = listOf(
            "empty" to "",
            "blank" to "\n",
            "garbage" to "\u0000ÿ{{garbage",
            "truncated" to good.take(good.length / 2),
            "an array at the top" to "[1]",
            "entry a string" to """{"u-1":"x"}""",
            "entry fields of the wrong type" to """{"u-1":{"store":1,"handle":2,"file":3,"deleted":"x"}}""",
            "entry without a handle" to """{"u-1":{"store":"documents"}}""",
            "a good entry then a broken one" to """{"u-1":{"store":"documents","handle":"h1","file":"a.gpx"},"u-2":5}""",
            "very large" to large,
        )
        for ((name, text) in cases) t.row("backup-index.json", name) {
            val d = fresh()
            val f = File(d, "idx.json").apply { writeText(text) }
            val idx = BackupIndex(f) // never throws
            if (name != "very large") {
                assertNull(idx.get("u-1"))
                assertEquals(0, idx.count("documents"))
                assertEquals("reading never rewrites or deletes", text, f.readText())
                assertTrue("reported", Problems.snapshot().any { it.area == "backup" && it.message.contains("unreadable") })
            } else {
                assertEquals(30_001, idx.count("documents"))
            }
            idx.put("u-9", BackupIndex.Entry("documents", "h9", "2026-11-02 Tour.gpx", false))
            JSONObject(f.readText()) // valid again
            assertEquals("h9", BackupIndex(f).get("u-9")!!.handle)
        }
        t.row("backup-index.json", "missing, or no file at all") {
            val d = fresh()
            val idx = BackupIndex(File(d, "not-there.json"))
            assertNull(idx.get("u-1"))
            idx.put("u-1", BackupIndex.Entry("documents", "h", "f.gpx", false))
            assertEquals("h", BackupIndex(File(d, "not-there.json")).get("u-1")!!.handle)
            val none = BackupIndex(null)
            none.put("u-1", BackupIndex.Entry("documents", "h", "f.gpx", false))
            assertEquals("h", none.get("u-1")!!.handle)
            assertTrue("a missing index is not a problem", Problems.snapshot().none { it.area == "backup" })
        }
    }

    // --- map cells: a download's manifest.json, a generation's store.json, MapCells' requests.json / usage.json ---

    private fun cellFiles(t: BrokenRows) {
        val a = CellId(543, 361)
        val b = CellId(544, 361)
        val goodManifest = CellFixtures.cell(dir(), a, "v1").json
        fun cases(good: String) = listOf(
            "empty" to "",
            "garbage" to "\u0000ÿ{{garbage",
            "truncated" to good.take(good.length / 2),
            "an array at the top" to "[1]",
            "fields of the wrong type" to """{"cell":5,"version":[],"files":"x","generation":"g","cells":"c"}""",
            "entries of the wrong type" to """{"cell":"10-543-361","version":"v1","files":[1,2],"generation":1,"cells":[5]}""",
            "entries without the needed fields" to """{"cell":"10-543-361","version":"v1","files":[{"name":"base.pmtiles"}],"generation":1,"cells":[{"id":"nope"}]}""",
            "very large" to buildString { append("{\"cells\":["); repeat(50_000) { append("{\"id\":\"10-$it-1\",\"reason\":\"NEAR\",\"at\":1},") }; append("{}]}") },
        )
        // a broken DOWNLOAD is removed (it loads again), the good one beside it stays
        for ((name, text) in cases(goodManifest)) t.row("cell download manifest.json", name) {
            fresh()
            val store = CellStore(dir())
            val d = File(File(store.downloadDir, b.id), "v1").apply { mkdirs() }
            File(d, CellStore.MANIFEST).writeText(text)
            val goodDir = File(File(store.downloadDir, a.id), "v1")
            CellFixtures.cell(goodDir, a, "v1")
            val pending = store.pendingDownloads() // never throws
            assertEquals(listOf(a), pending.map { it.manifest.cell })
            assertFalse("the broken download goes", d.exists())
            assertTrue("the good one stays", goodDir.isDirectory)
        }
        t.row("cell download manifest.json", "a folder without a manifest at all") {
            val store = CellStore(dir())
            File(File(store.downloadDir, b.id), "v1").mkdirs()
            assertTrue(store.pendingDownloads().isEmpty())
        }
        // a broken GENERATION leaves the installed map alone: reading never deletes it, the installed one stays current
        for ((name, text) in cases("""{"generation":2,"cells":[]}""")) t.row("cell store.json", name) {
            fresh()
            val data = dir()
            val store = CellStore(data)
            val d = File(File(store.downloadDir, a.id), "v1")
            val g1 = store.apply(add = listOf(CellStore.Download(CellFixtures.cell(d, a, "v1"), d)))
            val broken = File(store.storeDir, "g000002").apply { mkdirs(); File(this, CellStore.STORE_JSON).writeText(text) }
            val reread = CellStore(data)
            assertEquals(g1.number, reread.current()!!.number)
            assertEquals(listOf(a), reread.current()!!.cells.map { it.id })
            assertTrue("reading never deletes", broken.isDirectory)
            assertTrue("reported", Problems.snapshot().any { it.area == "cells" && it.message.contains("g000002 unreadable") })
        }
        // broken request / usage files never stop the start
        for ((name, text) in cases("""{"cells":[{"id":"10-543-361","reason":"NEAR","own":true,"at":1,"mobileOk":false}]}""")) t.row("cells requests.json + usage.json", name) {
            fresh()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val store = CellStore(dir())
                store.root.mkdirs()
                File(store.root, "requests.json").writeText(text)
                File(store.root, "usage.json").writeText(if (name == "very large") "{\"10-543-361\":\"x\"}" else text)
                val mc = MapCells(store, null, Net(), scope, "0.8.0", regionPackages = false)
                mc.start() // never throws
                assertTrue(mc.installed.value.isEmpty())
            } finally { scope.cancel() }
        }
    }

    // --- problems.json and the event log: read at every start (Problems.init); the log keeps working ---

    private fun problemsFiles(t: BrokenRows) {
        val good = """{"problems":[{"level":"ERROR","area":"db","message":"boom","detail":null,"first":1,"last":2,"count":3}],""" +
            """"events":[{"time":5,"at":"x","area":"map","message":"hold"}]}"""
        val cases = listOf(
            "empty" to "",
            "blank" to " \n\t",
            "garbage" to "\u0000ÿnot json at all",
            "truncated" to good.take(good.length / 2),
            "an array at the top" to "[1,2,3]",
            "problems a string" to """{"problems":"x","events":"y"}""",
            "entries are numbers" to """{"problems":[1,2],"events":[3]}""",
            "fields of the wrong type" to """{"problems":[{"level":5,"area":[],"message":{},"detail":1,"first":"a","last":null,"count":"b"}],""" +
                """"events":[{"time":"t","area":1,"message":[]}]}""",
            "very large" to buildString { append("{\"problems\":["); repeat(20_000) { append("{\"level\":\"WARN\",\"area\":\"a\",\"message\":\"m$it\",\"first\":1,\"last\":1,\"count\":1},") }; append("]}") },
        )
        for ((name, text) in cases) {
            t.row("problems.json", name) {
                val d = dir()
                File(d, "problems.json").writeText(text)
                Problems.init(d) // never throws
                Problems.warn("test", "after $name")
                assertTrue(Problems.snapshot().any { it.area == "test" && it.message == "after $name" })
                Problems.flushForTest()
                JSONObject(File(d, "problems.json").readText()) // written again, valid
            }
            t.row("event log", name) {
                val d = dir()
                File(d, EventLog.CURRENT).writeText(text)
                File(d, EventLog.OLDER).writeBytes(ByteArray(3000) { 0xff.toByte() })
                Problems.init(d)
                Problems.trace("test", "after $name")
                assertTrue(Problems.events().any { it.message == "after $name" })
            }
        }
        t.row("problems folder", "missing") {
            val d = File(dir(), "gone")
            d.deleteRecursively()
            Problems.init(d)
            Problems.warn("test", "no folder")
            assertTrue(Problems.snapshot().any { it.message == "no folder" })
        }
    }

    // --- time files: tour-times.json, plan-times.json, the trip state of trip-pace.json ---

    private fun timesFiles(t: BrokenRows) {
        val goodTour = """{"8":{"moving":10.0,"breaks":2.0,"planned":12.0,"needed":12.0,"start":1,"end":2}}"""
        val goodPlan = """{"8":{"d":1000.0,"g":[1.0,2.0,3.0]}}"""
        fun cases(good: String) = listOf(
            "empty" to "",
            "blank" to "\n",
            "garbage" to "\u0000ÿ{{garbage",
            "truncated" to good.take(good.length / 2),
            "an array at the top" to "[1,2]",
            "entry a string" to """{"8":"x"}""",
            "entry a number" to """{"8":5}""",
            "fields of the wrong type" to """{"8":{"moving":"x","breaks":[],"planned":"p","needed":{},"start":"s","end":"e","d":"x","g":"y"}}""",
            "groups too short / not numbers" to """{"8":{"d":1000.0,"g":["a","b","c"]},"9":{"d":1000.0,"g":[1]}}""",
            "very large" to buildString { append('{'); repeat(50_000) { append("\"$it\":{\"d\":1,\"g\":[1,1,1],\"moving\":1,\"breaks\":0},") }; append("\"8\":{\"d\":1000.0,\"g\":[1.0,2.0,3.0],\"moving\":10.0,\"breaks\":2.0}}") },
        )
        for ((name, text) in cases(goodTour)) t.row("tour-times.json", name) {
            fresh()
            val f = File(dir(), TourTimesStore.FILE).apply { writeText(text) }
            val store = TourTimesStore(f)
            val got = store.get(8) // null or a value – but never a throw
            if (name != "very large") assertTrue(got == null || got.movingS >= 0.0)
            store.put(9, TourTimes(1.0, 2.0))
            assertEquals(TourTimes(1.0, 2.0), TourTimesStore(f).get(9))
        }
        t.row("tour-times.json", "missing") { assertNull(TourTimesStore(File(dir(), "missing.json")).get(8)) }
        for ((name, text) in cases(goodPlan)) t.row("plan-times.json", name) {
            fresh()
            val f = File(dir(), PlanTimesStore.FILE).apply { writeText(text) }
            val store = PlanTimesStore(f)
            val got = store.get(8, 1000.0)
            if (got != null) assertTrue(got.all { it.isFinite() && it >= 0 })
            store.put(9, 500.0, DoubleArray(PaceModel.GROUPS) { 7.0 })
            assertEquals(7.0, PlanTimesStore(f).get(9, 500.0)!![0], 0.0)
        }
        t.row("plan-times.json", "missing") { assertNull(PlanTimesStore(File(dir(), "missing.json")).get(8, 1000.0)) }
        t.row("tour-times.json", "garbage is reported, not hidden") {
            fresh()
            val f = File(dir(), TourTimesStore.FILE).apply { writeText("{garbage") }
            TourTimesStore(f).get(8)
            assertTrue(Problems.snapshot().any { it.area == "time" && it.message.contains("unreadable") })
        }
        val badTrips = listOf(
            """{"prior":"x","a":"y","p":5,"m":"z","warmEnd":[],"active":{},"along":null,"breaks":"q"}""",
            """{"prior":[1,"x",null],"a":[1],"p":[1,2,3,4,5,6,7],"m":-5,"warmEnd":-1,"active":"NaN","along":"-Infinity"}""",
            """{"breaks":{"aLat":"x","aLon":null,"on":"yes","count":"n","closed":-5,"inS":"i","lS":[]}}""",
            """{"breaks":5,"prior":{}}""",
        )
        for ((i, json) in badTrips.withIndex()) t.row("trip state in trip-pace.json", "wrong types #${i + 1}") {
            val trip = TripPace.fromJson(JSONObject(json))
            assertTrue(trip.movingS() >= 0.0)
            assertNotNull(TripPace.fromJson(JSONObject(json), Factors.ONE))
            assertTrue(BreakDetector.fromJson(JSONObject(json).optJSONObject("breaks")).breakS() >= 0.0)
        }
        t.row("tour summary JSON", "wrong types start fresh") {
            val tt = TourTimes.fromJson(JSONObject("""{"moving":"x","breaks":null,"planned":"p","needed":[],"start":"s","end":{}}"""))
            assertEquals(0.0, tt.movingS, 0.0)
            assertEquals(0.0, tt.breakS, 0.0)
        }
    }
}
