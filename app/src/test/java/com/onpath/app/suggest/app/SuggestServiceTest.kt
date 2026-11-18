package com.onpath.app.suggest.app

import com.onpath.app.cells.CellApi
import com.onpath.app.cells.CellId
import com.onpath.app.cells.CellManifest
import com.onpath.app.cells.CellStore
import com.onpath.app.routing.Activity
import com.onpath.app.routing.BRouterEngine
import com.onpath.app.suggest.Land
import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's entry to the tour suggestions (stage 4 without UI): [SuggestService] on the app's kind of data, the
 * [SuggestRunner] (a newer wish stops the old one, one run at a time), [SuggestFetch] (only the suggest file of a cell),
 * the settings copy in the assets. The real runs need E5_N50.rd5 and the Hilden content-2 suggest files on this computer
 * (ONPATH_SEGMENTS / SUGGEST_FILES, as NetReaderTest and the planner's WebSuggestTest) and are skipped otherwise.
 */
class SuggestServiceTest {
    private val home = System.getProperty("user.home")
    private val segments = File(System.getenv("ONPATH_SEGMENTS") ?: "$home/onpath-routing/segments")
    private val cells = File(System.getenv("SUGGEST_FILES") ?: "$home/onpath-suggest-poc/suggest-file/cells-c2")
    private val profiles = File("src/main/assets/brouter")
    private val settings = File("src/main/assets/${AppSuggest.SETTINGS_ASSET}")
    private val tmp = Files.createTempDirectory("suggestservice").toFile()
    private val locale = Locale.getDefault()
    private val hilden = SuggestAsk(51.168, 6.9395, Activity.HIKE, 2, doubleArrayOf(2.0, 3.0), arrayOf(null, 400.0))

    @After fun tidy() { tmp.deleteRecursively(); Locale.setDefault(locale) }

    private fun service(segmentsDir: File = segments, missingCell: IntArray? = null, routingMissing: (Collection<String>) -> List<String> = { emptyList() }) =
        SuggestService(segmentsDir, profiles, { x, y ->
            if (missingCell != null && missingCell[0] == x && missingCell[1] == y) File(tmp, "none.bin") else File(cells, "10-$x-$y/suggest.bin")
        }, { settings.readText() }, routingMissing)

    private fun needData() {
        assumeTrue("no E5_N50.rd5 in $segments", File(segments, "E5_N50.rd5").isFile)
        assumeTrue("no Hilden suggest files in $cells", File(cells, "10-531-341/suggest.bin").isFile)
    }

    @Test fun theSettingsInTheAppAreTheOneSettingsFile() {
        // one settings file for POC, planner and app (docs/design/tour-suggestions.md §7): copy it when it changes
        assertEquals("app/src/main/assets/suggest/settings.json differs – copy tools/suggest-poc/settings.json",
            File("../tools/suggest-poc/settings.json").readText(), settings.readText())
        assertEquals(BRouterEngine.PROFILE_ASSETS, AppSuggest.PROFILE_FILES)
    }

    @Test fun needNamesTheRoutingPartsAndTheCells() {
        val n = service().need(hilden)
        assertTrue(n.routingParts.toString(), n.routingParts.contains("E5_N50.rd5:E6_N51"))
        assertTrue(n.cells.any { it[0] == 531 && it[1] == 341 })
        assertEquals(n.cells.map { it.toList() }, Land.cellsFor(6.9395, 51.168, com.onpath.app.suggest.Character(hilden.wish(service().settings()), service().settings()).radius(service().settings())).map { it.toList() })
    }

    @Test fun missingRoutingDataIsLoadedFirst() {
        val r = service(routingMissing = { names -> names.filter { it.endsWith("E6_N51") } }).suggest(hilden)
        assertEquals(listOf("E5_N50.rd5:E6_N51"), r.routingToLoad)
        assertTrue(r.cards.isEmpty() && r.notes.isEmpty() && !r.noData)
    }

    @Test fun noRoutingDataAtAllGivesNoCardsNotACrash() {
        val r = service(segmentsDir = File(tmp, "segments").apply { mkdirs() }).suggest(hilden)
        assertTrue(r.noData)
        assertTrue(r.cards.isEmpty())
        assertEquals(1, r.notes.size)
    }

    @Test fun aRealRunGivesLightCards() {
        needData()
        Locale.setDefault(Locale.GERMAN)
        val r = service().suggest(hilden)
        assertTrue("${r.cards.size} cards, notes ${r.notes}", r.cards.size in 1..5)
        assertFalse(r.mapUpdating)
        assertTrue(r.cellsToFetch.isEmpty())
        assertTrue(r.loops >= r.cards.size)
        for (c in r.cards) {
            assertTrue(c.name.isNotBlank())
            assertTrue(c.km in 3.0..20.0)
            assertTrue(c.coords.size >= 8)
            val p = c.points()
            assertEquals(p.first(), p.last()) // a loop
            assertTrue(p.all { it.lat in 51.0..51.4 && it.lon in 6.7..7.2 })
            assertTrue(c.pills.count { it.strength } <= 2 && c.pills.count { !it.strength } <= 2)
        }
    }

    @Test fun aCellWithoutItsFileSaysTheMapIsUpdatingAndNamesIt() {
        needData()
        val r = service(missingCell = intArrayOf(531, 341)).suggest(hilden)
        assertTrue(r.mapUpdating)
        assertEquals(listOf(listOf(531, 341)), r.cellsToFetch.map { it.toList() })
        assertTrue(r.notes.isNotEmpty())
    }

    @Test fun theCheckStopsARun() {
        needData()
        val phases = AtomicInteger()
        try {
            service().suggest(hilden) { if (phases.incrementAndGet() == 3) throw CancellationException("newer wish") }
            fail("not stopped")
        } catch (e: CancellationException) {
            assertEquals(3, phases.get())
        }
    }

    @Test fun aNewerWishCancelsTheRunningOneAndRunsAfterIt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val running = AtomicInteger()
        val most = AtomicInteger()
        val runner = SuggestRunner({ ask, check ->
            most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                if (ask.lat == 1.0) { started.complete(Unit); while (true) { check(); Thread.sleep(5) } }
                SuggestCards.loadRouting(listOf("second"))
            } finally { running.decrementAndGet() }
        }, Dispatchers.IO)
        val first = async(Dispatchers.Default) { runner.suggest(SuggestAsk(1.0, 1.0, Activity.HIKE)) }
        started.await()
        val second = async(Dispatchers.Default) { runner.suggest(SuggestAsk(2.0, 2.0, Activity.HIKE)) }
        assertEquals(listOf("second"), second.await().routingToLoad)
        first.join()
        assertTrue(first.isCancelled)
        assertEquals(1, most.get()) // never two runs at once
    }

    @Test fun fetchTakesOnlyTheSuggestFile() {
        val store = CellStore(File(tmp, "data").apply { mkdirs() })
        val bin = File("../core/src/test/resources/suggest/tiny-suggest.bin")
        val c = CellId(531, 342)
        fun manifest(id: CellId, withSuggest: Boolean) = CellManifest(id, CellApi.FORMAT, "2026-11-17-s9", null, null,
            listOfNotNull(CellManifest.File("base.pmtiles", 9_000_000, "0".repeat(64), "base"),
                if (withSuggest) CellManifest.File(CellStore.SUGGEST, bin.length(), "1".repeat(64), "suggest") else null), 0, "{}")
        val asked = ArrayList<String>()
        val fetch = SuggestFetch(store, { id -> if (id == c) manifest(id, true) else if (id.x == 1) throw CellApi.ApiException(CellApi.Failure.NOT_READY, "not built")
            else manifest(id, false) }, { f, part -> asked += f.name; bin.copyTo(part, overwrite = true) })
        val out = fetch.fetch(listOf(intArrayOf(531, 342), intArrayOf(532, 342), intArrayOf(1, 1)))
        assertEquals(SuggestFetch.Outcome.FETCHED, out[c])
        assertEquals(SuggestFetch.Outcome.NOT_ON_SERVER, out[CellId(532, 342)]) // a cell before schema 9
        assertEquals(SuggestFetch.Outcome.NOT_ON_SERVER, out[CellId(1, 1)])
        assertEquals(listOf(CellStore.SUGGEST), asked) // never the cell's map files
        assertTrue(bin.readBytes().contentEquals(store.suggestFile(c).readBytes()))
        assertEquals(listOf("10-531-342.bin"), store.suggestDir.list()!!.toList())
    }
}
