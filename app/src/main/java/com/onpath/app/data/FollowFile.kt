package com.onpath.app.data

import com.onpath.app.debug.Problems
import com.onpath.app.routing.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * `follow.json`: the plain line being followed and its name (the service reloads it after a restart; the app shows it
 * at start); read and written through `navigation/FollowStore` only. Never throws: a broken file = nothing followed, a failed write is reported.
 */
class FollowFile(private val file: File) {

    fun save(name: String, points: List<GeoPoint>?) {
        try {
            if (points == null) { file.delete(); return }
            val arr = JSONArray()
            val ele = JSONArray()
            points.forEach { arr.put(it.lat); arr.put(it.lon); ele.put(it.ele ?: JSONObject.NULL) }
            AtomicFiles.writeText(file, JSONObject().put("name", name).put("pts", arr).put("ele", ele).toString(),
                File(file.path + ".tmp"))
        } catch (e: Exception) { Problems.error("data", "Followed line not saved – lost after a restart", e) }
    }

    fun load(): Pair<String, List<GeoPoint>>? = try {
        if (!file.exists()) null else {
            val o = JSONObject(file.readText())
            val a = o.getJSONArray("pts")
            val e = o.optJSONArray("ele") // optional (older files lack it): elevation per point for "remaining ascent"
            o.getString("name") to (0 until a.length() / 2).map {
                val h = e?.takeIf { arr -> it < arr.length() && !arr.isNull(it) }?.optDouble(it)
                GeoPoint(a.getDouble(it * 2), a.getDouble(it * 2 + 1), h?.takeIf { v -> !v.isNaN() })
            }
        }
    } catch (e: Exception) { Problems.warn("data", "Saved followed line unreadable, not restored", e); null }

    companion object { const val NAME = "follow.json" }
}
