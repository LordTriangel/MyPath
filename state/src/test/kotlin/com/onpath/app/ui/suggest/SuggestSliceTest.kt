package com.onpath.app.ui.suggest

import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.suggest.SuggestAsk
import com.onpath.app.suggest.SuggestCard
import com.onpath.app.suggest.SuggestCards
import com.onpath.app.suggest.SuggestPill
import com.onpath.app.testkit.OwnComposeSnapshot
import com.onpath.app.testkit.ProblemsRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The round-trip suggestions' slice (docs/design/tour-suggestions.md §7 stage 4) with a fake runner: menu row → wish
 * sheet → OK → cards (the first chosen, its loop on the map) → „Übernehmen“ hands the loop with the wish's activity to the
 * plan and closes; routing data in reach loads first; cells without a suggest file are fetched and the run made once
 * again; a failing run is a line in the panel, never a crash.
 */
class SuggestSliceTest {
    @get:Rule val composeState = OwnComposeSnapshot()
    @get:Rule val problems = ProblemsRule()

    private val hilden = GeoPoint(51.168, 6.9395)

    private fun card(id: Int, km: Double, lon: Double) = SuggestCard(id, "Runde $id", if (id == 2) "Rundweg" else null, if (id == 2) 3.5 else null,
        km, km / 4, 120, "über Feld und Wald", listOf(SuggestPill("grün", true, 2), SuggestPill("lauter", false, 1)),
        doubleArrayOf(6.9395, 51.168, lon, 51.17, 6.9395, 51.168))

    private fun result(vararg cards: SuggestCard, notes: List<String> = emptyList(), fetch: List<IntArray> = emptyList()) =
        SuggestCards(cards.toList(), notes, fetch.isNotEmpty(), doubleArrayOf(2.0, 3.0), intArrayOf(0, 400), 12, emptyMap(), fetch)

    private class Fake(val answers: ArrayDeque<() -> SuggestCards>) : SuggestHost {
        val asks = ArrayList<SuggestAsk>()
        val ensured = ArrayList<List<String>>()
        val fetched = ArrayList<List<IntArray>>()
        val fits = ArrayList<List<GeoPoint>>()
        var taken: Triple<List<GeoPoint>, Activity, Int>? = null
        var missing = 0
        var takeOk = true
        override suspend fun suggest(ask: SuggestAsk): SuggestCards { asks += ask; return answers.removeFirst()() }
        override fun ensureRouting(parts: List<String>): List<String> { ensured += parts; return parts }
        override fun missingRouting(parts: List<String>): List<String> = if (missing-- > 0) parts else emptyList()
        override suspend fun fetchCells(cells: List<IntArray>): Boolean { fetched += cells; return true }
        override fun fit(points: List<GeoPoint>) { fits += points }
        override fun take(points: List<GeoPoint>, activity: Activity, sac: Int, done: (Boolean) -> Unit) {
            taken = Triple(points, activity, sac); done(takeOk)
        }
    }

    private fun slice(fake: Fake, block: SuggestSlice.() -> Unit): SuggestSlice {
        lateinit var s: SuggestSlice
        runBlocking { s = SuggestSlice(this, fake, on = true); s.block() } // runBlocking waits for the run it started
        return s
    }

    @Test fun wishToCardsToPlan() {
        val fake = Fake(ArrayDeque(listOf({ result(card(1, 8.4, 6.95), card(2, 10.1, 6.96)) })))
        val s = slice(fake) {
            open(hilden, Activity.HIKE, 3)
            assertTrue("the wish sheet", wishOpen)
            assertEquals(SuggestWish.of(hilden, Activity.HIKE, 3), wish)
            wish = wish!!.copy(activity = Activity.MTB, ziel = setOf("wasser", "gipfel"))
            ok()
            assertFalse(wishOpen)
        }
        val ask = fake.asks.single()
        assertEquals(Activity.MTB, ask.activity)
        assertEquals(listOf("gipfel", "wasser"), ask.ziel) // the chips' order, as the planner sends them
        assertEquals(SuggestPhase.CARDS, s.phase)
        assertEquals(2, s.cards.size)
        assertEquals("the first card is chosen", 0, s.chosen)
        assertEquals("its loop is the one line on the map", s.cards[0].points(), s.loop)
        assertEquals("the camera fits it", s.cards[0].points(), fake.fits.last())

        s.choose(1)
        assertEquals(s.cards[1].points(), s.loop)
        val want = s.cards[1].points()
        s.take(1)
        val (pts, act, sac) = fake.taken!!
        assertEquals("the loop goes to the plan", want, pts)
        assertEquals(Activity.MTB, act); assertEquals(3, sac)
        assertNull("the suggestions close with the plan", s.phase)
        assertNull(s.loop)
        assertTrue(s.cards.isEmpty())
    }

    @Test fun aFailedTakeKeepsTheCards() {
        val fake = Fake(ArrayDeque(listOf({ result(card(1, 8.4, 6.95)) }))).apply { takeOk = false }
        val s = slice(fake) { open(hilden, Activity.HIKE, 2); ok() }
        s.take(0)
        assertEquals(SuggestPhase.CARDS, s.phase)
        assertFalse(s.taking)
    }

    @Test fun routingDataLoadsFirstThenTheRun() {
        val fake = Fake(ArrayDeque(listOf({ SuggestCards.loadRouting(listOf("E5_N50/3")) }, { result(card(1, 8.4, 6.95)) }))).apply { missing = 1 }
        val s = slice(fake) { open(hilden, Activity.HIKE, 2); ok() }
        assertEquals(listOf(listOf("E5_N50/3")), fake.ensured)
        assertEquals("run again once the data is there", 2, fake.asks.size)
        assertEquals(SuggestPhase.CARDS, s.phase)
        assertEquals(1, s.cards.size)
    }

    @Test fun cellsWithoutASuggestFileAreFetchedAndTheRunMadeOnceAgain() {
        val note = "Die Karte wird hier gerade aktualisiert …"
        val fake = Fake(ArrayDeque(listOf({ result(card(1, 8.4, 6.95), notes = listOf(note), fetch = listOf(intArrayOf(531, 341))) },
            { result(card(1, 8.4, 6.95), card(2, 9.0, 6.96), notes = listOf(note), fetch = listOf(intArrayOf(531, 341))) })))
        val s = slice(fake) { open(hilden, Activity.HIKE, 2); ok() }
        assertArrayEquals(intArrayOf(531, 341), fake.fetched.single().single())
        assertEquals("one run again, not a loop", 2, fake.asks.size)
        assertEquals(2, s.cards.size)
        assertEquals(listOf(note), s.notes)
    }

    @Test fun aFailingRunIsALineNotACrash() {
        val fake = Fake(ArrayDeque(listOf({ throw IllegalStateException("broken suggest file") })))
        val s = slice(fake) { open(hilden, Activity.HIKE, 2); ok() }
        assertEquals(SuggestPhase.FAILED, s.phase)
        assertTrue(problems.said("suggest").any { it.contains("failed") })
    }

    @Test fun cancelKeepsCardsAndChangeReopensTheLastWish() {
        val fake = Fake(ArrayDeque(listOf({ result(card(1, 8.4, 6.95)) })))
        val s = slice(fake) { open(hilden, Activity.HIKE, 2); wish = wish!!.copy(hours = 1f..1.5f); ok() }
        s.change()
        assertTrue(s.wishOpen)
        assertEquals(1f..1.5f, s.wish!!.hours)
        s.cancelWish()
        assertEquals("the cards stay under a cancelled sheet", SuggestPhase.CARDS, s.phase)
        s.open(hilden, Activity.BIKE_TOURING, 2)
        assertEquals("the same spot keeps the last wish", 1f..1.5f, s.wish!!.hours)
        s.close()
        assertNull(s.phase); assertFalse(s.wishOpen)
    }

    /** On for everyone since 2026-11-18 (owner): a slice without a stored choice offers „Rundtour von hier“. */
    @Test fun onByDefault() {
        runBlocking { assertTrue(SuggestSlice(this, Fake(ArrayDeque())).on) }
    }

    @Test fun theSwitchOffClosesEverything() {
        val fake = Fake(ArrayDeque(listOf({ result(card(1, 8.4, 6.95)) })))
        var stored: Boolean? = null
        lateinit var s: SuggestSlice
        runBlocking { s = SuggestSlice(this, fake, on = true) { stored = it }; s.open(hilden, Activity.HIKE, 2); s.ok() }
        s.switch(false)
        assertFalse(s.on); assertEquals(false, stored); assertNull(s.phase)
    }

    @Test fun theWishAsTheMethodsQuestion() {
        val w = SuggestWish.of(hilden, Activity.HIKE, 2)
        assertEquals(2f..3f, w.hours)
        assertEquals("hike: bis 400 m", 0f..400f, w.ascent)
        val a = w.ask()
        assertArrayEquals(doubleArrayOf(2.0, 3.0), a.hours, 0.0)
        assertArrayEquals(arrayOf<Double?>(null, 400.0), a.ascent)
        assertNull("„egal“: both ends open", w.copy(ascent = 0f..SuggestWish.UP_MAX).ask().ascent)
        assertArrayEquals(arrayOf<Double?>(300.0, null), w.copy(ascent = 300f..SuggestWish.UP_MAX).ask().ascent)
        assertEquals(0f..800f, SuggestWish.of(hilden, Activity.EMTB, 2).ascent)
        assertEquals(0f..600f, SuggestWish.of(hilden, Activity.BIKE_TREKKING, 2).ascent)
    }
}
