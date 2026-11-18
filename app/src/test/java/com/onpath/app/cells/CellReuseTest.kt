package com.onpath.app.cells

import com.onpath.app.debug.Problems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * A new version of an installed map area downloads only the files that changed (owner 2026-11-17): what the manifest
 * names with the same sha256 as the installed version is taken from the phone – checked first – and the area is still
 * replaced as a whole. Against the fake doorman; its log says which files were fetched.
 */
class CellReuseTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: FakeDoorman
    private val scopes = ArrayList<CoroutineScope>()
    private val a = CellId(543, 361)
    private lateinit var v1: CellManifest
    private val addresses = "addresses/${a.id}.sqlite"
    private val all = setOf("base.pmtiles", "overlays.pmtiles", "contours.pmtiles", "terrain.pmtiles", "pois.geojson", addresses, "suggest.bin")

    private class Net : NetworkStatus {
        override fun connected() = true
        override fun unmetered() = true
    }

    @Before fun setUp() { server = FakeDoorman(tmp.newFolder("server")); server.buildPolls = 0; Problems.clear() }
    @After fun tearDown() { scopes.forEach { it.cancel() }; server.close() }

    /** One app run on [data]: its own worker, stopped with [CoroutineScope.cancel] ("the app was closed"). */
    private fun run(data: File): Pair<MapCells, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return MapCells(CellStore(data), CellApi(server.url, server.key, "0.8.0", 2000, 2000), Net(), scope, "0.8.0",
            pollDelayMs = { 30L }, backgroundPollMs = 30L, regionPackages = false).also { it.start() } to scope
    }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > end) fail("timeout waiting for $what")
            Thread.sleep(20)
        }
    }

    /** Files of [version] the phone asked the server for. */
    private fun fetched(version: String): Set<String> = server.log.filter { it.startsWith("GET /v1/files/") }
        .map { it.removePrefix("GET /v1/files/").substringBefore(" ") }.filter { "/$version/" in it }.map { it.substringAfter("/$version/") }.toSet()

    private fun sizeOf(m: CellManifest, names: Set<String>) = m.files.filter { it.name in names }.sumOf { it.size }

    private fun tileOf(dir: File, layer: String, t: Triple<Int, Int, Int>): String? =
        PmTiles.Reader(File(dir, layer)).use { r -> r.tile(t.first, t.second, t.third)?.let { String(it) } }

    private fun poiNames(dir: File): List<String> {
        val out = ArrayList<String>()
        GeoJsonMerge.forEachFeature(File(dir, "pois.geojson")) { out += it.asJsonObject["properties"].asJsonObject["name"].asString }
        return out.sorted()
    }

    /** Every tile, POI, the addresses and the suggest file of [a] in the map are the ones of the tags given. */
    private fun assertMapIs(mc: MapCells, version: String, tag: String = "T", base: String = tag, suggest: String) {
        assertEquals(version, mc.installed.value.single { it.id == a }.version)
        val dir = mc.mapDir()!!
        for (layer in CellFixtures.LAYERS.keys) for (t in CellFixtures.cellTiles(a)) {
            val want = if (layer == "base.pmtiles") base else tag
            assertEquals("$want:$layer:${t.first}/${t.second}/${t.third}", tileOf(dir, layer, t))
        }
        assertEquals(listOf("$tag 0", "$tag 1", "$tag 2"), poiNames(dir))
        assertTrue(File(dir, PoiTiles.FILE).isFile)
        assertEquals("addresses of $tag", File(mc.store.addressesDir, "${a.id}.sqlite").readText())
        assertEquals("suggest of $suggest", mc.store.suggestFile(a).readText())
        assertFalse("no download folder left", File(mc.store.downloadDir, a.id).exists())
    }

    private fun installV1(data: File): Pair<MapCells, CoroutineScope> {
        v1 = server.publish(a, "v1", tag = "T", suggest = "s1")
        val r = run(data)
        r.first.request(listOf(a), CellReason.POINT)
        waitFor("v1") { r.first.state(a) == CellState.Ready("v1") }
        assertEquals(all, fetched("v1"))
        return r
    }

    private fun warnings() = Problems.snapshot().filter { it.area == "cells" && it.level >= Problems.Level.WARN }

    /** The owner's case: a schema bump that only brought a new suggest.bin loads only that file. */
    @Test fun aNewVersionWhereOnlyTheSuggestFileChangedDownloadsOnlyThatFile() {
        val (mc, _) = installV1(tmp.newFolder("data"))
        val v2 = server.publish(a, "v2", tag = "T", suggest = "s2")
        mc.request(listOf(a), CellReason.REFRESH)
        waitFor("v2") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(setOf("suggest.bin"), fetched("v2"))
        assertMapIs(mc, "v2", suggest = "s2")
        assertEquals(emptyList<Problems.Entry>(), warnings())
        // the trace says what was kept and what was loaded; the size the UI shows is what really loads
        val kept = sizeOf(v2, all - "suggest.bin")
        val line = Problems.events().map { it.message }.first { it.startsWith("Download finished: ${a.id}") } // newest first
        assertTrue(line, "reused 6 files" in line)
        assertEquals(sizeOf(v2, setOf("suggest.bin")), mc.knownSize(a))
        println("bytes saved: ${v2.totalSize} in the version, $kept reused, ${v2.totalSize - kept} downloaded – $line")
        // and the next version reuses what this one kept (the hashes went along)
        server.publish(a, "v3", tag = "T", suggest = "s3", tags = mapOf("base.pmtiles" to "B3"))
        mc.request(listOf(a), CellReason.REFRESH)
        waitFor("v3") { mc.state(a) == CellState.Ready("v3") }
        assertEquals(setOf("suggest.bin", "base.pmtiles"), fetched("v3"))
        assertMapIs(mc, "v3", base = "B3", suggest = "s3")
    }

    /** A file on the phone that is not what was installed (damaged, changed) never counts: it is downloaded. */
    @Test fun aDamagedFileOnThePhoneIsDownloadedAgain() {
        val (mc, _) = installV1(tmp.newFolder("data"))
        // the address file was changed, and one byte of the map's base tiles flipped
        File(mc.store.addressesDir, "${a.id}.sqlite").writeText("something else")
        RandomAccessFile(File(mc.mapDir()!!, "base.pmtiles"), "rw").use { f ->
            f.seek(f.length() - 1); val b = f.read(); f.seek(f.length() - 1); f.write(b xor 1)
        }
        server.publish(a, "v2", tag = "T", suggest = "s2")
        mc.request(listOf(a), CellReason.REFRESH)
        waitFor("v2") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(setOf("suggest.bin", "base.pmtiles", addresses), fetched("v2"))
        assertMapIs(mc, "v2", suggest = "s2")
        val w = warnings().map { it.message }
        assertTrue(w.toString(), w.any { "base.pmtiles" in it } && w.any { addresses in it })
    }

    /** Nothing installed: everything is downloaded, as before. */
    @Test fun withoutAnInstalledVersionEverythingIsDownloaded() {
        val (mc, _) = installV1(tmp.newFolder("data"))
        assertMapIs(mc, "v1", suggest = "s1")
        assertEquals(v1.totalSize, mc.knownSize(a))
        assertEquals(emptyList<Problems.Entry>(), warnings())
    }

    /** A map installed by an app before the hashes were kept (store.json without them): downloaded in full. */
    @Test fun anAreaInstalledWithoutHashesIsDownloadedInFull() {
        val data = tmp.newFolder("data")
        val (first, scope) = installV1(data)
        scope.cancel()
        val json = File(first.mapDir()!!, CellStore.STORE_JSON)
        val o = JSONObject(json.readText())
        o.getJSONArray("cells").getJSONObject(0).apply { remove("sha"); remove("content") }
        json.writeText(o.toString())
        CellStore.forget(first.store.storeDir)
        server.publish(a, "v2", tag = "T", suggest = "s2")
        val (mc, _) = run(data)
        mc.request(listOf(a), CellReason.REFRESH)
        waitFor("v2") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(all, fetched("v2"))
        assertMapIs(mc, "v2", suggest = "s2")
        assertEquals(emptyList<Problems.Entry>(), warnings())
    }

    /** The connection breaks, the app is closed: the next run goes on where it stopped and still fetches only the changed files. */
    @Test fun anInterruptedDownloadGoesOnAndStillLoadsOnlyTheChangedFiles() {
        val data = tmp.newFolder("data")
        val (first, scope) = installV1(data)
        server.publish(a, "v2", tag = "T", suggest = "s2", tags = mapOf("base.pmtiles" to "B2"))
        server.cutAfter = 10
        first.request(listOf(a), CellReason.REFRESH)
        waitFor("download broke") { first.state(a) is CellState.Offline }
        assertMapIs0(first, "v1")
        scope.cancel()
        val before = server.log.size
        server.cutAfter = null
        val (mc, _) = run(data)
        waitFor("v2 after the restart") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(setOf("suggest.bin", "base.pmtiles"), fetched("v2"))
        assertTrue("resumed with Range", server.log.drop(before).any { "/v2/base.pmtiles" in it && "Range:bytes=10-" in it })
        assertMapIs(mc, "v2", base = "B2", suggest = "s2")
    }

    private fun assertMapIs0(mc: MapCells, version: String) {
        assertEquals(version, mc.installed.value.single().version)
        assertEquals("T:base.pmtiles:10/${a.x}/${a.y}", tileOf(mc.mapDir()!!, "base.pmtiles", Triple(10, a.x, a.y)))
    }

    /** While a new version comes in, the map is the whole old version or the whole new one – never a mix. */
    @Test fun theMapIsNeverAMixOfTwoVersions() {
        val (mc, _) = installV1(tmp.newFolder("data"))
        server.publish(a, "v2", tag = "T", suggest = "s2", tags = mapOf("base.pmtiles" to "B2"))
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val stop = java.util.concurrent.atomic.AtomicBoolean()
        val t0 = Triple(10, a.x, a.y)
        val watcher = Thread {
            while (!stop.get()) {
                val g = mc.store.current() ?: continue
                val v = g.cell(a)?.version ?: continue
                try {
                    // the suggest file is replaced right after the generation (as the addresses always were): not read here
                    seen += "$v base=${tileOf(g.dir, "base.pmtiles", t0)?.substringBefore(':')} overlays=${tileOf(g.dir, "overlays.pmtiles", t0)?.substringBefore(':')}"
                } catch (e: java.io.IOException) { /* expected: an older generation removed while it was read */ }
            }
        }.apply { start() }
        mc.request(listOf(a), CellReason.REFRESH)
        waitFor("v2") { mc.state(a) == CellState.Ready("v2") }
        Thread.sleep(50); stop.set(true); watcher.join()
        assertEquals(setOf("v1 base=T overlays=T", "v2 base=B2 overlays=T"), seen.toSet())
        assertMapIs(mc, "v2", base = "B2", suggest = "s2")
    }

    /**
     * A finished download that leaves files to the installed version, and that version is no longer the one installed
     * (here: the app was closed before it was joined, and the folder names another version): it is not joined – the map
     * stays whole – it is reported, and the area loads again in full.
     */
    @Test fun aDownloadWhoseKeptFilesNoLongerFitIsDroppedAndLoadedInFull() {
        val data = tmp.newFolder("data")
        val (first, scope) = installV1(data)
        scope.cancel()
        server.publish(a, "v2", tag = "T2", suggest = "s2")
        val dir = File(File(first.store.downloadDir, a.id), "v2")
        File(server.root, "cells/f2/${a.path}/v2").copyRecursively(dir)
        File(dir, "overlays.pmtiles").delete()
        // the manifest of such a folder has its own name, with the note in it (an app that does not know it never joins it)
        val manifest = File(dir, "manifest.json")
        File(dir, "manifest.reuse.json").writeText(JSONObject().put("from", "v0").put("files", org.json.JSONArray(listOf("overlays.pmtiles")))
            .put("manifest", manifest.readText()).toString())
        manifest.delete()
        val (mc, _) = run(data)
        waitFor("v2 in full") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(all, fetched("v2"))
        assertMapIs(mc, "v2", tag = "T2", suggest = "s2")
        assertTrue(warnings().toString(), warnings().any { a.id in it.message && it.level == Problems.Level.WARN })
    }

    /**
     * A "complete" download folder that lacks a map layer and says nothing about it (a power cut, a folder of another
     * app version): never joined – the area would have no base map – but reported and loaded again in full.
     */
    @Test fun aCompleteFolderWithAMissingLayerIsNotJoinedAndTheAreaLoadsInFull() {
        val data = tmp.newFolder("data")
        val (first, scope) = installV1(data)
        scope.cancel()
        server.publish(a, "v2", tag = "T2", suggest = "s2")
        val dir = File(File(first.store.downloadDir, a.id), "v2")
        File(server.root, "cells/f2/${a.path}/v2").copyRecursively(dir)
        File(dir, "base.pmtiles").delete()
        val (mc, _) = run(data)
        waitFor("v2 in full") { mc.state(a) == CellState.Ready("v2") }
        assertEquals(all, fetched("v2"))
        assertMapIs(mc, "v2", tag = "T2", suggest = "s2")
        assertTrue(warnings().toString(), warnings().any { a.id in it.message && it.level == Problems.Level.WARN })
    }

    /**
     * The user's Update stays an Update: downloaded during a tour with files left to the installed version, the note no
     * longer fits when the tour ends → asked again as an update (the area shows as loading), in full.
     */
    @Test fun anUpdateWhoseKeptFilesNoLongerFitIsAskedAgainAsAnUpdate() {
        val (mc, _) = installV1(tmp.newFolder("data"))
        server.publish(a, "v2", tag = "T", suggest = "s2")
        mc.holdUpdates = true
        mc.request(listOf(a), CellReason.UPDATE, ownPoint = true)
        waitFor("downloaded during the tour") { mc.state(a) == CellState.Downloaded }
        assertEquals(setOf("suggest.bin"), fetched("v2"))
        val note = File(File(File(mc.store.downloadDir, a.id), "v2"), "manifest.reuse.json")
        note.writeText(JSONObject(note.readText()).put("from", "v0").toString())
        mc.holdUpdates = false
        waitFor("v2 in full") { mc.state(a) == CellState.Ready("v2") && mc.installed.value.single().version == "v2" }
        assertEquals(all, fetched("v2"))
        assertMapIs(mc, "v2", suggest = "s2")
        val asked = Problems.events().map { it.message }.filter { it.startsWith("Requested 1 region(s)") }
        assertEquals(asked.toString(), 2, asked.count { "(update)" in it })
    }
}
