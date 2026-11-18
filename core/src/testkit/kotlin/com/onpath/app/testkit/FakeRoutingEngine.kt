package com.onpath.app.testkit

import com.onpath.app.data.Geo
import com.onpath.app.routing.Activity
import com.onpath.app.routing.GeoPoint
import com.onpath.app.routing.RouteLimits
import com.onpath.app.routing.RouteResult
import com.onpath.app.routing.RoutingEngine
import com.onpath.app.routing.RoutingException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The routing engine of the JVM test kit (docs: backlog "Code audit 2026-11-12", (b) a harness for state holders):
 * a route is the straight way through the waypoints in order, a point every [stepM] metres, the length as walked –
 * the same request always gives the same line, so tests can compare lines. What a state holder needs to be tested
 * against is switchable per test:
 * - [calls] – every request in order (how often and for what was routed);
 * - [fail] – a request that finds no route (throws [RoutingException] with the text it returns);
 * - [hold] / [release] – requests wait inside the engine until released: "a route is still running while …";
 * - [detour] – a leg that is that much longer than the straight way (a bulge to its left).
 * Thread-safe; blocking like the real engine, so it runs on whatever executor the code under test routes on
 * ([ManualExecutor] for a fixed order).
 */
class FakeRoutingEngine(private val stepM: Double = 10.0) : RoutingEngine {
    data class Call(val waypoints: List<GeoPoint>, val activity: Activity, val sac: Int)

    val calls: MutableList<Call> = Collections.synchronizedList(ArrayList())
    /** Not null for a request = no route, with this message. */
    @Volatile var fail: (Call) -> String? = { null }
    /** Factor ≥ 1 for a leg (from, to). */
    @Volatile var detour: (GeoPoint, GeoPoint) -> Double = { _, _ -> 1.0 }

    @Volatile private var gate: CountDownLatch? = null
    @Volatile private var waiting = CountDownLatch(0)

    /** From now on every request waits inside the engine until [release]. */
    fun hold() { gate = CountDownLatch(1); waiting = CountDownLatch(1) }

    /** Lets the held requests (and all later ones) through. */
    fun release() { gate?.countDown(); gate = null }

    /** Waits until a request is held inside the engine (it has been counted in [calls]); false = none came in time. */
    fun awaitHeld(timeoutMs: Long = 10_000): Boolean = waiting.await(timeoutMs, TimeUnit.MILLISECONDS)

    override fun route(waypoints: List<GeoPoint>, activity: Activity, sacLimit: Int): RouteResult =
        route(waypoints, activity, sacLimit, RouteLimits(30_000))

    override fun route(waypoints: List<GeoPoint>, activity: Activity, sacLimit: Int, limits: RouteLimits): RouteResult {
        val call = Call(waypoints.toList(), activity, sacLimit)
        calls += call
        gate?.let { g ->
            waiting.countDown()
            if (!g.await(60, TimeUnit.SECONDS)) throw RoutingException("FakeRoutingEngine: held for a minute and never released")
        }
        if (limits.cancel?.isCancelled == true) throw RoutingException("cancelled")
        fail(call)?.let { throw RoutingException(it) }
        if (waypoints.size < 2) throw RoutingException("FakeRoutingEngine: a route needs two points")
        val pts = ArrayList<GeoPoint>()
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]; val b = waypoints[i + 1]
            val f = detour(a, b)
            if (f > 1.0) {
                val k = kotlin.math.sqrt(f * f - 1) / 2
                val lonToLat = kotlin.math.cos(Math.toRadians(a.lat))
                val mid = GeoPoint((a.lat + b.lat) / 2 - (b.lon - a.lon) * k * lonToLat, (a.lon + b.lon) / 2 + (b.lat - a.lat) * k / lonToLat)
                straight(a, mid, pts); straight(mid, b, pts)
            } else straight(a, b, pts)
        }
        pts += waypoints.last()
        val d = Geo.length(pts)
        return RouteResult(pts, d, 0.0, 0.0, d / 1.2, matched = waypoints.toList())
    }

    private fun straight(a: GeoPoint, b: GeoPoint, out: MutableList<GeoPoint>) {
        val n = maxOf(1, (Geo.distance(a, b) / stepM).toInt())
        for (k in 0 until n) out += GeoPoint(a.lat + (b.lat - a.lat) * k / n, a.lon + (b.lon - a.lon) * k / n)
    }
}
