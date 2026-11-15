package com.onpath.app.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * The held place's add menu („Einfügen“ · „Neues Ziel“, owner 2026-11-15) in German at a larger (1.3) and the phone's
 * largest font (2.0): one line each, a label wraps rather than being cut. Same pictures as the
 * [ScreenshotTest] tests of the same name, drawn with this class's Paparazzi.
 */
class PlaceAddMenuLargeFontGermanTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f), showSystemUi = false)

    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)

    private val shots get() = ScreenshotTest().also {
        it.javaClass.getDeclaredField("paparazzi").apply { isAccessible = true }.set(it, paparazzi)
    }

    @Test fun poiMenu() = shots.poiMenu()
    @Test fun poiMenuClearGoal() = shots.poiMenuClearGoal()
}

/** The same at font size 1.3 (a common "larger" setting). */
class PlaceAddMenuMidFontGermanTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f), showSystemUi = false)

    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)

    private val shots get() = ScreenshotTest().also {
        it.javaClass.getDeclaredField("paparazzi").apply { isAccessible = true }.set(it, paparazzi)
    }

    @Test fun poiMenu() = shots.poiMenu()
    @Test fun poiMenuClearGoal() = shots.poiMenuClearGoal()
}
