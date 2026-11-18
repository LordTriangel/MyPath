package com.onpath.app.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Multi-day tours in the plan code (docs/design/multi-day-tours.md §5, owner 2026-11-17): new optional fields, still
 * version 1 – an older reader sees a normal long plan, the new reader everything; broken input is null, never a crash.
 */
class PlanCodeDaysTest {
    private val pts = listOf(
        GeoPoint(48.13743, 11.57549), GeoPoint(47.80000, 11.60000), GeoPoint(47.50000, 11.50000),
        GeoPoint(47.00000, 11.50000), GeoPoint(45.43713, 12.33265),
    )
    private val hotel = Hotel("Gasthof Post", "Dorfstraße 1, 6020 Innsbruck", "https://www.booking.com/hotel/at/post.html?aid=1&x=ü")
    private val tour = SharedPlan(
        pts, Activity.BIKE_TOURING, 2, pinned = setOf(1), names = mapOf(1 to "Bad Tölz", 3 to "Gasthof Post"),
        title = "München – Venedig", days = 6, limits = DayLimits(maxTimeMin = 330, maxAscentM = 1200),
        nights = mapOf(1 to NightMark.OVERNIGHT, 3 to NightMark(hotel)),
        description = "Über die Alpen.\n\nTag 3: früh los.",
    )

    private fun normalPart(p: SharedPlan) = p.copy(days = null, limits = DayLimits.NONE, nights = emptyMap(), description = null)

    private fun roundPoints(p: SharedPlan) = PlanCode.decode(PlanCode.encode(p.copy(days = null, nights = emptyMap(),
        limits = DayLimits.NONE, description = null)))!!.points

    @Test fun roundTripKeepsEveryMultiDayField() {
        val back = PlanCode.decode(PlanCode.encode(tour))!!
        assertEquals(tour.copy(points = back.points), back)
        assertEquals(roundPoints(tour), back.points)
    }

    @Test fun anOlderReaderSeesANormalLongPlan() {
        // the app before multi-day tours knew the tags 1 and 2 and skipped the rest: points, pins, names (the hotel as a
        // named point – scenario Q) and the title arrive unchanged
        val back = PlanCode.decode(PlanCode.encode(tour), knownTags = 2)!!
        assertEquals(normalPart(tour).copy(points = back.points), back)
        assertEquals("Gasthof Post", back.names[3])
    }

    @Test fun aNormalPlanKeepsItsBytes() {
        // no multi-day field set: not one byte more (the frozen code of PlanCodeTest stays; samePlan with old items)
        val plan = normalPart(tour)
        val withEmpty = plan.copy(limits = DayLimits(null, null), nights = emptyMap(), description = "  ")
        assertEquals(PlanCode.encode(plan), PlanCode.encode(withEmpty))
        assertEquals(plan.copy(points = roundPoints(plan)), PlanCode.decode(PlanCode.encode(plan)))
    }

    @Test fun multiDayFormatIsFrozen() {
        // codes already in links and synced items keep opening in every later app and in the web planner
        val plan = SharedPlan(listOf(GeoPoint(46.6717, 11.15), GeoPoint(46.68, 11.13), GeoPoint(46.6879, 11.1111)),
            Activity.HIKE, 3, days = 3, limits = DayLimits(300, 1000),
            nights = mapOf(1 to NightMark(Hotel("Post", null, "https://b.example/h"))), description = "A\nB")
        val code = "AQUDA8TcuQTwjYgB_AyfH6wMwx0AAwEDBASsAugHBR0BAgEEUG9zdAMTaHR0cHM6Ly9iLmV4YW1wbGUvaAYDQQpC"
        assertEquals(code, PlanCode.encode(plan))
        assertEquals(plan.copy(points = PlanCode.decode(code)!!.points), PlanCode.decode(code))
    }

    @Test fun limitsOfTheNewFields() {
        val long = tour.copy(
            days = 500, limits = DayLimits(maxTimeMin = 0, maxAscentM = 99_999),
            nights = mapOf(0 to NightMark.OVERNIGHT, 2 to NightMark(Hotel("H".repeat(300), "A".repeat(300), "L".repeat(900))),
                4 to NightMark.OVERNIGHT, 9 to NightMark.OVERNIGHT),
            description = "x".repeat(5000),
        )
        val back = PlanCode.decode(PlanCode.encode(long))!!
        assertEquals(PlanCode.MAX_DAYS, back.days)
        assertEquals(DayLimits.NONE, back.limits) // 0 minutes and 99 999 m are no limits
        assertEquals(setOf(2), back.nights.keys) // never on the start, the last point or a point that is not there
        val h = back.nights[2]!!.hotel!!
        assertEquals(PlanCode.MAX_NAME, h.name!!.length)
        assertEquals(PlanCode.MAX_ADDRESS, h.address!!.length)
        assertEquals(PlanCode.MAX_LINK, h.link!!.length)
        assertEquals(PlanCode.MAX_DESCRIPTION, back.description!!.length)
        assertEquals(1, PlanCode.decode(PlanCode.encode(tour.copy(days = 0)))!!.days)
    }

    @Test fun descriptionKeepsParagraphsOnly() {
        val back = PlanCode.decode(PlanCode.encode(tour.copy(description = "  Eins\r\nZwei\n\n\n\nDrei\u0000\t ")))!!
        assertEquals("Eins\nZwei\n\nDrei", back.description)
    }

    @Test fun aHotelWithoutTextsStaysAHotel() {
        val back = PlanCode.decode(PlanCode.encode(tour.copy(nights = mapOf(2 to NightMark(Hotel(" ", null, null))))))!!
        assertEquals(NightMark(Hotel()), back.nights[2])
        assertTrue(back.nights[2]!!.isHotel)
    }

    @Test fun unknownNightKindsAndHotelFieldsFromANewerApp() {
        fun withField(tag: Int, vararg data: Int): String {
            val base = java.util.Base64.getUrlDecoder().decode(PlanCode.encode(normalPart(tour)))
            val extra = byteArrayOf(tag.toByte(), data.size.toByte()) + data.map { it.toByte() }.toByteArray()
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(base + extra)
        }
        // kind 7 (unknown): still a night – a night is a night
        assertEquals(mapOf(2 to NightMark.OVERNIGHT), PlanCode.decode(withField(5, 2, 7))!!.nights)
        // a mark without a kind: Übernachtung
        assertEquals(mapOf(2 to NightMark.OVERNIGHT), PlanCode.decode(withField(5, 2))!!.nights)
        // a hotel with an unknown field 9 and its name
        val h = PlanCode.decode(withField(5, 2, 2, 9, 1, 65, 1, 2, 72, 73))!!.nights[2]!!.hotel
        assertEquals(Hotel("HI"), h)
        // a limits field with only the time (an older writer would never do it, but it must not break)
        assertEquals(DayLimits(maxTimeMin = 60), PlanCode.decode(withField(4, 60))!!.limits)
    }

    @Test fun brokenInputIsNullNeverACrash() {
        val code = PlanCode.encode(tour)
        for (cut in 1 until code.length) PlanCode.decode(code.substring(0, cut)) // must not throw
        val bytes = java.util.Base64.getUrlDecoder().decode(code)
        val rnd = Random(17)
        repeat(2000) {
            val b = bytes.copyOf()
            repeat(1 + rnd.nextInt(4)) { b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }
            PlanCode.decode(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)) // must not throw
        }
        assertNull(PlanCode.decode(code.dropLast(3) + "!!!"))
    }
}
