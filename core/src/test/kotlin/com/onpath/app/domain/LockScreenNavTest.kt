package com.onpath.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenNavTest {
    @Test fun navigateDuringATourShowsOverTheLockScreen() = assertTrue(LockScreenNav.shows(navigate = true, touring = true))
    @Test fun planNever() = assertFalse(LockScreenNav.shows(navigate = false, touring = true))
    @Test fun noTourNever() = assertFalse(LockScreenNav.shows(navigate = true, touring = false))
}
