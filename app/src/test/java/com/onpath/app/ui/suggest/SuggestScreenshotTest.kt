package com.onpath.app.ui.suggest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.suggest.SuggestCard
import com.onpath.app.suggest.SuggestPill
import com.onpath.app.ui.MapPlaceholder
import com.onpath.app.ui.OnPathTheme
import com.onpath.strings.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * The round-trip suggestions on the phone (tour-suggestions.md §7 stage 4): the wish sheet of sketch 5 B (Material 3
 * dialog, activity as plain labels, two range sliders, Ziel chips, Abbrechen · OK), the wait and the cards of sketch 4 A
 * with the pills of sketch 7 side by side in one row to swipe (sketch 8) in the plan sheet's place. English, German, German at a larger font.
 */
object SuggestShots {
    private val at = GeoPoint(51.168, 6.9395)
    private val line = doubleArrayOf(6.9395, 51.168, 6.95, 51.17, 6.9395, 51.168)

    fun wish(activity: Activity = Activity.HIKE) = SuggestWish.of(at, activity, 2).copy(ascent = 0f..450f, ziel = setOf("wasser"))

    private fun cards() = listOf(
        SuggestCard(1, "Rund um den Elbsee", null, null, 9.4, 2.4, 60,
            "Elbsee, Hildener Heide und ein Stück am Itterbach – meist Waldwege, wenig Straße, ruhig bis zum Ende.",
            listOf(SuggestPill(Txt.s(Res.string.suggest_pill_natur_good), true, 3), SuggestPill(Txt.s(Res.string.suggest_pill_ruhe_good), true, 2),
                SuggestPill(Txt.s(Res.string.suggest_pill_road, "450"), false, 2)), line),
        SuggestCard(2, "Neanderlandsteig Etappe 3", "Neanderlandsteig", 6.2, 11.8, 3.1, 140,
            "Durch das Neandertal, über die Höhen bei Gruiten und zurück am Bach entlang.",
            listOf(SuggestPill(Txt.s(Res.string.suggest_pill_besonderes_good), true, 2), SuggestPill(Txt.s(Res.string.suggest_pill_longer, "15"), false, 2),
                SuggestPill(Txt.s(Res.string.suggest_pill_weg_tracks), false, 1)), line),
        SuggestCard(3, "Hildener Stadtwald", null, null, 7.9, 2.0, 40, "Stadtwald und Heide, am Ende durch den Ort.",
            listOf(SuggestPill(Txt.s(Res.string.suggest_pill_town, "25"), false, 1)), line),
    )

    fun dialog(paparazzi: Paparazzi, activity: Activity = Activity.HIKE) = paparazzi.snapshot {
        OnPathTheme {
            Box(Modifier.fillMaxSize()) { MapPlaceholder(Modifier.fillMaxSize()) }
            SuggestWishDialog(wish(activity), {}, {}, {})
        }
    }

    fun panel(paparazzi: Paparazzi, phase: SuggestPhase, notes: List<String> = emptyList(), chosen: Int = 0) = paparazzi.snapshot {
        OnPathTheme {
            Box(Modifier.fillMaxSize()) {
                MapPlaceholder(Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter)) {
                    SuggestPanel(SuggestPanelUi(phase, wishSummary(wish()), if (phase == SuggestPhase.CARDS) cards() else emptyList(), notes, chosen = chosen),
                        SuggestPanelCallbacks())
                }
            }
        }
    }
}

open class SuggestScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    @Test fun wishSheet() = SuggestShots.dialog(paparazzi)
    @Test fun wishSheetBike() = SuggestShots.dialog(paparazzi, Activity.BIKE_TREKKING)
    @Test fun waiting() = SuggestShots.panel(paparazzi, SuggestPhase.WAIT)
    @Test fun cards() = SuggestShots.panel(paparazzi, SuggestPhase.CARDS)
    /** Swiped to the second card (sketch 8): it is in front, chosen; the first is off to the left, the third looks in. */
    @Test fun cardsSecond() = SuggestShots.panel(paparazzi, SuggestPhase.CARDS, chosen = 1)
    @Test fun cardsMapUpdating() = SuggestShots.panel(paparazzi, SuggestPhase.CARDS, listOf(Txt.s(Res.string.suggest_note_map_updating)))
    @Test fun failed() = SuggestShots.panel(paparazzi, SuggestPhase.FAILED)
}

class SuggestGermanScreenshotTest : SuggestScreenshotTest() {
    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)
}

/** German at font size 1.3: nothing cut, the buttons in one row (Material 3). */
class SuggestLargeFontGermanTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(fontScale = 1.3f), showSystemUi = false)

    private val before = Locale.getDefault()
    @Before fun german() = Locale.setDefault(Locale.GERMANY)
    @After fun back() = Locale.setDefault(before)

    @Test fun wishSheet() = SuggestShots.dialog(paparazzi)
    @Test fun cards() = SuggestShots.panel(paparazzi, SuggestPhase.CARDS)
}
