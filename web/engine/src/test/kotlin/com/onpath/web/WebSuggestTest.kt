package com.onpath.web

import com.onpath.app.suggest.Candidate
import com.onpath.app.suggest.Character
import com.onpath.app.suggest.Land
import com.onpath.app.suggest.SuggestSettings
import com.onpath.app.suggest.Wish
import com.onpath.strings.Txt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Round-trip suggestions through the web engine's entry (WebSuggest – `suggest` in WebApi): the two steps the page uses
 * (`need`, then `run`), the wish JSON, the cards' pills (sketch 7) and – where the routing data and the suggest files are
 * on this computer (E5_N50.rd5, the Hilden cells; skipped otherwise, as the app's NetReaderTest) – a real run of the
 * app's TourSuggester on them.
 */
class WebSuggestTest {
    private val repo = File("../..").canonicalFile
    private val root = Files.createTempDirectory("websuggest").toFile()
    private val home = System.getProperty("user.home")
    private val segments = File(System.getenv("ONPATH_SEGMENTS") ?: "$home/onpath-routing/segments")
    private val cells = File(System.getenv("SUGGEST_FILES") ?: "$home/onpath-suggest-poc/suggest-file/cells-c2")
    private val hilden = """{"lat":51.168,"lon":6.9395,"activity":"HIKE","sac":2,"hours":[2,3],"ascent":[null,400],"ziel":[]}"""

    @After fun tidy() { root.deleteRecursively(); Txt.setLanguage("en") }

    private fun withSettings() {
        Engine.init(root.path)
        File(root, "assets/suggest").mkdirs()
        repo.resolve("tools/suggest-poc/settings.json").copyTo(File(root, WebSuggest.SETTINGS), overwrite = true)
    }

    @Test fun withoutTheSettingsNothingIsAskedFor() {
        Engine.init(root.path)
        assertEquals("""{"parts":[],"cells":[],"settings":false}""", WebSuggest.call("need", hilden))
        assertTrue(WebSuggest.call("run", hilden).startsWith("{\"error\":"))
        assertTrue(WebSuggest.call("other", hilden).startsWith("{\"error\":"))
    }

    @Test fun needNamesTheRoutingPartsAndTheCellsInReach() {
        withSettings()
        val json = WebSuggest.call("need", hilden)
        assertTrue(json, json.contains("[\"E5_N50.rd5\","))
        val S = SuggestSettings.parse(File(root, WebSuggest.SETTINGS).readText())
        val w = WebSuggest.parseWish(S, hilden).wish
        val r = Character(w, S).radius(S)
        for (c in Land.cellsFor(6.9395, 51.168, r)) assertTrue("cell ${c[0]}/${c[1]} in $json", json.contains("[${c[0]},${c[1]}]"))
        assertTrue(json.endsWith("\"settings\":true}"))
    }

    @Test fun theWishReadsThePlannersActivityAndTheRanges() {
        withSettings()
        val S = SuggestSettings.parse(File(root, WebSuggest.SETTINGS).readText())
        val a = WebSuggest.parseWish(S, """{"lat":51.1,"lon":6.8,"activity":"BIKE_TREKKING","sac":3,"hours":[2.5,4],"ascent":null,"ziel":["wasser","gipfel","nix"]}""")
        assertEquals("gravel", a.wish.activity)
        assertEquals(2.5, a.wish.hours[0], 0.0); assertEquals(4.0, a.wish.hours[1], 0.0)
        assertEquals(null, a.wish.ascent)
        assertEquals(listOf("gipfel", "wasser"), a.wish.ziel) // the settings' order, unknown kinds left out
        val h = WebSuggest.parseWish(S, hilden)
        assertEquals("hike", h.wish.activity); assertEquals(2, h.wish.tLevel)
        assertEquals(400.0, h.wish.ascentHi!!, 0.0)
        assertEquals("mtb", WebSuggest.methodActivity(com.onpath.app.routing.Activity.EMTB))
        assertEquals("bike", WebSuggest.methodActivity(com.onpath.app.routing.Activity.EBIKE))
    }

    private fun cand(axes: DoubleArray, ascent: Int = 100, hours: Double = 2.5, road: Int = 0, town: Int = 0, cross: Int = 0,
                     ground: IntArray = intArrayOf(10, 20, 60, 10), track: Int = 0): Candidate {
        val c = Candidate(IntArray(0), IntArray(0), 10_000.0, ascent.toDouble(), hours, 0.0, 0.0, cross)
        axes.copyInto(c.axes)
        c.numbers = Candidate.Numbers(10.0, hours, ascent, 50, 20, town, 0, road, 0, 0.0, 0, 0.0, ground, 0, cross, 0, 0,
            emptyList(), emptyList(), emptyList(), 0.0, emptyList(), trackPct = track)
        return c
    }

    @Test fun pillsAsSketch7() {
        withSettings()
        Txt.setLanguage("de")
        val S = SuggestSettings.parse(File(root, WebSuggest.SETTINGS).readText())
        val ch = Character(Wish.of(S, 6.9, 51.1, "hike", 2, doubleArrayOf(2.0, 3.0), ascent = arrayOf(null, 400.0)), S)
        val best = cand(doubleArrayOf(0.95, 0.9, 0.9, 0.9, 1.0))
        val mid = cand(doubleArrayOf(0.7, 0.7, 0.7, 0.7, 1.0))
        val weak = cand(doubleArrayOf(0.4, 0.5, 0.5, 0.45, 1.0), ascent = 520, hours = 3.6, road = 450, town = 35, cross = 1,
            ground = intArrayOf(70, 10, 10, 10))
        val list = listOf(best, mid, weak)
        val pb = WebSuggest.pills(best, list, ch)
        assertEquals(2, pb.size) // only the two strongest strengths
        assertTrue(pb.all { it.cls.startsWith("g") })
        assertEquals("grün", pb[0].text) // the farthest above the mean first
        assertTrue(WebSuggest.pills(mid, list, ch).isEmpty()) // in the middle of the list: nothing stands out
        val pw = WebSuggest.pills(weak, list, ch)
        assertEquals(listOf("↑ 520 m – mehr als geplant", "36 min länger als geplant"), pw.map { it.text }) // a missed wish first
        assertTrue(pw.all { it.cls.startsWith("y") })
        assertEquals("y2", pw[0].cls) // 120 m over: clear (50 … 150)
        // alike on every axis: no pills at all
        val same = listOf(cand(doubleArrayOf(0.8, 0.8, 0.8, 0.8, 1.0)), cand(doubleArrayOf(0.82, 0.79, 0.8, 0.81, 1.0)))
        assertTrue(WebSuggest.pills(same[0], same, ch).isEmpty())
        // a weak Weg says why: here the asphalt
        val paved = cand(doubleArrayOf(0.8, 0.8, 0.3, 0.8, 1.0), ground = intArrayOf(80, 10, 5, 5))
        val ok = listOf(paved, cand(doubleArrayOf(0.8, 0.8, 0.9, 0.8, 1.0)), cand(doubleArrayOf(0.8, 0.8, 0.85, 0.8, 1.0)))
        assertEquals(listOf("viel Asphalt"), WebSuggest.pills(paved, ok, ch).map { it.text })
    }

    @Test fun aRealRunGivesCardsWithLoops() {
        assumeTrue("no E5_N50.rd5 in $segments", File(segments, "E5_N50.rd5").isFile)
        assumeTrue("no Hilden suggest files in $cells", File(cells, "10-531-341/suggest.bin").isFile)
        withSettings()
        Txt.setLanguage("de")
        File(root, "segments").mkdirs()
        Files.createSymbolicLink(File(root, "segments/E5_N50.rd5").toPath(), File(segments, "E5_N50.rd5").toPath())
        repo.resolve("app/src/main/assets/brouter").copyRecursively(File(root, "assets/brouter"))
        for (d in cells.listFiles()!!.filter { it.name.startsWith("10-") }) {
            File(root, "suggest/${d.name}").mkdirs()
            Files.createSymbolicLink(File(root, "suggest/${d.name}/suggest.bin").toPath(), File(d, "suggest.bin").toPath())
        }
        val json = WebSuggest.call("run", hilden)
        assertFalse(json, json.startsWith("{\"error\""))
        val cards = Regex("\\{\"id\":").findAll(json).count()
        assertTrue(json.take(400), cards in 1..5)
        assertTrue(json.contains("\"coords\":[[51."))
        assertTrue(json.contains("\"mapUpdating\":false"))
        for (m in Regex("\"pills\":\\[([^\\]]*)]").findAll(json)) {
            val p = m.groupValues[1]
            assertTrue(p, Regex("\"c\":\"g").findAll(p).count() <= 2 && Regex("\"c\":\"y").findAll(p).count() <= 2)
        }
    }
}
