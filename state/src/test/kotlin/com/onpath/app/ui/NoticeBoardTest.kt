package com.onpath.app.ui

import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The first slice cut out of the view model (rebuild phase 2 piece 1): where a message goes (owner: no dark bar; a line
 * where the user acted, the red box only for an error that has no such place). NoticesTest (:app) pins [NoticeRouting]
 * itself; these flows run the board that applies it – with the screens it reads as lambdas.
 */
class NoticeBoardTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    private var routesOpen = false
    private var settingsOpen = false
    private var mapShown = false
    private fun board() = NoticeBoard({ routesOpen }, { settingsOpen }, { mapShown })

    @Test fun anErrorWithoutItsPlaceIsABoxThatStaysUntilTheUserClearsIt() {
        val b = board()
        b.sayError("Export failed", NoticePlace.ROUTES)      // My routes was closed meanwhile
        assertEquals("Export failed", b.errorCard)
        assertNull(b.routesNote)
        b.sayInfo("Something else", NoticePlace.BACKGROUND)  // nothing else clears it
        assertEquals("Export failed", b.errorCard)
        b.errorCard = null                                    // the OK button
        assertNull(b.errorCard)
    }

    @Test fun aLineGoesWhereTheUserActedAndOnlyThere() {
        val b = board()
        routesOpen = true
        b.sayInfo("1 track restored.", NoticePlace.ROUTES)
        assertEquals(Notice("1 track restored.", NoticeKind.INFO), b.routesNote)
        routesOpen = false; settingsOpen = true
        b.sayError("Update failed", NoticePlace.SETTINGS)
        assertEquals(Notice("Update failed", NoticeKind.ERROR), b.settingsNote)
        assertNull("a line is not also a box", b.errorCard)
        // the map's quiet line: only while the Plan map is what the user looks at; each one restarts its time
        settingsOpen = false; mapShown = true
        b.sayInfo("Relief off", NoticePlace.MAP)
        assertEquals(Notice("Relief off", NoticeKind.INFO), b.mapNote)
        assertEquals(1, b.mapNoteTick)
        b.sayInfo("Relief off again", NoticePlace.MAP)
        assertEquals(2, b.mapNoteTick)
    }

    @Test fun aSecondErrorReplacesTheFirstAndAnInfoOnAClosedPlaceChangesNothing() {
        val b = board()
        b.sayError("first", NoticePlace.BACKGROUND)
        b.sayError("second", NoticePlace.MAP)
        assertEquals("second", b.errorCard)
        b.sayInfo("not shown", NoticePlace.ROUTES)           // My routes is closed: an INFO has nowhere to go
        assertEquals("second", b.errorCard)
        assertNull(b.routesNote); assertNull(b.settingsNote); assertNull(b.mapNote)
    }

    @Test fun aMessageThatIsNotShownIsStillInTheProblemLog() {
        val b = board()
        b.sayInfo("gone from view", NoticePlace.SETTINGS)    // Settings is closed
        assertTrue(problems.traced("ui").any { "INFO for settings not shown" in it && "gone from view" in it })
    }

    @Test fun theBoardIsThePortOtherSlicesSayThrough() {
        val b = board()
        val say: Say = b
        say.say("through the port", NoticeKind.ERROR, NoticePlace.BACKGROUND)
        assertEquals("through the port", b.errorCard)
    }
}
