package com.onpath.app.ui

import com.onpath.app.domain.tour.TourEndFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There are three ways to end a tour – End tour, "You have arrived" → Save and the "Interrupted tour" question after a
 * restart. All three show the bus / car / lift summary when a ride was found (core feature C12). The dialogs' buttons
 * (Continue · Delete · Save, the bin note) are in the screenshot frames `unfinishedTour`; the flows in the E2E tests
 * (arrival: Rundgang flow 2, end sheet: flow 7, CoreFieldE2ETest endTour…).
 * The ride check of the three ways (TourEndFlow.afterRideCheck / unfinishedSave: rides → the summary, none → the plain sheet or saved at
 * once) is proved on the real slice since the test clean-up G05 (2026-11-19): TourEndFlowTest.endTourWithARideShowsTheSummaryAfterTheCheckOnIo,
 * .endTourWithoutARideAsksPlainOrSavesAtOnceAsAskedFor (End tour and the arrival box's "Tour beenden"),
 * .askSaveOfAnInterruptedTourWithARideShowsTheSummaryAndWritesNothing / …WithoutARideSavesAtOnce (the "Interrupted tour" question).
 */
class TourEndPathsTest {
    /** Field bug 2026-10-31: after a reboot the map behind "Interrupted tour" was empty – no service, no track. */
    @Test fun anInterruptedTourIsDrawnBehindItsQuestion() {
        val rec = listOf("running")
        val old = listOf("interrupted")
        assertEquals("the running recording wins", rec, TourEndFlow.trackShown(rec, unfinishedOpen = true, unfinishedTrack = old))
        assertEquals("no service after a reboot: the interrupted tour's track",
            old, TourEndFlow.trackShown(null, unfinishedOpen = true, unfinishedTrack = old))
        assertTrue("question answered: nothing left over", TourEndFlow.trackShown(null, unfinishedOpen = false, unfinishedTrack = old).isEmpty())
        assertTrue(TourEndFlow.trackShown<String>(null, unfinishedOpen = true, unfinishedTrack = null).isEmpty())
    }

    /** An arrival box from Navigate waits for Navigate – Plan shows it not (owner 2026-11-03: Plan is silent). */
    @Test fun theArrivalBoxShowsOnlyInNavigateWhileRecording() {
        assertTrue(TourEndFlow.arrivalBoxShows(asked = true, recording = true, navigate = true))
        assertFalse("Plan: it waits", TourEndFlow.arrivalBoxShows(asked = true, recording = true, navigate = false))
        assertFalse("the tour ended meanwhile", TourEndFlow.arrivalBoxShows(asked = true, recording = false, navigate = true))
        assertFalse(TourEndFlow.arrivalBoxShows(asked = false, recording = true, navigate = true))
    }

    // Deleting an interrupted tour marks it "done" FIRST (restored from the recycle bin it is a normal tour, never "unfinished" again):
    // a behaviour flow since piece 17 – TourEndFlowTest.deleteMarksTheTourDoneThenBinsItThenTellsTheBackupAndTheList (the bin write
    // sees the row already "done"), deleteWhenTheStatsWriteFailsDoesNotBin.
}
