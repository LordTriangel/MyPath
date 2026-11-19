package com.onpath.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner 2026-10-31 (docs/design/ui-style.md): every box (FRAGE, HINWEIS) sits 12 dp under the top element – Plan under
 * the search pill + ☰ row, Navigate under the turn / status column (the re-route question too), My routes under the
 * tabs, Settings under the title. One rule, [MessagePlacement].
 */
class MessagePlacementTest {
    @Test fun twelveDpUnderTheTopElement() {
        assertEquals(12, MessagePlacement.GAP_DP)
        assertEquals(TOP_BAR_BOTTOM.value.toInt() + 12, MessagePlacement.topDp(MessageScreen.PLAN))
        assertEquals(MessagePlacement.ROUTES_HEADER_DP + 12, MessagePlacement.topDp(MessageScreen.ROUTES))
        assertEquals(MessagePlacement.SETTINGS_HEADER_DP + 12, MessagePlacement.topDp(MessageScreen.SETTINGS))
        for (h in listOf(0, 8, 120, 260, 600)) assertEquals(h + 12, MessagePlacement.topDp(MessageScreen.NAVIGATE, h))
        // not measured yet: still below the status bar, never above it
        assertEquals(12, MessagePlacement.topDp(MessageScreen.NAVIGATE, -5))
        // the small download chip stays right under ☰, above where a box goes
        assertTrue(MessagePlacement.CHIP_TOP_DP < MessagePlacement.topDp(MessageScreen.PLAN))
    }

    /** Every box's buttons are big enough to hit with a glove. */
    @Test fun boxButtonsAreBigEnough() {
        assertTrue(MESSAGE_BUTTON_DP >= 48)
        assertTrue(NAVIGATE_BUTTON_DP >= 56)
    }
}
