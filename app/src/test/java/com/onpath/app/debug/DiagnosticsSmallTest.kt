package com.onpath.app.debug

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostics rules of the app, small (converted from DiagnosticsTest, FrameStatsTest, HealthLineTest, MapFpsTrialTest –
 * test-audit G17): pmtiles header reader, fps-trial parse, frame stats, health line. They guard diagnostics, not features.
 * The :core rules (stall classification, jank tally, MapLibre log level, screen lifecycle) are in core's DiagnosticsRulesTest.
 */
class DiagnosticsSmallTest {
    // ---- from DiagnosticsTest.kt ----
    @Test fun readsPmtilesHeader() {
        val h = Diagnostics.pmtilesHeader(File("src/test/resources/search/mini.pmtiles"))
        assertEquals(3, h.getInt("version"))
        assertTrue(h.getInt("maxZoom") >= h.getInt("minZoom"))
        assertTrue(h.getBoolean("complete"))
        assertEquals(4, h.getJSONArray("bounds").length())
    }

    @Test fun notAPmtilesFile() {
        val f = File.createTempFile("notpm", ".pmtiles").apply { writeBytes(ByteArray(200)) }
        assertTrue(Diagnostics.pmtilesHeader(f).has("error"))
    }

    // ---- from MapFpsTrialTest.kt ----
    @Test fun aNumberInRangeIsTheLimit() {
        assertEquals(60, MapFpsTrial.parse("60\n"))
        assertEquals(120, MapFpsTrial.parse(" 120 "))
    }

    @Test fun nothingUsableMeansNoLimit() {
        assertNull(MapFpsTrial.parse(null))
        assertNull(MapFpsTrial.parse(""))
        assertNull(MapFpsTrial.parse("fast"))
        assertNull(MapFpsTrial.parse("0"))
        assertNull(MapFpsTrial.parse("1000"))
    }

    // ---- from FrameStatsTest.kt ----
    // The "Sheet drag: …" numbers the owner's swipe writes into the events trace (FrameJankMeter).
    @Test fun noFramesNoLine() = assertNull(FrameStats.of(emptyList()))

    @Test fun countsJankyFramesAndPercentile() {
        // 20 frames: 17 smooth (5 ms), two over 16 ms, one over 33 ms
        val ms = List(17) { 5.0 } + listOf(17.0, 20.0, 40.0)
        val s = FrameStats.of(ms, refreshHz = 120f)!!
        assertEquals(20, s.frames)
        assertEquals(3, s.over16)
        assertEquals(1, s.over33)
        assertEquals(3, s.overBudget)  // 8.3 ms budget at 120 Hz
        assertEquals(20.0, s.p95Ms, 0.0)  // nearest rank: 19th of 20
        assertEquals(40.0, s.maxMs, 0.0)
        assertEquals("Sheet drag: 20 frames, 3 janky > 16 ms, 1 > 33 ms (p95 20.0 ms, max 40.0 ms; 120 Hz: 3 over 8.3 ms)",
            s.line("Sheet drag"))
    }

    @Test fun budgetFollowsTheDisplay() {
        val s = FrameStats.of(listOf(10.0, 12.0), refreshHz = 60f)!!
        assertEquals(0, s.overBudget)
        assertEquals(60, s.hz)
    }

    // ---- from HealthLineTest.kt ----
    // The 10-minute health line in the event log (Health.line).
    @Test fun fullLine() {
        val l = Health.line(Health.Sample(
            pssKb = 2_200_000, nativePssKb = 1_570_000, javaPssKb = 100_000, graphicsKb = 210_000,
            nativeHeapBytes = 1_580_000_000, cpuMs = 204_000, wallMs = 600_000, frames = 12_000, jankyFrames = 576,
            storeChanges = 37, cells = 37, cellQueue = 20, routingQueue = 3,
            swapKb = 420_000, javaUsedBytes = 96_000_000, mapGeoJsonBytes = 23_400_000, searchEntries = 167_202,
            styleLoads = 2, styleData = "g000093",
        ))
        assertEquals("Health: PSS 2.10 GB (swap 0.40; native 1.50, java 0.10, gfx 0.20), native heap 1.47 GB allocated, " +
            "java used 0.09 GB, map GeoJSON 22 MB, search 167202 entries, style loads 2 (g000093), CPU 0.34 cores " +
            "(204 s in 10 min), janky 4.8 % of 12000 frames, store changes 37 / 37 cells, queue 20 regions + 3 routing parts", l)
    }

    @Test fun unknownsAndNoFrames() {
        val l = Health.line(Health.Sample(null, null, null, null, 0, 0, 0, 0, 0, null, null, null, null))
        assertTrue(l, l.startsWith("Health: PSS ? (swap ?; native ?, java ?, gfx ?)"))
        assertTrue(l, l.contains("map GeoJSON ?, search not built, style loads 0 (–)"))
        assertTrue(l, l.contains("no frames drawn"))
        assertTrue(l, l.contains("queue 0 regions + 0 routing parts"))
    }
}
