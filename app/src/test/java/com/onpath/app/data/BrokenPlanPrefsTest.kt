package com.onpath.app.data

import com.onpath.app.debug.Problems
import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Safety net for the plan the app restores at every start (preferences `plan`, `planPinned`, `planSavedPoints`,
 * `planLinkPoints`, read in AppViewModel's first lines – a throw there is a crash at every start, the app would never
 * open again). A missing, empty, cut or garbage value: never a throw, an empty plan (or "never saved"), a warning in
 * Problems, and the next save works. (The preferences file itself is Android's: a broken XML reads as empty there.)
 */
class BrokenPlanPrefsTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("plan-bad").toFile()
        Problems.init(dir); Problems.clear()
    }

    private val pts = listOf(GeoPoint(46.67171, 11.15003), GeoPoint(46.68512, 11.1347), GeoPoint(46.6902, 11.1611))

    /** The stored text of a good plan, as [Prefs] writes it. */
    private val good: String by lazy {
        val sp = MapPrefs(); Prefs(sp, dir).planWaypoints = pts
        sp.getString("plan", null)!!
    }

    private fun cases() = listOf(
        "empty" to "",
        "blank" to " \n",
        "cut in half" to good.take(good.length / 2),
        "cut inside the first point" to good.take(8),
        "one bracket" to "[",
        "garbage" to "\u0000ÿ{{garbage",
        "an object, not a list" to "{\"lat\":46.5,\"lon\":11.2}",
        "a list of numbers" to "[46.5,11.2]",
        "a point without lon" to "[{\"lat\":46.5}]",
        "a coordinate a word" to "[{\"lat\":\"north\",\"lon\":11.2}]",
        "a null point" to "[null]",
    )

    @Test fun aBrokenPlanIsAnEmptyPlanNeverAThrow() {
        for ((what, text) in cases()) for (key in listOf("plan", "planPinned", "planSavedPoints", "planLinkPoints")) {
            Problems.clear()
            val sp = MapPrefs(mutableMapOf<String, Any?>(key to text))
            val p = Prefs(sp, dir)
            val got: Collection<GeoPoint>? = try {
                when (key) {
                    "plan" -> p.planWaypoints
                    "planPinned" -> p.planPinned
                    "planSavedPoints" -> p.planSavedPoints
                    else -> p.planLinkPoints
                }
            } catch (t: Throwable) { throw AssertionError("$key $what: reading threw", t) }
            assertTrue("$key $what: no half plan ($got)", got.isNullOrEmpty())
            if (text.isNotBlank()) assertTrue("$key $what: reported",
                Problems.snapshot().any { it.area == "db" && it.message.contains("waypoint list unreadable") })
            // the next save works
            p.planWaypoints = pts; p.planPinned = setOf(pts[1]); p.planSavedPoints = pts; p.planLinkPoints = pts
            assertEquals("$key $what", pts, Prefs(sp, dir).planWaypoints)
            assertEquals("$key $what", setOf(pts[1]), Prefs(sp, dir).planPinned)
            assertEquals("$key $what", pts, Prefs(sp, dir).planSavedPoints)
            assertEquals("$key $what", pts, Prefs(sp, dir).planLinkPoints)
        }
    }

    @Test fun nothingStoredIsAnEmptyPlanAndSaysNothing() {
        val p = Prefs(MapPrefs(), dir)
        assertTrue(p.planWaypoints.isEmpty()); assertTrue(p.planPinned.isEmpty())
        assertNull(p.planSavedPoints); assertNull(p.planLinkPoints); assertNull(p.planSavedName)
        assertEquals(-1L, p.planSavedId)
        assertTrue(Problems.snapshot().isEmpty())
    }

    @Test fun aGoodPlanBesideABrokenPartStays() {
        // the pinned points are cut, the plan itself is whole: the plan is restored, only the pins are gone
        val sp = MapPrefs(mutableMapOf<String, Any?>("plan" to good, "planPinned" to good.take(good.length / 2), "planSavedPoints" to "garbage"))
        val p = Prefs(sp, dir)
        assertEquals(pts, p.planWaypoints)
        assertTrue(p.planPinned.isEmpty())
        assertTrue(p.planSavedPoints!!.isEmpty())
    }
}
