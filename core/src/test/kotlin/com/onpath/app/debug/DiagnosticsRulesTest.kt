package com.onpath.app.debug

import com.onpath.app.debug.MainStall.Kind as StallKind
import com.onpath.app.debug.MapLibreLogLevel.Priority
import com.onpath.app.debug.Problems.Level
import com.onpath.app.map.ReliefAuto.Gesture
import com.onpath.app.map.ReliefAuto.Kind
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostics rules of :core (converted from MainStallTest, MapJankTallyTest, MapLibreLogLevelTest, ScreenLifecycleTest –
 * test-audit G17): stall classification, jank tally, an old ERROR entry is not announced again, the event log names each
 * screen. They guard diagnostics, not features.
 */
class DiagnosticsRulesTest {
    // ---- from MainStallTest.kt ----
    // Main-thread stall classification and wording (MainStall; Pixel 2026-11-07: 11 of 13 stalls were first frames).
    private fun f(cls: String, method: String, line: Int = 10) = StackTraceElement(cls, method, "${cls.substringAfterLast('.')}.kt", line)

    /** Stall classification: right after screen creation = first-frame INFO, later / unknown = WARN, leaving wins over first frame. */
    @Test fun stallKindAndLevel() {
        for (ms in listOf(0L, 1_200L, 3_000L)) assertEquals("$ms ms after creation", StallKind.FIRST_FRAME, MainStall.kind(sinceCreatedMs = ms, leaving = false))
        assertEquals(Problems.Level.INFO, MainStall.level(StallKind.FIRST_FRAME))
        for (ms in listOf(3_001L, 600_000L)) assertEquals("$ms ms: later stall", StallKind.OTHER, MainStall.kind(sinceCreatedMs = ms, leaving = false))
        assertEquals(Problems.Level.WARN, MainStall.level(StallKind.OTHER))
        assertEquals("unknown creation time", StallKind.OTHER, MainStall.kind(sinceCreatedMs = null, leaving = false))
        assertEquals("crossed creation time", StallKind.OTHER, MainStall.kind(sinceCreatedMs = -5, leaving = false))
        assertEquals(StallKind.LEAVING, MainStall.kind(sinceCreatedMs = 600_000, leaving = true))
        assertEquals("leaving wins over first frame", StallKind.LEAVING, MainStall.kind(sinceCreatedMs = 500, leaving = true))
        assertEquals(StallKind.LEAVING, MainStall.kind(sinceCreatedMs = null, leaving = true))
        assertEquals(Problems.Level.WARN, MainStall.level(StallKind.LEAVING))
    }

    @Test fun stallMessageWordingPerKindEarlySampleAndUnreadableStack() {
        val st = listOf(f("android.graphics.Matrix", "translate", 300), f("com.onpath.app.map.MapScreen", "draw", 42))
        assertEquals("Main thread blocked at the first frame after the screen was created (in: Matrix.translate:300 ← MapScreen.draw:42)",
            MainStall.message(StallKind.FIRST_FRAME, st, st))
        assertEquals("Main thread blocked while leaving the app (in: Matrix.translate:300 ← MapScreen.draw:42)",
            MainStall.message(StallKind.LEAVING, st, st))
        assertEquals("Main thread blocked (in: Matrix.translate:300 ← MapScreen.draw:42)",
            MainStall.message(StallKind.OTHER, st, st))
        val early = listOf(f("org.maplibre.android.maps.Style", "load", 88), f("com.onpath.app.map.MapStyle", "apply", 12))
        val last = listOf(f("android.graphics.Matrix", "translate", 300))
        assertEquals("early sample named only when different", "Main thread blocked (in: Matrix.translate:300; at 250 ms in: Style.load:88 ← MapStyle.apply:12)",
            MainStall.message(StallKind.OTHER, early, last))
        assertEquals("Main thread blocked (in: Matrix.translate:300)", MainStall.message(StallKind.OTHER, emptyList(), last))
        assertEquals("unreadable stack", "Main thread blocked (in: ?)", MainStall.message(StallKind.OTHER, emptyList(), emptyList()))
    }


    @Test fun framesKeepFrameworkAboveTheAppFrame() {
        val st = listOf(
            f("android.graphics.Matrix", "translate", 1), f("android.view.View", "draw", 2),
            f("com.onpath.app.ui.Track", "paint", 3), f("com.onpath.app.ui.Map", "render", 4),
            f("android.view.ViewGroup", "dispatchDraw", 5), f("android.os.Looper", "loop", 6),
        )
        assertEquals(listOf("Matrix.translate:1", "View.draw:2", "Track.paint:3", "Map.render:4", "ViewGroup.dispatchDraw:5"),
            MainStall.frames(st))
    }

    @Test fun deepAppFrameKeepsTopAndItself() {
        val st = (1..8).map { f("android.view.Frame$it", "m", it) } + f("com.onpath.app.MainActivity", "onStop", 99)
        assertEquals(listOf("Frame1.m:1", "Frame2.m:2", "Frame3.m:3", "…", "MainActivity.onStop:99"), MainStall.frames(st))
    }

    @Test fun noAppFrameTakesTheTop() {
        val st = (1..8).map { f("android.view.Frame$it", "m", it) }
        assertEquals(listOf("Frame1.m:1", "Frame2.m:2", "Frame3.m:3", "Frame4.m:4", "Frame5.m:5"), MainStall.frames(st))
        assertEquals(listOf("Frame1.m:1"), MainStall.frames(st, max = 1))
    }

    @Test fun eventLogLineHasTimeKindAndTwelveFrames() {
        val st = (1..15).map { f("android.view.Frame$it", "m$it", it) }
        val early = listOf(f("com.onpath.app.data.Prefs", "save", 7))
        assertEquals("Main thread blocked 812 ms while leaving the app – stack: " +
            (1..12).joinToString(" ← ") { "Frame$it.m$it" } + "; at 250 ms in: Prefs.save:7",
            MainStall.traceLine(StallKind.LEAVING, 812, early, st))
        assertEquals("Main thread blocked 640 ms at the first frame after the screen was created – stack: ?",
            MainStall.traceLine(StallKind.FIRST_FRAME, 640, emptyList(), emptyList()))
        assertEquals("Main thread blocked 700 ms – stack: Frame1.m1 ← Frame2.m2",
            MainStall.traceLine(StallKind.OTHER, 700, emptyList(), st.take(2)))
    }

    @Test fun nativeFrameWithoutLine() {
        assertEquals("MessageQueue.nativePollOnce", MainStall.short(StackTraceElement("android.os.MessageQueue", "nativePollOnce", null, -2)))
    }

    // ---- from MapJankTallyTest.kt ----
    // The problem report's "mapJank": janky share per gesture kind and relief state; the recent gestures for ReliefAuto.
    @Test fun sumsPerKindAndReliefState() {
        val t = MapJankTally()
        t.add(Gesture(Kind.PAN, 100, 46, true))
        t.add(Gesture(Kind.PAN, 100, 50, true))
        t.add(Gesture(Kind.ZOOM, 80, 10, true))
        t.add(Gesture(Kind.PAN, 50, 4, false))
        val j = t.json()
        assertEquals(3, j.length())
        val pan = j.getJSONObject("pan, relief on")
        assertEquals(2, pan.getInt("gestures"))
        assertEquals(200, pan.getInt("frames"))
        assertEquals(96, pan.getInt("janky"))
        assertEquals(48, pan.getInt("jankyPercent"))
        assertEquals(13, j.getJSONObject("zoom, relief on").getInt("jankyPercent"))
        assertEquals(8, j.getJSONObject("pan, relief off").getInt("jankyPercent"))
    }

    @Test fun keepsTheRecentGesturesOldestFirst() {
        val t = MapJankTally()
        repeat(40) { t.add(Gesture(Kind.PAN, it, 0, true)) }
        val r = t.recent()
        assertEquals(30, r.size)
        assertEquals(10, r.first().frames)
        assertEquals(39, r.last().frames)
    }

    @Test fun emptyWithoutGestures() = assertEquals(0, MapJankTally().json().length())

    // ---- from MapLibreLogLevelTest.kt ----
    private fun lvl(p: Priority, msg: String, t: Boolean = false) = MapLibreLogLevel.of(p, "Mbgl", msg, t)

    @Test fun harmlessNotesAreInfo() {
        // the note the owner saw on the map (logged by MapLibre via Logger.e)
        assertEquals(Level.INFO, lvl(Priority.ERROR, "LocationComponent#paddingWhileTracking method call is ignored " +
            "because the camera mode is transitioning"))
        assertEquals(Level.INFO, lvl(Priority.ERROR, "LocationComponent#zoomWhileTracking method can only be used " +
            "when a camera mode other than CameraMode#NONE is engaged."))
        assertEquals(Level.INFO, lvl(Priority.ERROR, "Layer planned-route already exists"))
        assertEquals(Level.INFO, lvl(Priority.WARN, "{Worker}[Resource]: Request canceled"))
        assertEquals(Level.INFO, lvl(Priority.ERROR, "Unable to resolve host \"example.org\": no network"))
        assertEquals(Level.INFO, lvl(Priority.ERROR, "Failed to load tile: Canceled", t = true))
    }

    @Test fun otherWarningsAreWarn() {
        assertEquals(Level.WARN, lvl(Priority.WARN, "Something unusual in the style"))
        assertEquals(Level.WARN, lvl(Priority.WARN, "Failed to load style", t = true)) // a warning is never announced
    }

    @Test fun nonBreakingErrorsAreWarn() {
        assertEquals(Level.WARN, lvl(Priority.ERROR, "{Worker}[Style]: Invalid geometry in line layer"))
    }

    @Test fun breakingErrorsAreError() {
        assertEquals(Level.ERROR, lvl(Priority.ERROR, "[ParseStyle]: Failed to parse style: expected value"))
        assertEquals(Level.ERROR, lvl(Priority.ERROR, "Failed to load style: file not readable"))
        assertEquals(Level.ERROR, lvl(Priority.ERROR, "Unable to open database file onpath/base.pmtiles"))
        assertEquals(Level.ERROR, lvl(Priority.ERROR, "Glyph range 8192-8447 not found"))
        assertEquals(Level.ERROR, lvl(Priority.ERROR, "Native exception in map view", t = true))
    }

    /** An entry stored as ERROR by an older version, now reported as INFO, is not announced again in a new run. */
    @Test fun storedErrorReportedAsInfoIsNotAnnounced() {
        val dir = Files.createTempDirectory("mlll").toFile()
        Problems.init(dir)
        Problems.clear()
        val msg = "MapLibre (Mbgl-LocationComponent): paddingWhileTracking ignored"
        File(dir, "problems.json").writeText(
            """{"problems":[{"level":"ERROR","area":"map","message":"$msg","first":1,"last":1,"count":1}]}""")
        Problems.init(dir) // new run: nothing announced yet
        Problems.info("map", msg)
        assertNull(Problems.newSerious.value)
        assertEquals(2, Problems.snapshot().single().count) // still recorded – nothing silent
        Problems.clear()
    }

    // ---- from ScreenLifecycleTest.kt ----
    // Two MainActivity instances at a cold start: the event log must name each screen and why it went.
    @Test fun secondScreenWhileFirstAliveIsCounted() {
        val (a, before) = ScreenLifecycle.created()
        val (b, othersAtB) = ScreenLifecycle.created()
        assertEquals(before + 1, othersAtB)
        assertEquals(before + 1, ScreenLifecycle.destroyed(a))
        assertEquals(before, ScreenLifecycle.destroyed(b))
        val line = ScreenLifecycle.createdLine(b, 1, 42, root = true, restored = false, intent = "MAIN")
        assertTrue(line, line.contains("1 other screen(s) still alive"))
        assertFalse(ScreenLifecycle.createdLine(a, 0, 42, true, false, "MAIN").contains("alive"))
    }

    @Test fun destroyReasonIsNamed() {
        assertEquals("Screen #1 destroyed (configuration change)", ScreenLifecycle.destroyedLine(1, finishing = false, configChange = true, others = 0))
        assertEquals("Screen #1 destroyed (finished), 1 other screen(s) alive", ScreenLifecycle.destroyedLine(1, true, false, 1))
        assertEquals("Screen #2 destroyed (by the system)", ScreenLifecycle.destroyedLine(2, false, false, 0))
    }

    /** Field 2026-11-06/07 (Pixel, after each install): a launcher start created as a non-root screen beside the task's root. */
    @Test fun aNonRootLauncherStartStandsDown() {
        val launcher = setOf("android.intent.category.LAUNCHER")
        assertTrue(ScreenLifecycle.standsDown(taskRoot = false, "android.intent.action.MAIN", launcher))
        // the task root is the one screen – it never stands down
        assertFalse(ScreenLifecycle.standsDown(taskRoot = true, "android.intent.action.MAIN", launcher))
        // a start that carries something (a link, a GPX, a share) is never dropped
        assertFalse(ScreenLifecycle.standsDown(false, "android.intent.action.VIEW", null))
        assertFalse(ScreenLifecycle.standsDown(false, "android.intent.action.SEND", null))
        assertFalse(ScreenLifecycle.standsDown(false, "android.intent.action.MAIN", null))
        assertFalse(ScreenLifecycle.standsDown(false, null, launcher))
    }

    @Test fun intentTextShowsLaunchDetailsButNoContent() {
        val t = ScreenLifecycle.intentText("android.intent.action.MAIN", null, null,
            setOf("android.intent.category.LAUNCHER"), 0x10200000, fromHistory = false, sender = "com.android.shell")
        assertEquals("MAIN [LAUNCHER] flags 0x10200000 from com.android.shell", t)
        val v = ScreenLifecycle.intentText("android.intent.action.VIEW", "content", "text/xml", null, 0x100000, true, null)
        assertEquals("VIEW content: text/xml flags 0x100000 from recents", v)
    }
}
