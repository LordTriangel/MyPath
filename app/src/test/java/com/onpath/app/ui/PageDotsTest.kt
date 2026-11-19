package com.onpath.app.ui

import com.onpath.strings.Res
import com.onpath.strings.Txt
import com.onpath.strings.routes_overview_page_dots
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The route overview's page dots (owner 2026-11-19, „damit man sieht, dass man wischen kann“): a dot per page up to seven,
 * a window around the shown page beyond that with smaller edge dots where more follow; TalkBack reads „Seite 3 von 27“.
 */
class PageDotsTest {
    private val before = Locale.getDefault()
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)
    @After fun back() = Locale.setDefault(before)

    @Test fun aShortListGetsOneDotPerPage() {
        assertEquals(0 until 3, DotWindow.window(0, 3))
        assertEquals(0 until 7, DotWindow.window(6, 7))
        assertFalse((0 until 3).any { DotWindow.small(it, 1, 3) })
    }

    @Test fun aLongListShowsSevenAroundTheShownPage() {
        assertEquals(0 until 7, DotWindow.window(2, 27))
        assertEquals(7 until 14, DotWindow.window(10, 27))
        assertEquals(20 until 27, DotWindow.window(26, 27))
        (0 until 27).forEach { p -> assertTrue("page $p is in its window", p in DotWindow.window(p, 27)) }
    }

    @Test fun edgeDotsShrinkOnlyWhereMorePagesFollow() {
        // page 3 of 27: nothing before the window, more after it
        assertFalse(DotWindow.small(0, 2, 27))
        assertTrue(DotWindow.small(6, 2, 27))
        // in the middle: both edges small, the shown page never
        assertTrue(DotWindow.small(7, 10, 27))
        assertTrue(DotWindow.small(13, 10, 27))
        assertFalse(DotWindow.small(10, 10, 27))
        // the last page: only the left edge
        assertTrue(DotWindow.small(20, 26, 27))
        assertFalse(DotWindow.small(26, 26, 27))
    }

    @Test fun talkBackSaysThePage() {
        assertEquals("Page 3 of 27", Txt.s(Res.string.routes_overview_page_dots, 3, 27))
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Seite 3 von 27", Txt.s(Res.string.routes_overview_page_dots, 3, 27))
    }
}
