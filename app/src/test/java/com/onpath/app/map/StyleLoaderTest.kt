package com.onpath.app.map

import com.onpath.app.debug.Problems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StyleLoaderTest {
    @Test fun classicUsesPlainFileName() = assertEquals("onpath.json", StyleLoader.fileName("classic"))
    @Test fun otherThemesUseSuffix() = assertEquals("onpath-high-contrast.json", StyleLoader.fileName("high-contrast"))
    @Test fun themeFromFileName() {
        assertEquals("classic", StyleLoader.themeOf("onpath.json"))
        assertEquals("high-contrast", StyleLoader.themeOf("onpath-high-contrast.json"))
        assertNull(StyleLoader.themeOf("notes.txt"))
        assertNull(StyleLoader.themeOf("onpath-.json"))
    }
    @Test fun labelIsReadable() = assertEquals("High contrast", StyleLoader.label("high-contrast"))

    // developer test style (tools/dev/push_style.sh): only with developer tools, only when the file is there
    @Test fun testStyleOnlyWithDeveloperTools() {
        val dir = Files.createTempDirectory("onpath-style").toFile()
        try {
            assertNull("no file", StyleLoader.devStyleFile(dir, devTools = true))
            assertNull("no status without a file", StyleLoader.devStyleStatus(dir, devTools = true))
            val f = File(dir, StyleLoader.DEV_STYLE).apply { parentFile!!.mkdirs(); writeText("""{"version":8}""") }
            assertEquals(f, StyleLoader.devStyleFile(dir, devTools = true))
            assertNull("never for normal users", StyleLoader.devStyleFile(dir, devTools = false))
            assertNull("no status for normal users", StyleLoader.devStyleStatus(dir, devTools = false))
            assertTrue(StyleLoader.devStyleStatus(dir, devTools = true)!!.contains(StyleLoader.DEV_STYLE))
            assertTrue(StyleLoader.removeDevStyle(dir))
            assertFalse(f.exists())
            assertNull(StyleLoader.devStyleFile(dir, devTools = true))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun testStyleRejectedByTheMapIsSkippedUntilANewPush() {
        val dir = Files.createTempDirectory("onpath-style").toFile()
        try {
            val f = File(dir, StyleLoader.DEV_STYLE).apply { parentFile!!.mkdirs(); writeText("""{"version":8}""") }
            // a normal style failing is no test style failure: the caller uses the fallback style
            assertFalse(StyleLoader.noteFailure("other style broken"))
            assertEquals(f, StyleLoader.devStyleFile(dir, devTools = true))
            // the map rejects the test style: reported as a warning, skipped, the caller loads the normal style
            Problems.clear()
            StyleLoader.noteTestStyleLoadedForTest(f)
            assertTrue(StyleLoader.devStyleStatus(dir, devTools = true)!!.startsWith("Test style active"))
            assertTrue(StyleLoader.noteFailure("layer xyz: bad expression"))
            assertTrue(Problems.snapshot().any { it.message.contains("rejected") })
            assertNull("skipped until a new push", StyleLoader.devStyleFile(dir, devTools = true))
            assertTrue(StyleLoader.devStyleStatus(dir, devTools = true)!!.contains("not used"))
            // a new push (other content) is tried again
            f.writeText("""{"version":8,"name":"next try"}""")
            assertEquals(f, StyleLoader.devStyleFile(dir, devTools = true))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun formatOneUpgradesGeoJsonToVectorPmtilesWhenPresent() {
        val style = """
            {"version":8,"metadata":{"onpath:format":1},"sources":{
              "base":{"type":"vector","url":"pmtiles://file:///d/base.pmtiles"},
              "paths":{"type":"geojson","data":"file:///d/paths.geojson","maxzoom":16},
              "routes":{"type":"geojson","data":"file:///d/routes.geojson","maxzoom":14},
              "trailnames":{"type":"geojson","data":"file:///d/trailnames.geojson","maxzoom":14},
              "pois":{"type":"geojson","data":"file:///d/pois.geojson","maxzoom":14}},
             "layers":[
              {"id":"roads","type":"line","source":"base","source-layer":"transportation"},
              {"id":"path-base","type":"line","source":"base","metadata":{"onpath:fallback-for":"paths"}},
              {"id":"paths-own","type":"line","source":"paths"},
              {"id":"routes-hike","type":"line","source":"routes"},
              {"id":"trail-name-t1","type":"symbol","source":"trailnames"},
              {"id":"poi-tier1","type":"symbol","source":"pois"}]}
        """.trimIndent()
        val present = setOf("/d/base.pmtiles", "/d/overlays_f1.pmtiles", "/d/pois.pmtiles", "/d/trailnames.geojson", "/d/pois.geojson")
        val out = org.json.JSONObject(OptionalSources.strip(
            style,
            sizeOf = { if (it in present) 1024L * 1024L else -1L },
            exists = { it in present },
        ))
        val sources = out.getJSONObject("sources")
        assertEquals("vector", sources.getJSONObject("paths").getString("type"))
        assertEquals("pmtiles://file:///d/overlays_f1.pmtiles", sources.getJSONObject("paths").getString("url"))
        assertEquals("pmtiles://file:///d/overlays_f1.pmtiles", sources.getJSONObject("routes").getString("url"))
        assertEquals("pmtiles://file:///d/overlays_f1.pmtiles", sources.getJSONObject("trailnames").getString("url"))
        assertEquals("pmtiles://file:///d/pois.pmtiles", sources.getJSONObject("pois").getString("url"))
        val layers = (0 until out.getJSONArray("layers").length()).associate {
            val l = out.getJSONArray("layers").getJSONObject(it)
            l.getString("id") to l
        }
        assertFalse("fallback hidden when paths active", "path-base" in layers)
        assertEquals("paths", layers.getValue("paths-own").getString("source-layer"))
        assertEquals("routes", layers.getValue("routes-hike").getString("source-layer"))
        assertEquals("trailnames", layers.getValue("trail-name-t1").getString("source-layer"))
        assertEquals("pois", layers.getValue("poi-tier1").getString("source-layer"))
    }

    @Test fun formatOneStripsOversizedOptionalGeoJsonAndThrottlesLargeOnes() {
        val style = """
            {"version":8,"metadata":{"onpath:format":1},"sources":{
              "base":{"type":"vector","url":"pmtiles://file:///d/base.pmtiles"},
              "paths":{"type":"geojson","data":"file:///d/paths.geojson","maxzoom":16,"tolerance":0.25},
              "routes":{"type":"geojson","data":"file:///d/routes.geojson","maxzoom":14,"tolerance":0.5,"buffer":32},
              "pois":{"type":"geojson","data":"file:///d/pois.geojson","maxzoom":14,"buffer":32}},
             "layers":[
              {"id":"path-base","type":"line","source":"base","metadata":{"onpath:fallback-for":"paths"}},
              {"id":"paths-own","type":"line","source":"paths"},
              {"id":"routes-hike","type":"line","source":"routes"},
              {"id":"poi-tier1","type":"symbol","source":"pois"}]}
        """.trimIndent()
        val sizes = mapOf(
            "/d/base.pmtiles" to 100_000_000L,
            "/d/paths.geojson" to 70_000_000L,
            "/d/routes.geojson" to 16_000_000L,
            "/d/pois.geojson" to 8_000_000L,
        )
        val out = org.json.JSONObject(OptionalSources.strip(
            style,
            sizeOf = { sizes[it] ?: -1L },
            exists = { it in sizes },
        ))
        val sources = out.getJSONObject("sources")
        assertFalse("oversized paths stripped", sources.has("paths"))
        assertEquals(11, sources.getJSONObject("routes").getInt("maxzoom"))
        assertEquals(4, sources.getJSONObject("routes").getInt("buffer"))
        assertTrue(sources.getJSONObject("routes").getDouble("tolerance") >= 1.2)
        assertEquals(11, sources.getJSONObject("pois").getInt("maxzoom"))
        val layerIds = (0 until out.getJSONArray("layers").length()).map { out.getJSONArray("layers").getJSONObject(it).getString("id") }
        assertEquals(listOf("path-base", "routes-hike", "poi-tier1"), layerIds)
    }
}
