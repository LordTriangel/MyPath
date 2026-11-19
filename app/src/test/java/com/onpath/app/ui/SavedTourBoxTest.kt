package com.onpath.app.ui

import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Owner 2026-11-06 (onpath.private#35): after "Tour speichern" the box "Tour gespeichert" shows the saved tour; nothing about
 * the saved tour in the plan sheet or the mode pill. (The box itself: TourEndFlowTest, ReleaseRundgang.flow07.)
 */
class SavedTourBoxTest {
    private fun src(f: String) = sourceFile(f).readText()

    @Test fun nothingInThePlanSheet() {
        assertFalse(src("ui/PlanPanel.kt").contains("savedTour"))
        assertFalse(src("ui/MapScreen.kt").contains("savedTour"))
        assertFalse(src("ui/AppViewModel.kt").contains("movedOn"))
    }
}
