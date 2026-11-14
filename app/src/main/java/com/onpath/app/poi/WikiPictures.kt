package com.onpath.app.poi

import com.onpath.app.debug.Problems
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The picture of a POI or peak from Wikipedia – an online extra (owner 2026-11-14: "offline as the base, online for
 * pictures"). Only when Settings → POIs → "Bilder von Wikipedia" is on and the phone is online; the map and the details
 * sheet never wait for it and work the same without it.
 *
 * Which article: the POI's own `wikipedia` tag ("de:Zugspitze") when it has one, else a Wikipedia geosearch around the
 * POI whose article title is the POI's name ("Pendling" or "Pendling (Berg)"). What leaves the phone: the name or the
 * position rounded to 3 decimals (~100 m) – listed in Settings → Privacy. Pure parts are unit tested ([WikiPicturesTest]).
 */
object WikiPictures {
    /** A found picture: the thumbnail to show and the article it belongs to (tap → open, the credit). */
    data class Picture(val thumbUrl: String, val articleUrl: String, val title: String)

    const val THUMB_PX = 640

    /** "de:Zugspitze" → ("de", "Zugspitze"); a tag without a language uses [defaultLang]. */
    fun fromTag(tag: String?, defaultLang: String): Pair<String, String>? {
        val t = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val i = t.indexOf(':')
        return if (i in 2..3 && t.substring(0, i).all { it.isLetter() }) t.substring(0, i).lowercase(Locale.ROOT) to t.substring(i + 1).trim()
        else defaultLang to t
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun base(lang: String) =
        "https://$lang.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
            "&prop=pageimages%7Cinfo&inprop=url&piprop=thumbnail&pithumbsize=$THUMB_PX"

    fun titleUrl(lang: String, title: String) = base(lang) + "&titles=" + enc(title)

    /** Position rounded to 3 decimals (~100 m) – enough to find the article, not the exact spot. */
    fun geoUrl(lang: String, lat: Double, lon: Double, radiusM: Int) = base(lang) +
        "&generator=geosearch&ggslimit=30&ggsradius=$radiusM&ggscoord=" +
        String.format(Locale.ROOT, "%.3f%%7C%.3f", lat, lon)

    /** Search radius: a summit's article point can be off by some hundred metres, a hut's is close. */
    fun radiusFor(kind: String): Int = if (kind == "peak" || kind == "saddle") 1500 else 500

    /** Lower case, accents kept, "-" and spaces equal; for comparing a POI name with an article title. */
    fun norm(s: String): String = s.lowercase(Locale.ROOT).replace('-', ' ').replace(Regex("\\s+"), " ").trim()

    /** True when [title] is the article of [name]: equal, or equal before a bracket ("Pendling (Berg)"). */
    fun titleMatches(title: String, name: String): Boolean {
        val t = norm(title); val n = norm(name)
        return t == n || t.substringBefore(" (").trim() == n
    }

    /**
     * The picture in an API answer: with [name] only a page whose title matches it (geosearch), without the first page
     * that has a thumbnail (an article asked for by its title). null when none / unreadable.
     */
    fun parse(json: String, name: String?): Picture? = try {
        val pages = JSONObject(json).optJSONObject("query")?.optJSONArray("pages")
        var found: Picture? = null
        if (pages != null) for (i in 0 until pages.length()) {
            val p = pages.optJSONObject(i) ?: continue
            val title = p.optString("title")
            if (name != null && !titleMatches(title, name)) continue
            val thumb = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.startsWith("https://") } ?: continue
            val url = p.optString("fullurl").takeIf { it.startsWith("https://") } ?: continue
            found = Picture(thumb, url, title); break
        }
        found
    } catch (e: Exception) {
        Problems.info("poi", "Wikipedia answer not readable; no picture", e); null
    }

    /** Requests in the app language; German texts → de.wikipedia, else en. */
    fun lang(): String = if (Poi.isGerman()) "de" else "en"

    /** Found pictures by POI id (null = looked, none) – a sheet opened again does not ask again. */
    private val cache = object : LinkedHashMap<String, Picture?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Picture?>?) = size > 200
    }

    /** Online, off the main thread: the picture of [poi] or null. Never throws. */
    fun find(poi: Poi, userAgent: String): Picture? {
        synchronized(cache) { if (cache.containsKey(poi.id)) return cache[poi.id] }
        val name = poi.name ?: return null
        val lang = lang()
        val pic = try {
            fromTag(poi.wikipedia, lang)?.let { (l, t) -> parse(get(titleUrl(l, t), userAgent), null) }
                ?: parse(get(geoUrl(lang, poi.lat, poi.lon, radiusFor(poi.kind)), userAgent), name)
        } catch (e: Exception) {
            Problems.info("poi", "Wikipedia picture not loaded (offline or service not reachable)", e)
            return null // not cached: the next opening tries again
        }
        synchronized(cache) { cache[poi.id] = pic }
        return pic
    }

    private fun get(u: String, userAgent: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    /** The thumbnail as a bitmap (at most [maxBytes]); null when it cannot be loaded. Never throws. */
    fun loadBitmap(url: String, userAgent: String, maxBytes: Int = 3 * 1024 * 1024): android.graphics.Bitmap? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", userAgent)
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw java.io.IOException("picture larger than $maxBytes bytes")
                }
                out.toByteArray()
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        Problems.info("poi", "Wikipedia picture not downloaded", e); null
    }

    /** Wikimedia asks every client for a User-Agent that says who it is. */
    fun userAgent(version: String) = "MyPath-Android/$version (personal hiking app; https://github.com/LordTriangel/OnPath)"
}
