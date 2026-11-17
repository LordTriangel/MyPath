package com.onpath.app.plansync

import com.onpath.app.debug.Problems
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.sync.AesGcm
import com.onpath.app.sync.MemoryServer
import com.onpath.app.sync.SyncFormat
import com.onpath.app.sync.SyncItem
import com.onpath.app.sync.SyncKeys
import com.onpath.app.sync.SyncLedger
import com.onpath.app.sync.SyncRound
import com.onpath.app.sync.SyncTrackTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Safety net for the plan sync's own files (`files/sync/`: pair.txt, device.txt, ledger.txt, links.txt, status.txt,
 * ended.txt, marks.txt, scan.txt – read at every start and before every pass). Missing, empty, cut in half, garbage
 * bytes or a folder in the file's place: no reader throws, each gives its plain fallback (not paired / an empty
 * ledger / no links / never synced), an unreadable pairing or scan is reported, and the next write works. And the
 * part that matters (C11, C16): a pass over a broken ledger or broken links never loses a route on the phone and
 * never moves one to the recycle bin.
 */
class BrokenSyncFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun reset() { Problems.init(tmp.newFolder("problems")); Problems.clear() }

    private fun id(n: Int) = SyncFormat.newId(ByteArray(16) { n.toByte() })
    private val dev = SyncFormat.newDevice(ByteArray(8) { 3 })
    private val secret = ByteArray(32) { (it * 3 + 5).toByte() }

    /** Every sync file as the app writes it. */
    private fun good(dir: File): Map<String, String> {
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

    /** What a crash, a full disk or a bad sector can leave of a file with [good] content. */
    private fun broken(good: String): List<Pair<String, ByteArray>> = listOf(
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

    /** Every reader of the folder, as the start and a pass call them; returns what they gave. */
    private fun readAll(f: SyncFiles): List<Any?> = listOf(f.pairing(), f.device { n -> ByteArray(n) { 2 } }, f.ledger().entries.size,
        f.links().all.size, f.links().pending.size, f.lastOk(), f.ended(), f.marks.all().size, f.scan())

    @Test fun noReaderThrowsOnABrokenFileAndTheNextWriteWorks() {
        val goodDir = tmp.newFolder("good")
        val texts = good(goodDir)
        assertEquals("every sync file is written by the set-up", NAMES.toSet(), goodDir.list()!!.toSet())
        var n = 0
        for (name in NAMES) for ((what, bytes) in broken(texts.getValue(name))) {
            // the good folder copied (plain copies: the app's own writes sync to disk, 70 folders of them take seconds)
            val dir = tmp.newFolder("case${n++}")
            for (other in NAMES) File(dir, other).writeText(texts.getValue(other))
            File(dir, name).writeBytes(bytes)
            val f = SyncFiles(dir)
            val got = try { readAll(f) } catch (t: Throwable) { throw AssertionError("$name $what: a reader threw", t) }
            assertTrue("$name $what: the device id is always a valid one", SyncFormat.isDevice(got[1] as String))
            // the next write of the broken file works and reads back
            when (name) {
                "pair.txt" -> { f.savePairing(secret, 4321); assertEquals("$name $what", 4321L, f.pairing()!!.since) }
                "device.txt" -> assertEquals("$name $what: made once, then kept", got[1], SyncFiles(dir).device { k -> ByteArray(k) { 5 } })
                "ledger.txt" -> {
                    f.saveLedger(SyncLedger.empty().apply { save(id(9), SyncTrackTest.PLAN, "Neu", 20, dev) })
                    assertEquals("$name $what", listOf("Neu"), f.ledger().plans().map { it.title })
                }
                "links.txt" -> { f.saveLinks(SyncLinks.empty().apply { link(id(9), "uuid-z") }); assertEquals("$name $what", "uuid-z", f.links().tourOf(id(9))) }
                "status.txt" -> { f.saveLastOk(5); assertEquals("$name $what", 5L, f.lastOk()) }
                "ended.txt" -> { f.saveEnded(6); assertEquals("$name $what", 6L, f.ended()) }
                "marks.txt" -> { f.marks.arrived("uuid-n"); assertEquals("$name $what", SyncMark.NEW, SyncFiles(dir).marks.of("uuid-n")) }
                "scan.txt" -> { f.saveScan(PairScan(7)); assertEquals("$name $what", 7L, f.scan()!!.at) }
            }
        }
    }

    @Test fun eachBrokenFileGivesItsPlainFallback() {
        for ((what, bytes) in broken("x\ny\nz\n")) {
            if (what == "cut after the first line" || what == "one byte" || what == "the header and garbage") continue // need the real header
            val dir = tmp.newFolder()
            for (name in NAMES) File(dir, name).writeBytes(bytes)
            val f = SyncFiles(dir)
            assertNull("$what: not paired", f.pairing())
            assertEquals("$what: an empty ledger", 0, f.ledger().entries.size)
            assertTrue("$what: no links", f.links().all.isEmpty() && f.links().pending.isEmpty())
            assertEquals("$what: never synced", 0L, f.lastOk())
            assertEquals("$what: not ended", 0L, f.ended())
            assertTrue("$what: no marks", f.marks.all().isEmpty())
            assertNull("$what: no scan waiting", f.scan())
        }
    }

    @Test fun anUnreadablePairingOrScanIsReportedAMissingOneIsNot() {
        val dir = tmp.newFolder()
        val f = SyncFiles(dir)
        readAll(f)
        assertTrue("nothing there yet: nothing to report", Problems.snapshot().none { it.area == "sync" })
        File(dir, "pair.txt").writeText("garbage"); File(dir, "scan.txt").writeText("garbage")
        assertNull(f.pairing()); assertNull(f.scan())
        val said = Problems.snapshot().filter { it.area == "sync" }.map { it.message }
        assertTrue("pairing: $said", said.any { it.contains("Pairing file unreadable") })
        assertTrue("scan: $said", said.any { it.contains("scan file unreadable") })
    }

    @Test fun aDamagedLedgerLinksOrMarksFileIsReportedNotSilentlyEmpty() {
        for (name in listOf("ledger.txt", "links.txt", "marks.txt")) for ((what, bytes) in broken("x\ny\n")) {
            if (what == "cut after the first line" || what == "one byte" || what == "the header and garbage") continue
            Problems.clear()
            val dir = tmp.newFolder()
            good(dir)
            Problems.clear()
            File(dir, name).writeBytes(bytes)
            readAll(SyncFiles(dir))
            assertTrue("$name $what: reported (${Problems.snapshot().map { it.message }})",
                Problems.snapshot().any { it.area == "sync" && it.message.contains(name) })
        }
        // good files, and a ledger of a newer app (kept as it is, SyncLedger.read): nothing to report
        val dir = tmp.newFolder()
        good(dir); Problems.clear()
        readAll(SyncFiles(dir))
        File(dir, "ledger.txt").writeText("${SyncLedger.HEADER} ${SyncLedger.VERSION + 1} 0\nsomething new\n")
        readAll(SyncFiles(dir))
        assertTrue("${Problems.snapshot().map { it.message }}", Problems.snapshot().none { it.area == "sync" })
    }

    @Test fun aFolderInAFilesPlaceReadsAsMissing() {
        val dir = tmp.newFolder()
        for (name in NAMES) File(dir, name).mkdirs()
        val f = SyncFiles(dir)
        assertNull(f.pairing()); assertNull(f.scan())
        assertEquals(0, f.ledger().entries.size); assertTrue(f.links().all.isEmpty())
        assertEquals(0L, f.lastOk()); assertEquals(0L, f.ended()); assertTrue(f.marks.all().isEmpty())
    }

    // --- a whole pass over broken files: nothing on the phone is lost or binned ---

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
            val dir = tmp.newFolder("pass${n++}")
            val w = World(dir)
            w.store.savePlanned("Waalweg", listOf(a, b, c), Activity.HIKE, 3)
            w.store.savePlanned("Leiter Alm", listOf(a, c), Activity.MTB)
            w.browser.save(id(1), SyncTrackTest.PLAN, "Vom Browser", w.clock, w.web)
            w.phoneSync(); w.browserSync(); w.phoneSync(); w.browserSync()
            val before = w.store.live().map { it.name }.sorted()
            assertEquals(listOf("Leiter Alm", "Vom Browser", "Waalweg"), before)
            assertEquals(3, w.browser.plans().size)

            val targets = if (name == "both") listOf("ledger.txt", "links.txt") else listOf(name)
            var what = ""
            for (t in targets) {
                val f = File(dir, t)
                val (w0, bytes) = broken(if (f.isFile) f.readText() else "x\n")[kind]
                what = "$name $w0"
                f.writeBytes(bytes)
            }
            // a new process reads the broken files; two rounds each way
            val again = PlanSyncPass(SyncFiles(dir), w.store, { w.server.box(it) }, { k -> w.rnd.nextBytes(k) }, { w.clock }, "Pixel 8",
                { if (it == SyncItem.TRACK) "Track" else "Route" }, AesGcm)
            repeat(2) {
                try { assertNotNull(what, again.run { false }) } catch (t: Throwable) { throw AssertionError("$what: the pass threw", t) }
                w.clock += 1000
                w.browserSync()
            }
            val live = w.store.live().map { it.name }
            assertTrue("$what: no route in the recycle bin (${w.store.list.filter { it.deletedMs != null }.map { it.name }})",
                w.store.list.none { it.deletedMs != null })
            for (t in before) assertTrue("$what: \"$t\" is still on the phone ($live)", t in live)
            assertEquals("$what: no route changed its points", listOf(a, b, c),
                w.store.list.first { it.name == "Waalweg" }.waypoints.map { GeoPoint(it.lat, it.lon) })
            // and the browser keeps its routes as well (a copy at worst, never fewer)
            for (t in before) assertTrue("$what: \"$t\" is still in the browser (${w.browser.plans().map { it.title }})",
                w.browser.plans().any { it.title == t })
        }
    }

    private companion object {
        val NAMES = listOf("pair.txt", "device.txt", "ledger.txt", "links.txt", "status.txt", "ended.txt", "marks.txt", "scan.txt")
    }
}
