package com.onpath.web

import com.onpath.app.data.Geo
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.PlanCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * „Übernachtung hier“ on the route (owner 2026-11-19): the night's point lies ON the routed line, so the route cannot
 * change – the leg is cut there ([LegSplit]), nothing is routed, the line, distance, climb and time stay identical. A
 * point beside the line (a hotel away from the route) is routed as before. A real run on E10_N45.rd5 where it is on this
 * computer (ONPATH_SEGMENTS, else ~/Projects/work/onpath-data/segments); skipped otherwise.
 */
class WebLegSplitTest {
    private val repo = File("../..").canonicalFile
    private val root = Files.createTempDirectory("weblegsplit").toFile()
    private val segments = File(System.getenv("ONPATH_SEGMENTS") ?: (System.getProperty("user.home") + "/Projects/work/onpath-data/segments"))
    // the Meraner Höhenweg (WebPlanTotalsTest): its first four points, Wandern T3
    private val hoehenweg = "AQUDDPiLugSE8YcBogbZH94K8yedLO898Q7VHK0ZuUWwL99M6GK6N7AHsFyWPLJgDOyUAauSAdlUAAABEU1lcmFuZXIgSMO2aGVud2VnAhEAR2FzdGhvZiBIb2NobXV0aAIKAUxlaXRlcmFsbQINAkhvY2hnYW5naGF1cwILA0dpZ2dlbGJlcmcCCgRIb2NoZm9yY2gCCwVVbnRlcnN0ZWxsAg4GS2F0aGFyaW5hYmVyZwIHB0Vpc2hvZgIRCFN0ZXR0aW5lciBIw7x0dGUCCQlQZmVsZGVycwIGClVsZmFzAhELR2FzdGhvZiBIb2NobXV0aA"

    @After fun tidy() { root.deleteRecursively() }

    private fun num(json: String, key: String) = Regex("\"$key\":(-?[0-9.]+)").find(json)!!.groupValues[1].toDouble()
    private fun legs(key: String) = Regex("\"$key\":(\\d+)").find(Engine.lastLegs)!!.groupValues[1].toInt()
    private fun enc(p: List<GeoPoint>) = p.joinToString(";") { "${Engine.num(it.lat, 6)},${Engine.num(it.lon, 6)}" }
    private fun pointsOf(rule: String): List<GeoPoint> =
        Regex("\\[(-?[0-9.]+),(-?[0-9.]+)]").findAll(rule.substringAfter("\"points\":[").substringBefore("]],") + "]")
            .map { GeoPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }.toList()

    private fun setUp(): Pair<List<GeoPoint>, com.onpath.app.routing.SharedPlan> {
        assumeTrue("no E10_N45.rd5 in $segments", File(segments, "E10_N45.rd5").isFile)
        Engine.init(root.path)
        File(root, "segments").mkdirs()
        Files.createSymbolicLink(File(root, "segments/E10_N45.rd5").toPath(), File(segments, "E10_N45.rd5").toPath())
        repo.resolve("app/src/main/assets/brouter").copyRecursively(File(root, "assets/brouter"))
        val plan = PlanCode.decode(hoehenweg)!!
        // the page's points: 6 decimals
        return Engine.parsePoints(enc(plan.points.take(4))) to plan
    }

    @Test fun aNightOnTheLineSplitsTheLegWithoutRouting() {
        val (pts, plan) = setUp()
        val act = plan.activity.name; val sac = plan.sac
        val before = Engine.plan(enc(pts), act, sac)
        assertEquals(3, legs("routed") + legs("cached")) // the store may hold them from another test of this JVM
        val line = Engine.lastPlanPoints(pts, plan.activity, plan.sac)!!
        // „Übernachtung hier“ in the middle of the second leg (a click on the route there)
        val a = line.indexOfFirst { Geo.distance(it, pts[1]) < 1.0 }.coerceAtLeast(0)
        val b = line.indexOfFirst { Geo.distance(it, pts[2]) < 1.0 }.let { if (it < 0) line.lastIndex else it }
        val spot = line[(a + b) / 2]
        val rule = WebDays.rule("addNight", enc(pts), act, sac, "", "", "d\t2", 0, spot.lat, spot.lon, "")
        val after = pointsOf(rule)
        assertEquals(rule, 5, after.size)
        val json = Engine.plan(enc(after), act, sac)
        assertEquals(Engine.lastLegs, 0, legs("routed"))
        for (k in listOf("distanceM", "ascentM", "descentM")) assertEquals(k, num(before, k), num(json, k), 0.01)
        val again = Engine.lastPlanPoints(after, plan.activity, plan.sac)!!
        assertEquals(line.size, again.size)
        assertTrue("the same line", line.indices.all { line[it] == again[it] })
        // the time of the whole plan and of the plan panel's rows (the model time on the same line)
        assertEquals(num(before, "timeS"), num(json, "timeS"), 0.5)
    }

    @Test fun aPointBesideTheLineIsRoutedAsBefore() {
        val (pts, plan) = setUp()
        val act = plan.activity.name; val sac = plan.sac
        Engine.plan(enc(pts), act, sac)
        val line = Engine.lastPlanPoints(pts, plan.activity, plan.sac)!!
        val a = line.indexOfFirst { Geo.distance(it, pts[1]) < 1.0 }.coerceAtLeast(0)
        val b = line.indexOfFirst { Geo.distance(it, pts[2]) < 1.0 }.let { if (it < 0) line.lastIndex else it }
        val spot = line[(a + b) / 2]
        // a hotel 300 m off the route: R1 + R3 – the point moves to the hotel, its two legs are routed
        val hotel = GeoPoint(spot.lat + 0.0027, spot.lon)
        val rule = WebDays.rule("setHotel", enc(pts), act, sac, "", "", "d\t2", -1, hotel.lat, hotel.lon,
            "Gasthof\t\t\t${spot.lat}\t${spot.lon}")
        val after = pointsOf(rule)
        assertEquals(rule, 5, after.size)
        Engine.plan(enc(after), act, sac)
        assertEquals(Engine.lastLegs, 2, legs("routed"))
        // a point a few metres beside the line: not on it, routed as a via (the router may take another way)
        val beside = after.toMutableList().also { it[2] = GeoPoint(spot.lat + 0.0001, spot.lon) }
        Engine.plan(enc(beside), act, sac)
        assertNotEquals(Engine.lastLegs, 0, legs("routed"))
    }
}
