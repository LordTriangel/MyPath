package com.onpath.app.cells

import com.onpath.app.debug.Problems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The store's side of "a new version downloads only what changed" ([CellStore.reusable], [CellStore.Reuse]): layers
 * the download leaves out come from the current generation – checked first, and never when they do not fit.
 */
class CellStoreReuseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val a = CellId(543, 361)
    private val b = CellId(544, 361)
    private val unchanged = setOf("overlays.pmtiles", "contours.pmtiles", "terrain.pmtiles", "pois.geojson")

    @Before fun setUp() = Problems.clear()

    private fun download(store: CellStore, c: CellId, version: String, tag: String, tags: Map<String, String> = emptyMap()): CellStore.Download {
        val dir = File(File(store.downloadDir, c.id), version)
        return CellStore.Download(CellFixtures.cell(dir, c, version, tag, tags = tags), dir)
    }

    /** [d] as the app downloads it: without the files the installed version already has. */
    private fun withReuse(store: CellStore, d: CellStore.Download): CellStore.Download {
        val r = store.reusable(d.manifest)
        r?.names?.forEach { assertTrue(File(d.dir, it).delete()) }
        store.writeManifest(d.dir, d.manifest, r)
        return d.copy(reuse = r)
    }

    private fun tileOf(dir: File, layer: String, c: CellId, z: Int = 12): String? =
        PmTiles.Reader(File(dir, layer)).use { r -> r.tile(z, c.x shl (z - 10), c.y shl (z - 10))?.let { String(it).substringBefore(':') } }

    private fun poiNames(dir: File): List<String> {
        val out = ArrayList<String>()
        GeoJsonMerge.forEachFeature(File(dir, "pois.geojson")) { out += it.asJsonObject["properties"].asJsonObject["name"].asString }
        return out.sorted()
    }

    private fun warnings() = Problems.snapshot().filter { it.area == "cells" && it.level >= Problems.Level.WARN }.map { it.message }

    @Test fun unchangedLayersComeFromTheCurrentGenerationAndTheNeighbourStays() {
        val store = CellStore(tmp.newFolder("data"))
        val g1 = store.apply(add = listOf(download(store, a, "v1", "A"), download(store, b, "v1", "B")))
        val a1 = g1.cell(a)!!
        assertEquals(6, a1.sha.size)
        assertEquals(unchanged + "base.pmtiles", a1.content.keys)
        store.deleteDownloads(a)

        val d2 = withReuse(store, download(store, a, "v2", "A", tags = mapOf("base.pmtiles" to "A2")))
        assertEquals(CellStore.Reuse("v1", unchanged), d2.reuse)
        // as found again after a restart
        assertEquals(d2.reuse, store.pendingDownloads().single { it.manifest.cell == a }.reuse)
        val g2 = store.apply(add = listOf(d2))
        val a2 = g2.cell(a)!!
        assertEquals("v2", a2.version)
        assertEquals("A2", tileOf(g2.dir, "base.pmtiles", a))
        for (l in listOf("overlays.pmtiles", "contours.pmtiles", "terrain.pmtiles")) {
            assertEquals("A", tileOf(g2.dir, l, a)); assertEquals("A", tileOf(g2.dir, l, a, z = 10))
            assertEquals("B", tileOf(g2.dir, l, b))
            assertEquals(l, a1.bytes[l], a2.bytes[l])
            assertEquals(l, a1.content[l], a2.content[l])
        }
        assertEquals("B", tileOf(g2.dir, "base.pmtiles", b))
        assertEquals(listOf("A 0", "A 1", "A 2", "B 0", "B 1", "B 2"), poiNames(g2.dir))
        assertEquals(a1.bytes["pois.geojson"], a2.bytes["pois.geojson"])
        assertEquals(a1.bytes[PoiTiles.FILE], a2.bytes[PoiTiles.FILE])
        assertEquals(a1.content["pois.geojson"], a2.content["pois.geojson"])
        assertTrue(a1.content["base.pmtiles"] != a2.content["base.pmtiles"])
        assertEquals(g1.cell(b), g2.cell(b))
        PmTiles.Reader(File(g2.dir, PoiTiles.FILE)).use { r -> assertNotNull(r.entries().firstOrNull()) }
        assertEquals(emptyList<String>(), warnings())
        // read again from disk: the hashes are in store.json, and the next version reuses them
        CellStore.forget(store.storeDir)
        assertEquals(a2, store.current()!!.cell(a))
        store.deleteDownloads(a)
        val d3 = withReuse(store, download(store, a, "v3", "A", tags = mapOf("base.pmtiles" to "A2", "pois.geojson" to "P3")))
        assertEquals(CellStore.Reuse("v2", unchanged - "pois.geojson" + "base.pmtiles"), d3.reuse)
        val g3 = store.apply(add = listOf(d3))
        assertEquals("A2", tileOf(g3.dir, "base.pmtiles", a))
        assertEquals(listOf("B 0", "B 1", "B 2", "P3 0", "P3 1", "P3 2"), poiNames(g3.dir))
    }

    @Test fun nothingToReuseWithoutAnInstalledVersionOrWhenEverythingChanged() {
        val store = CellStore(tmp.newFolder("data"))
        val d1 = download(store, a, "v1", "A")
        assertNull(store.reusable(d1.manifest))
        store.apply(add = listOf(d1))
        assertNull("the same version", store.reusable(d1.manifest))
        assertNull(store.reusable(download(store, a, "v2", "other").manifest))
        assertNull("another cell", store.reusable(download(store, b, "v2", "A").manifest))
        assertEquals(emptyList<String>(), warnings())
    }

    /** What lies in the store is read and compared with the hash kept at install: a damaged layer / POI file is not reused. */
    @Test fun aDamagedStoreFileIsNotReused() {
        val store = CellStore(tmp.newFolder("data"))
        val g1 = store.apply(add = listOf(download(store, a, "v1", "A")))
        store.deleteDownloads(a)
        java.io.RandomAccessFile(File(g1.dir, "terrain.pmtiles"), "rw").use { f -> f.seek(f.length() - 3); f.write('x'.code) }
        File(g1.dir, "pois.geojson").let { it.writeText(it.readText().replace("A 1", "A x")) }
        val d2 = download(store, a, "v2", "A", tags = mapOf("base.pmtiles" to "A2"))
        assertEquals(CellStore.Reuse("v1", setOf("overlays.pmtiles", "contours.pmtiles")), store.reusable(d2.manifest))
        assertTrue(warnings().toString(), warnings().any { "terrain.pmtiles" in it } && warnings().any { "pois.geojson" in it })
        // an unreadable one neither
        File(g1.dir, "overlays.pmtiles").writeText("no archive")
        assertEquals(CellStore.Reuse("v1", setOf("contours.pmtiles")), store.reusable(d2.manifest))
    }

    /** A download that leaves files to a version that is not the installed one is refused as a whole; the map stays as it is. */
    @Test fun aReuseThatDoesNotFitIsRefusedAndTheMapStays() {
        val store = CellStore(tmp.newFolder("data"))
        val g1 = store.apply(add = listOf(download(store, a, "v1", "A")))
        store.deleteDownloads(a)
        val d2 = withReuse(store, download(store, a, "v2", "A", tags = mapOf("base.pmtiles" to "A2")))
        val other = download(store, b, "v1", "B")
        fun refused(d: CellStore.Download, why: String) {
            try { store.apply(add = listOf(other, d)); fail(why) } catch (e: CellStore.ReuseException) { assertEquals(why, setOf(a), e.cells) }
            assertEquals(why, g1.number, store.current()!!.number)
            assertEquals(why, emptyList<String>(), store.storeDir.list()!!.filter { it.endsWith(".tmp") })
        }
        refused(d2.copy(reuse = d2.reuse!!.copy(from = "v0")), "another version")
        refused(d2.copy(reuse = d2.reuse!!.copy(names = d2.reuse!!.names + "base.pmtiles")), "a file that changed (and was downloaded)")
        refused(d2.copy(reuse = d2.reuse!!.copy(names = setOf("addresses/${a.id}.sqlite"))), "not a store file")
        // the installed cell was deleted meanwhile
        store.remove(setOf(a))
        try { store.apply(add = listOf(d2)); fail() } catch (e: CellStore.ReuseException) { assertEquals(setOf(a), e.cells) }
        assertTrue(store.installed().isEmpty())
        // a broken note makes the download unreadable (it would otherwise be joined without the layers it leaves out)
        File(d2.dir, CellStore.MANIFEST_REUSE).writeText("{")
        assertEquals(emptyList<CellStore.Download>(), store.pendingDownloads().filter { it.manifest.cell == a })
        assertTrue(!d2.dir.exists())
    }

    /** A folder that lacks a map layer of its manifest – and no note leaves it to the installed version – is never joined. */
    @Test fun aDownloadWithAMissingLayerIsRefused() {
        val store = CellStore(tmp.newFolder("data"))
        val fresh = download(store, b, "v1", "B")
        File(fresh.dir, "base.pmtiles").delete()
        try { store.apply(add = listOf(fresh)); fail() } catch (e: CellStore.ReuseException) { assertEquals(setOf(b), e.cells) }
        assertNull(store.current())
        val g1 = store.apply(add = listOf(download(store, a, "v1", "A")))
        store.deleteDownloads(a)
        val d2 = download(store, a, "v2", "A2")
        File(d2.dir, "pois.geojson").delete()
        try { store.apply(add = listOf(d2)); fail() } catch (e: CellStore.ReuseException) { assertEquals(setOf(a), e.cells) }
        assertEquals(g1.number, store.current()!!.number)
        assertEquals("v1", store.current()!!.cell(a)!!.version)
    }

    /**
     * A folder that leaves files to the installed version is no complete download for an app that does not know that
     * (older, downgraded: it reads manifest.json only, fails, deletes the folder and loads the area again); its
     * manifest and the note are one file.
     */
    @Test fun aReuseFolderHasNoPlainManifest() {
        val store = CellStore(tmp.newFolder("data"))
        store.apply(add = listOf(download(store, a, "v1", "A")))
        store.deleteDownloads(a)
        val d2 = withReuse(store, download(store, a, "v2", "A", tags = mapOf("base.pmtiles" to "A2")))
        assertTrue(!File(d2.dir, CellStore.MANIFEST).exists())
        // the old reader, as it was
        try { CellManifest.parse(File(d2.dir, CellStore.MANIFEST).readText()); fail() } catch (e: java.io.IOException) { /* expected: no manifest.json */ }
        assertEquals(listOf(CellStore.MANIFEST_REUSE), d2.dir.list()!!.filter { it.endsWith(".json") && it != "pois.geojson" })
        assertEquals(d2, store.pendingDownloads().single())
        // without anything left to the installed version: the plain manifest, and the other name is gone
        store.writeManifest(d2.dir, d2.manifest, null)
        assertEquals(listOf(CellStore.MANIFEST), d2.dir.list()!!.filter { it.endsWith(".json") })
        assertNull(store.pendingDownloads().single().reuse)
    }

    /** The address and suggest files lie beside the store: offered for a copy only when the installed version has that very file. */
    @Test fun filesBesideTheStoreAreOfferedOnlyForTheSameFile() {
        val store = CellStore(tmp.newFolder("data"))
        val d1 = download(store, a, "v1", "A")
        val addr = d1.manifest.files.first { it.name.startsWith("addresses/") }
        assertNull(store.installedFile(a, addr))
        store.apply(add = listOf(d1))
        assertEquals(File(store.addressesDir, "${a.id}.sqlite"), store.installedFile(a, addr))
        assertNull(store.installedFile(a, addr.copy(sha256 = "0".repeat(64))))
        assertNull(store.installedFile(a, d1.manifest.files.first { it.name == "base.pmtiles" }))
        assertNull(store.installedFile(b, addr))
    }
}
