package com.onpath.app.ui.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The round-trip card's whole text (owner 2026-11-19, V1 „die Karte wächst“): a tap on the text grows the card, „▴
 * weniger“ folds it back, a swipe to another card folds it; one card open at a time. (The tap → grown card on the
 * screen: RoundTripSuggestE2ETest.)
 */
class OpenCardTest {
    @Test fun aTapOnTheTextOpensTheCardWenigerClosesIt() {
        val c = OpenCard()
        assertEquals("none open at first", -1, c.open)
        c.tapText(0)
        assertEquals("the tapped card grows", 0, c.open)
        c.less()
        assertEquals("„weniger“ folds it back", -1, c.open)
    }

    @Test fun aSwipeToAnotherCardClosesIt() {
        val c = OpenCard()
        c.tapText(0)
        c.settled(0)
        assertEquals("settling on the open card keeps it open", 0, c.open)
        c.settled(1)
        assertEquals("the row settled on the next card: folded", -1, c.open)
    }

    @Test fun onlyOneCardOpenAtATime() {
        val c = OpenCard()
        c.tapText(0)
        c.tapText(2) // the text of the card beside: it comes to the front and grows
        assertEquals(2, c.open)
        c.settled(2)
        assertEquals("the row settles on it: it stays open", 2, c.open)
    }

    @Test fun aScreenshotMayStartOpen() {
        assertEquals(1, OpenCard(start = 1).open)
    }
}
