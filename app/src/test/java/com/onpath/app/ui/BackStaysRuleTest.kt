package com.onpath.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Flow map N4 (owner 2026-11-15 after a hike: „aus der Aufzeichnung verlassen wir die App nicht“): Android's back in
 * Navigate while a tour records never leaves the app and asks nothing – it closes what is open first, then does nothing.
 * Without a recording back leaves as in every app.
 */
class BackStaysRuleTest {
    @Test fun swallowedOnlyWhileRecordingAndNothingElseClosesFirst() {
        assertTrue(BackStaysRule.swallows(recording = true, barOpen = false, sheetOpen = false))
        assertFalse("no tour: back leaves as in every app", BackStaysRule.swallows(recording = false, barOpen = false, sheetOpen = false))
        assertFalse("the unlocked bar closes first", BackStaysRule.swallows(recording = true, barOpen = true, sheetOpen = false))
        assertFalse("the activity / settings sheet closes first", BackStaysRule.swallows(recording = true, barOpen = false, sheetOpen = true))
    }

    @Test fun navigateNeverSendsTheAppAwayAndAsksNothing() {
        val nav = File("src/main/java/com/onpath/app/ui/NavigateScreen.kt").readText()
        assertFalse("no moveTaskToBack in Navigate", nav.contains("moveTaskToBack"))
        assertFalse("no 'App verlassen?' question", nav.contains("nav_leave"))
        assertTrue("the swallowed back is logged (Pixel log)", nav.contains("Back in Navigate while recording: stays in the app"))
        for (dir in listOf("values", "values-de")) {
            val xml = File("../strings/src/commonMain/composeResources/$dir/strings_navigate.xml").readText()
            assertFalse("$dir: the question's texts are gone", xml.contains("name=\"nav_leave_"))
        }
    }
}
