package com.onpath.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full pages fade in and out quickly (owner 2026-11-12, "ganz schnell, aber smooth"). */
class PageMotionTest {
    @Test fun quickFadeWithAnimationsOn() {
        assertEquals(PageMotion.ENTER_MS to PageMotion.EXIT_MS, PageMotion.durations(1f))
        assertTrue(PageMotion.ENTER_MS <= 200 && PageMotion.EXIT_MS <= PageMotion.ENTER_MS)
    }

    @Test fun instantWithAnimationsOff() {
        assertEquals(0 to 0, PageMotion.durations(0f))
        assertEquals(0 to 0, PageMotion.durations(Float.NaN))
    }
}
