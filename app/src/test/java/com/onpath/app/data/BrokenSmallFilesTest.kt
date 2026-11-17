package com.onpath.app.data

import com.onpath.app.debug.Problems
import com.onpath.app.map.OverviewPictures
import com.onpath.app.map.RouteThumbs
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.LegCache
import com.onpath.app.routing.RouteResult
import com.onpath.app.routing.Share
import com.onpath.app.search.OfflineIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Safety net for the small files the app writes and reads back by itself, beside the ones with their own Broken…
 * tests: a stored route leg (`legs/…leg`, read when a saved route loads), the remembered data folder (`data-dir.txt`,
 * read before anything else at start), the search's place cache, an overview picture's key / size and a route
 * thumbnail's key. Missing, empty, cut in half, garbage bytes or a folder in the file's place: never a throw, the
 * plain fallback (routed again / the standard folder / read again / drawn again), a line in Problems, and the next
 * write works.
 */
class BrokenSmallFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun reset() { Problems.init(tmp.newFolder("problems")); Problems.clear() }

    private fun broken(good: String): List<Pair<String, ByteArray>> = listOf(
        "empty" to ByteArray(0),
        "blank" to "\n".toByteArray(),
        "cut in half" to good.take(good.length / 2).toByteArray(),
        "cut after the first line" to (good.substringBefore('\n') + "\n").toByteArray(),
        "garbage text" to "\u0000ÿ{{garbage\n1\t2\t3\n".toByteArray(),
        "random bytes" to Random(11).nextBytes(2048),
    )

    private fun said(area: String) = Problems.snapshot().filter { it.area == area }

    // --- a stored leg ---

    private val from = GeoPoint(46.60, 11.10); private val to = GeoPoint(46.62, 11.11)
    private val key = LegCache.key(from, to, Activity.HIKE, 2)
    private val leg = RouteResult(listOf(from.copy(ele = 400.0), GeoPoint(46.61, 11.105, 410.0), to.copy(ele = 405.0)), 2345.6, 10.0, 5.0, 900.0,
        listOf(Share("path", 2345.6)), listOf(Share("gravel", 2345.6)), listOf(from, to))

    private fun legs() = LegFiles(File(tmp.root, "legs"), { "r1-0123456789ab" }, { "1730000000000" })

    @Test fun aBrokenStoredLegIsRoutedAgainNeverThrown() {
        legs().put(key, from, to, leg)
        val f = File(tmp.root, "legs/${LegFiles.name(key)}")
        val good = f.readText()
        assertNotNull("the good leg loads", legs().get(key))
        for ((what, bytes) in broken(good)) {
            Problems.clear()
            f.writeBytes(bytes)
            val store = legs()
            val got = try { store.get(key) } catch (t: Throwable) { throw AssertionError("leg $what: reading threw", t) }
            assertNull("leg $what: no half leg", got)
            assertFalse("leg $what: the unreadable file is gone", f.exists())
            assertTrue("leg $what: reported", said("routing").any { it.message.contains("stored leg") })
            // routed again and stored: it loads
            store.put(key, from, to, leg)
            assertEquals("leg $what", leg.points, legs().get(key)!!.leg.points)
        }
        // another leg's content under this name (a file copied / renamed by hand): not taken for this leg
        val other = LegCache.key(to, from, Activity.HIKE, 2)
        legs().put(other, to, from, leg)
        File(tmp.root, "legs/${LegFiles.name(other)}").copyTo(f, overwrite = true)
        assertNull(legs().get(key))
        // a folder in the file's place, a missing folder
        f.delete(); f.mkdirs()
        assertNull(legs().get(key))
        assertNull(LegFiles(File(tmp.root, "no-such-folder"), { "x" }, { "1" }).get(key))
    }

    // --- the remembered data folder ---

    @Test fun aBrokenRememberedDataFolderFallsBackToTheStandardOne() {
        val f = File(tmp.root, "data-dir.txt")
        val r = DataStorage.RememberedFile(f)
        assertNull("missing", r.read())
        for ((what, bytes) in broken("/storage/emulated/0/Android/data/com.onpath.app/files/data\n")) {
            f.writeBytes(bytes)
            val got = try { r.read() } catch (t: Throwable) { throw AssertionError("data-dir $what: reading threw", t) }
            // whatever text is there is only a path to try – it never throws; nothing readable = the standard folder
            if (what == "empty" || what == "blank") assertNull(what, got)
            r.write("/data/next")
            assertEquals(what, "/data/next", DataStorage.RememberedFile(f).read())
        }
        f.delete(); f.mkdirs()
        assertNull("a folder in its place", r.read())
        r.write("/data/x") // cannot be written over a folder: reported, never thrown
        assertTrue(said("data").any { it.message.contains("not remembered") })
    }

    // --- the search's place cache ---

    @Test fun aBrokenPlaceCacheIsReadAgainFromTheMap() {
        val cacheDir = tmp.newFolder("cache")
        // a base map the reader does not take for one (not a PMTiles file): no places, no throw
        val base = File(tmp.root, "base.pmtiles").apply { writeBytes(ByteArray(300) { 7 }) }
        assertTrue(OfflineIndex.baseMapPlaces(base, cacheDir).isEmpty())
        val cache = cacheDir.listFiles()!!.single()
        val key = "${base.length()}:${base.lastModified()}"
        val line = OfflineIndex.cacheLine(com.onpath.app.search.BaseMapPlaces.Item("Meran", listOf("Merano"), "place", "town", 46.67, 11.16, 325))
        cache.writeText("$key\n$line\n")
        assertEquals("a good cache is used", listOf("Meran"), OfflineIndex.baseMapPlaces(base, cacheDir).map { it.name })
        for ((what, bytes) in broken("$key\n$line\n$line\n")) {
            cache.writeBytes(bytes)
            try { OfflineIndex.baseMapPlaces(base, cacheDir) } catch (t: Throwable) { throw AssertionError("place cache $what: reading threw", t) }
        }
        // the right key, then lines that are cut or garbage: those lines are left out, the good one stays
        cache.writeText("$key\n$line\n${line.take(line.length / 2)}\n\u0000\t\t\t\t\t\t\nplace\ttown\tnorth\teast\t\tX\t\n")
        assertEquals(listOf("Meran"), OfflineIndex.baseMapPlaces(base, cacheDir).map { it.name })
        // a base map cut off inside its header: no throw either (nothing found; the caller's search goes on)
        base.writeBytes("PMTiles".toByteArray()); cache.delete()
        val got = try { OfflineIndex.baseMapPlaces(base, cacheDir) } catch (e: Exception) { null }
        assertTrue("a cut base map gives no places (or an exception the index catches – OfflineIndex.safe)", got.isNullOrEmpty())
    }

    // --- overview pictures and thumbnails ---

    @Test fun brokenPictureKeysAreMadeAgain() {
        val pics = OverviewPictures(File(tmp.root, "overview"))
        assertNull(pics.meta("u1")); assertNull(pics.bytes("u1", "k")); assertNull(pics.size()); assertNull(pics.size(done = true))
        assertTrue(pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3)))
        pics.setSize(800, 600, 24)
        val keyFile = File(tmp.root, "overview/u1.key"); val sizeFile = File(tmp.root, "overview/size")
        assertEquals(Triple(800, 600, 24), pics.size())
        for ((what, bytes) in broken(keyFile.readText())) {
            keyFile.writeBytes(bytes); sizeFile.writeBytes(bytes)
            try {
                val m = pics.meta("u1")
                if (what != "cut in half") assertNull("overview key $what", m)
                assertNull("overview picture $what: not taken for the key", pics.bytes("u1", "k").takeIf { m == null })
                assertNull("overview size $what", pics.size())
            } catch (t: Throwable) { throw AssertionError("overview $what: reading threw", t) }
            assertTrue(pics.write("u1", OverviewPictures.Meta("k", 1f, 2f, 3f, 4f), byteArrayOf(1, 2, 3)))
            assertEquals("k", pics.meta("u1")!!.key)
            pics.setSize(800, 600, 24); assertEquals(Triple(800, 600, 24), pics.size())
        }
        keyFile.delete(); keyFile.mkdirs()
        assertNull("a folder in the key's place", pics.meta("u1"))

        val thumbs = RouteThumbs(File(tmp.root, "thumbs"))
        assertNull(thumbs.storedKey("u1"))
        assertTrue(thumbs.write("u1", "line-key", byteArrayOf(1)))
        val tk = File(tmp.root, "thumbs/u1.key")
        for ((what, bytes) in broken("line-key")) {
            tk.writeBytes(bytes)
            val k = try { thumbs.storedKey("u1") } catch (t: Throwable) { throw AssertionError("thumbnail $what: reading threw", t) }
            // any other text is only "another line's key": the picture is made again
            if (what != "cut after the first line") assertTrue("thumbnail $what", k != "line-key") // a one-line file: that cut is the whole key
            assertTrue(thumbs.write("u1", "line-key", byteArrayOf(1)))
            assertEquals("line-key", thumbs.storedKey("u1"))
        }
        tk.delete(); tk.mkdirs()
        assertNull("a folder in the key's place", thumbs.storedKey("u1"))
    }
}
