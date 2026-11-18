package com.onpath.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCard
import com.onpath.app.suggest.SuggestCards
import com.onpath.app.suggest.SuggestPill
import com.onpath.app.ui.suggest.SuggestHost
import com.onpath.app.ui.suggest.SuggestPhase
import com.onpath.strings.Res
import com.onpath.strings.Txt
import com.onpath.strings.common_ok
import com.onpath.strings.suggest_ui_from_here
import com.onpath.strings.suggest_ui_take
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Round-trip suggestions on the phone (docs/design/tour-suggestions.md §7 stage 4), on by default since 2026-11-18: a hold
 * on the empty map → „Rundtour von hier“ (right under „Hier starten“) → the wish sheet → OK → the cards in the plan
 * sheet's place, side by side in one row (sketch 8) → a swipe to the second card draws its loop (the one suggestion
 * line on the map) → „Übernehmen“.
 *
 * The method itself is not run here: the test map (Meran) has no suggest file, so the run is a stand-in answer
 * ([SuggestHost.suggest] replaced, everything else – camera, „Übernehmen“ through TrackToPlan – is the app's own).
 * With real routing data (the workflow's E10_N45.rd5) „Übernehmen“ must give a plan; without it nothing may crash.
 */
@RunWith(AndroidJUnit4::class)
class RoundTripSuggestE2ETest : E2EBase() {

    @Test fun holdRoundTripWishCardsLoopTake() {
        launch()
        val asks = ArrayList<SuggestAsk>()
        scenario.onActivity { a ->
            val s = vm(a).suggest
            assertTrue("on for everyone since 2026-11-18 (owner), no switch needed", s.on)
            val real = s.host
            s.host = object : SuggestHost by real {
                override suspend fun suggest(ask: SuggestAsk): SuggestCards { asks += ask; return answer(ask.lat, ask.lon) }
            }
        }
        val (w, h) = mapSize()
        hold(w * 0.45f, h * 0.35f, takeAddMenu = false)
        waitUntil("the first-point menu", 5_000) { read { it.firstPointMenu } != null }
        clickText(Txt.s(Res.string.suggest_ui_from_here))
        waitUntil("the wish sheet", 5_000) { read { it.suggest.wishOpen } }
        shot("suggest-wish")
        clickText(Txt.s(Res.string.common_ok))
        waitUntil("the cards", 10_000) { read { it.suggest.phase } == SuggestPhase.CARDS }
        assertEquals("one run for the wish", 1, asks.size)
        assertEquals("the hike's pre-set: 2–3 h", 2.0, asks[0].hours[0], 0.0)
        assertNotNull("the first card", findFresh(By.text("Runde am Hang"), 5_000))
        shot("suggest-cards")

        // the cards sit side by side in one row (sketch 8): a swipe to the left brings the second card to the front
        val first = findFresh(By.text("Runde am Hang"), 5_000)!!.visibleBounds
        val (sw, _) = mapSize()
        device.swipe((sw * 0.85f).toInt(), first.centerY(), (sw * 0.15f).toInt(), first.centerY(), 25)
        waitUntil("the second card chosen by the swipe", 5_000) { read { it.suggest.chosen } == 1 }
        assertEquals("only the chosen loop is on the map", read { it.suggest.cards[1].points() }, read { it.suggest.loop })
        waitUntil("its line drawn by MapLibre", 10_000) { loopDrawn() }
        shot("suggest-loop")

        // the button may be recomposed under the finger's search (the camera fit moves the sheet): find it fresh, tap
        val take = Txt.s(Res.string.suggest_ui_take)
        waitUntil("„Übernehmen“ tapped", 10_000) {
            // every card has one (sketch 8): the chosen card's is the one shown whole, not the sliver of a card beside it
            runCatching {
                findFresh(By.text(take), 2_000) // a fresh look at the screen (the cache cleared) …
                val b = device.findObjects(By.text(take)).map { it.visibleBounds }.maxByOrNull { it.width() } ?: return@runCatching false
                device.click(b.centerX(), b.centerY()) // … then the widest one, by its place (a node may go stale)
            }.getOrDefault(false) // expected: a stale node – again
        }
        if (realRouting) {
            waitUntil("the loop as a plan", 60_000) { waypointCount() >= 2 }
            assertEquals("the suggestions closed", null, read { it.suggest.phase })
            assertTrue("a round trip: it ends where it starts", read { it.isRoundTrip() })
        } else {
            // no routing data in this run: TrackToPlan cannot fit – the cards stay (or the plan is there), no crash
            waitUntil("Übernehmen answered", 30_000) { !read { it.suggest.taking } }
            assertTrue(read { it.suggest.phase == SuggestPhase.CARDS || it.waypoints.size >= 2 })
        }
        shot("suggest-taken")
    }

    /** Two loops of ~1.5 km around the held spot (closed: first = last). */
    private fun answer(lat: Double, lon: Double): SuggestCards {
        fun loop(dLon: Double) = doubleArrayOf(lon, lat, lon + dLon, lat + 0.004, lon + dLon * 2, lat, lon + dLon, lat - 0.004, lon, lat)
        val cards = listOf(
            SuggestCard(1, "Runde am Hang", null, null, 4.2, 1.2, 180, "Waalweg und Wald", listOf(SuggestPill("grün", true, 2)), loop(0.005)),
            SuggestCard(2, "Runde im Tal", "Rundweg", 2.0, 3.6, 1.0, 60, "am Bach entlang", listOf(SuggestPill("lauter", false, 1)), loop(-0.005)),
        )
        return SuggestCards(cards, emptyList(), false, doubleArrayOf(2.0, 3.0), intArrayOf(0, 400), 2, emptyMap())
    }

    /** MapLibre draws the suggestion line ([com.onpath.app.map.MapController.SRC_SUGGEST]) on the screen. */
    private fun loopDrawn(): Boolean {
        var drawn = false
        val done = CountDownLatch(1)
        scenario.onActivity { a ->
            val mv = mapView(a)!!
            mv.getMapAsync { m ->
                drawn = m.queryRenderedFeatures(android.graphics.RectF(0f, 0f, mv.width.toFloat(), mv.height.toFloat()),
                    com.onpath.app.map.MapController.SRC_SUGGEST).isNotEmpty()
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
        return drawn
    }

    private fun <T> read(f: (com.onpath.app.ui.AppViewModel) -> T): T {
        var v: T? = null
        scenario.onActivity { v = f(vm(it)) }
        @Suppress("UNCHECKED_CAST") return v as T
    }
}
