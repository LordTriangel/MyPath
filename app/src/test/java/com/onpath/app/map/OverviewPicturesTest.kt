package com.onpath.app.map

import com.onpath.app.data.Tour
import com.onpath.app.data.TourKind
import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The large overview pictures kept on the phone (owner 2026-11-15, swiping through My routes): key, choice, cap, files. */
class OverviewPicturesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tour(id: Long, created: Long, fav: Boolean = false, draft: Boolean = false, state: String = "done", uuid: String = "u$id",
                     deleted: Long? = null) =
        Tour(id, TourKind.PLANNED, "T$id", created, "HIKING", 2, 1000.0, 10.0, 10.0, 600.0, state, emptyList(), uuid = uuid,
            favourite = fav, draft = draft, deletedMs = deleted)

    @Test fun theKeyChangesWithLinePlanSizeAndTheme() {
        val base = OverviewRules.key("L", emptyList(), 1000, 1200, "classic")
        assertEquals(base, OverviewRules.key("L", emptyList(), 1000, 1200, "classic"))
        assertNotEquals(base, OverviewRules.key("L2", emptyList(), 1000, 1200, "classic"))
        assertNotEquals(base, OverviewRules.key("L", listOf(GeoPoint(46.0, 11.0), GeoPoint(46.1, 11.1)), 1000, 1200, "classic"))
        assertNotEquals(base, OverviewRules.key("L", emptyList(), 1000, 1300, "classic"))
        assertNotEquals(base, OverviewRules.key("L", emptyList(), 1000, 1200, "dark"))
    }

    @Test fun theNewestTwentyAndEveryFavourite() {
        val tours = (1L..30L).map { tour(it, it * 1000, fav = it == 2L) } +
            tour(40, 99_000, draft = true) + tour(41, 98_000, state = "active") + tour(42, 97_000, uuid = "") +
            tour(43, 96_000, deleted = 5)
        val w = OverviewRules.wanted(tours)
        assertEquals((30L downTo 11L).toList() + 2L, w.map { it.id })
        assertFalse("a draft, a recording, no uuid, the bin: none", w.any { it.id >= 40 })
    }

    @Test fun theCapTakesTheOldestUsedButNeverAWantedOne() {
        val usage = (1..45).associate { "u$it" to it.toLong() } + ("gone" to 100L)
        val keep = (1..45).mapTo(HashSet()) { "u$it" }
        val out = OverviewRules.evict(usage, keep, wanted = setOf("u1", "u2"), cap = 40)
        assertTrue("a tour no longer on the phone loses its picture", "gone" in out)
        assertEquals(listOf("gone", "u3", "u4", "u5", "u6", "u7"), out)
        assertEquals(listOf("gone"), OverviewRules.evict(usage, keep + "x", emptySet(), cap = 50))
    }

    @Test fun storedPictureIsReadOnlyForItsKeyAndNeverThrows() {
        val pics = OverviewPictures(tmp.newFolder("overviews"))
        assertNull(pics.bytes("a", "k"))
        assertTrue(pics.write("a", OverviewPictures.Meta("k1", 1f, 2f, 3f, 4f), byteArrayOf(7, 8)))
        val (m, b) = pics.bytes("a", "k1")!!
        assertEquals(listOf(1f, 2f, 3f, 4f), listOf(m.startX, m.startY, m.goalX, m.goalY))
        assertEquals(listOf<Byte>(7, 8), b.toList())
        assertNull("another line / size / theme: drawn again", pics.bytes("a", "k2"))
        pics.dir.resolve("a.key").writeText("garbage")
        assertNull(pics.meta("a"))
        assertNull(pics.bytes("a", "k1"))
        pics.remove("a")
        assertTrue(pics.usage().isEmpty())
    }

    @Test fun theSizeOfTheLastOpeningIsKept() {
        val pics = OverviewPictures(tmp.newFolder("o2"))
        assertNull("never opened: the background waits", pics.size())
        pics.setSize(1080, 1400, 48)
        assertEquals(Triple(1080, 1400, 48), pics.size())
        pics.dir.resolve("size").writeText("x y")
        assertNull(pics.size())
    }

    /** A done tour's look back takes room from its map (owner 2026-11-15, round 3): its pictures keep their own size. */
    @Test fun aDoneToursOverviewKeepsItsOwnSize() {
        val pics = OverviewPictures(tmp.newFolder("o3"))
        pics.setSize(1080, 1400, 48)
        assertNull("a done tour's overview never opened: the background waits for it", pics.size(done = true))
        pics.setSize(1080, 820, 48, done = true)
        assertEquals(Triple(1080, 1400, 48), pics.size())
        assertEquals(Triple(1080, 820, 48), pics.size(done = true))
    }
}
