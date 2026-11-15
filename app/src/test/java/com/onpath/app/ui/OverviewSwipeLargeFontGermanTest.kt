package com.onpath.app.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * The overview's „3 von 27“ pill (owner 2026-11-15, swiping through My routes) in German at the phone's largest font
 * (2.0): it stays one line over the picture. Same picture as [ScreenshotTest.routesOverviewSwipe].
 */
class OverviewSwipeLargeFontGermanTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 2.0f), showSystemUi = false)

    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)

    private val shots get() = ScreenshotTest().also {
        it.javaClass.getDeclaredField("paparazzi").apply { isAccessible = true }.set(it, paparazzi)
    }

    @Test fun routesOverviewSwipe() = shots.routesOverviewSwipe()
}
