package com.onpath.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the imports decide: the GPX track's name, the map-data import's message, the failed GPX import's text. */
class ImportRulesTest {
    @Test fun theTracksOwnNameWinsAndTheDefaultIsAskedOnlyWhenNothingElseIsThere() {
        var asked = 0
        val def = { asked++; "Import 3 Nov" }
        assertEquals("Rax", ImportRules.gpxName("Rax", "file.gpx", def))
        assertEquals("file", ImportRules.gpxName(null, "file.gpx", def))
        assertEquals("file", ImportRules.gpxName(null, "file.GPX", def))
        assertEquals("a.gpx", ImportRules.gpxName(null, "a.gpx.gpx", def))
        assertEquals(0, asked)
        assertEquals("Import 3 Nov", ImportRules.gpxName(null, null, def))
        assertEquals(1, asked)
    }

    @Test fun theDataImportSaysSummaryPackageNoteAndTheRestartHintOnlyForNewTilesDuringATour() {
        val hint = { "Restart after the tour." }
        assertEquals("Done. Mixed.", ImportRules.dataImportMessage("Done.", { " Mixed." }, false, { true }, hint))
        assertEquals("Done.", ImportRules.dataImportMessage("Done.", { "" }, true, { false }, hint))
        assertEquals("Done. Restart after the tour.", ImportRules.dataImportMessage("Done.", { "" }, true, { true }, hint))
        assertEquals("Done. Mixed. Restart after the tour.", ImportRules.dataImportMessage("Done.", { " Mixed." }, true, { true }, hint))
    }

    @Test fun theRecordingStateIsReadOnlyWhenNewTilesCameIn() {
        var read = false
        ImportRules.dataImportMessage("Done.", { "" }, false, { read = true; true }) { "x" }
        assertFalse(read)
        ImportRules.dataImportMessage("Done.", { "" }, true, { read = true; true }) { "x" }
        assertTrue(read)
    }

    @Test fun aFailedGpxImportShowsOwnReasonElseTheGeneralText() {
        var plainAsked = false
        assertEquals("with: No track.", ImportRules.gpxFailureText("No track.", { "with: $it" }) { plainAsked = true; "general" })
        assertFalse(plainAsked)
        assertEquals("general", ImportRules.gpxFailureText("", { "with: $it" }) { "general" })
    }
}
