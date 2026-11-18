package com.onpath.app.data

import com.onpath.app.debug.Problems
import com.onpath.app.gpx.GpxWaypoint
import org.json.JSONArray
import org.json.JSONObject

/** The JSON columns of a tour (waypoints, pinned stops) – pure; [TourDb] and [TourPlan] use it. */
object TourJson {
    fun pinnedToJson(p: Set<Int>): String = JSONArray().apply { p.sorted().forEach { put(it) } }.toString()

    fun jsonToPinned(json: String?): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        return try { JSONArray(json).let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } } catch (e: Exception) {
            Problems.warn("db", "Stored pinned stops unreadable, dropped", e); emptySet()
        }
    }

    fun waypointsToJson(list: List<GpxWaypoint>): String = JSONArray().apply {
        list.forEach { w ->
            put(JSONObject().apply {
                put("lat", w.lat); put("lon", w.lon)
                w.name?.let { put("name", it) }
                w.ele?.let { put("ele", it) }
            })
        }
    }.toString()

    fun jsonToWaypoints(json: String?): List<GpxWaypoint> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                GpxWaypoint(
                    o.getDouble("lat"), o.getDouble("lon"),
                    if (o.has("name")) o.getString("name") else null,
                    if (o.has("ele")) o.getDouble("ele") else null,
                )
            }
        } catch (e: Exception) { Problems.warn("db", "Stored waypoint list unreadable, waypoints dropped", e); emptyList() }
    }

}
