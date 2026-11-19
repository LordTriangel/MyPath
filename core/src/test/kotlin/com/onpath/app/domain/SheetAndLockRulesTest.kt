package com.onpath.app.domain

import com.onpath.app.domain.tour.UnlockWindow
import com.onpath.app.domain.tour.UnlockWindow.BarButton as B
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour bar's unlock window and the plan sheet's settling (converted from UnlockWindowTest and SheetDragTest –
 * test-audit G11): one hold unlocks 10 s, where the dragged sheet settles.
 */
class SheetAndLockRulesTest {
    // ---- from UnlockWindowTest.kt ----
    // One hold unlocks the bar for 10 s (owner 2026-11-07 09:05; 10 s since 2026-11-08) – [UnlockWindow].
    private val t0 = 1_000_000L

    @Test fun shutAtTheStartAndATapNeverUnlocks() {
        assertTrue(UnlockWindow.locked(lockOn = true, until = UnlockWindow.LOCKED, now = t0))
        assertEquals("a tap on a shut bar keeps it shut", UnlockWindow.LOCKED, UnlockWindow.afterTap(UnlockWindow.LOCKED, t0))
    }

    @Test fun oneHoldOpensTheBarForTenSeconds() {
        val until = UnlockWindow.afterFire(t0)
        assertEquals(t0 + 10_000, until)
        assertFalse("right after the hold: plain taps", UnlockWindow.locked(true, until, t0 + 1))
        assertFalse("9.9 s later still open", UnlockWindow.locked(true, until, t0 + 9_999))
        assertTrue("10 s idle: locked again", UnlockWindow.locked(true, until, t0 + 10_000))
    }

    @Test fun everyTapStartsTheWindowAgain() {
        var until = UnlockWindow.afterFire(t0)
        until = UnlockWindow.afterTap(until, t0 + 7_000) // Planen after 7 s
        assertFalse("open 15 s after the hold", UnlockWindow.locked(true, until, t0 + 15_000))
        until = UnlockWindow.afterTap(until, t0 + 16_000) // Beenden 9 s later
        assertFalse(UnlockWindow.locked(true, until, t0 + 25_999))
        assertTrue("then 10 s idle: shut", UnlockWindow.locked(true, until, t0 + 26_000))
        assertEquals("a tap after it shut does not open it", until, UnlockWindow.afterTap(until, t0 + 30_000))
    }

    @Test fun screenOffOrPocketShutsItAtOnce() {
        // owner 2026-11-07 10:45: "Pause drücken, dann in die Tasche – danach soll wieder gesperrt sein"
        val until = UnlockWindow.afterFire(t0)                // held Pause: open for 10 s
        assertFalse(UnlockWindow.locked(true, until, t0 + 1_000))
        val shut = UnlockWindow.afterScreenOff()              // 2 s later into the pocket / power button / background
        assertTrue("locked although 8 s of the window were left", UnlockWindow.locked(true, shut, t0 + 2_000))
        assertTrue("every button held again", UnlockWindow.needsHold(B.END, lockOn = true,
            windowOpen = UnlockWindow.isOpen(shut, t0 + 2_000)))
        assertEquals("a tap after it does not open it again", shut, UnlockWindow.afterTap(shut, t0 + 3_000))
    }

    @Test fun holdTimeAusHasNothingToUnlock() {
        assertFalse("Aus: never held buttons", UnlockWindow.locked(lockOn = false, until = UnlockWindow.LOCKED, now = t0))
    }

    @Test fun oneRuleEverywhereHeldWhileShutATapWhileOpen() {
        // owner 2026-11-08: pale = hold, strong = tap – Weiter too
        for (b in B.entries) assertTrue("$b held while shut", UnlockWindow.needsHold(b, lockOn = true, windowOpen = false))
        for (b in B.entries) assertFalse("$b on a tap while open", UnlockWindow.needsHold(b, lockOn = true, windowOpen = true))
        // Aus: nothing is held
        for (b in B.entries) for (open in listOf(false, true))
            assertFalse("$b with the lock off", UnlockWindow.needsHold(b, lockOn = false, windowOpen = open))
    }

    @Test fun aTapOnWeiterOutsideTheWindowDoesNothingInsideItResumesAndLocks() {
        // outside: Weiter wants a hold, and a tap does not open the window
        assertTrue(UnlockWindow.needsHold(B.RESUME, lockOn = true, windowOpen = UnlockWindow.isOpen(UnlockWindow.LOCKED, t0)))
        assertEquals(UnlockWindow.LOCKED, UnlockWindow.afterTap(UnlockWindow.LOCKED, t0))
        // inside (a held Anhalten 3 s ago): a tap resumes – and the moving bar is locked at once
        val until = UnlockWindow.afterFire(t0)
        assertFalse(UnlockWindow.needsHold(B.RESUME, lockOn = true, windowOpen = UnlockWindow.isOpen(until, t0 + 3_000)))
        assertTrue("locked right after Weiter", UnlockWindow.locked(true, UnlockWindow.afterResume(), t0 + 3_000))
    }

    @Test fun testsCanShortenTheWindow() {
        try {
            UnlockWindow.openMsForTests = 1_500
            assertEquals(t0 + 1_500, UnlockWindow.afterFire(t0))
        } finally { UnlockWindow.openMsForTests = null }
        assertEquals(t0 + UnlockWindow.OPEN_MS, UnlockWindow.afterFire(t0))
    }

    // ---- from SheetDragTest.kt ----
    // Where the dragged plan sheet settles (three steps, no jumping).
    // small 200, profile 500, points 900
    private val steps = listOf(200f, 500f, 900f)
    private fun target(shown: Float, v: Float = 0f) = SheetDrag.target(shown, steps, v, flingPxS = 1000f)

    @Test fun smallMovementGoesBack() {
        assertEquals(1, target(450f))
        assertEquals(1, target(560f))
    }

    @Test fun settlesAtTheNearestStep() {
        assertEquals(0, target(330f))
        assertEquals(2, target(720f))
        assertEquals(2, target(1000f))  // rubber band above the top step
        assertEquals(0, target(120f))
    }

    /** A long pull goes past a step: from the small sheet straight to the points. */
    @Test fun aLongPullSkipsAStep() = assertEquals(2, target(850f))

    @Test fun aFlingGoesToTheNextStepInItsDirection() {
        assertEquals(2, target(510f, v = -2000f))
        assertEquals(0, target(490f, v = 2000f))
        assertEquals(1, target(300f, v = -2000f))
        assertEquals(1, target(800f, v = 2000f))
    }

    @Test fun aFlingBeyondTheEndsStaysAtTheEnd() {
        assertEquals(2, target(950f, v = -2000f))
        assertEquals(0, target(150f, v = 2000f))
    }

    @Test fun aSlowReleaseIsNoFling() = assertEquals(1, target(520f, v = -600f))

    @Test fun theReleaseLineNamesPositionStepsSpeedAndTarget() = assertEquals(
        "Sheet let go at 1143 px (steps 536 / 938 / 1602 px, -12 px/s) → step 1",
        SheetDrag.describe(1143.4f, listOf(536f, 938.2f, 1602f), -12.3f, 1))

    @Test fun followsTheFingerWithRubberBandAtTheEnds() {
        assertEquals(400f, SheetDrag.follow(400f, 100f, 800f), 0f)
        assertEquals(830f, SheetDrag.follow(900f, 100f, 800f), 1e-3f)
        assertEquals(70f, SheetDrag.follow(0f, 100f, 800f), 1e-3f)
    }
}
