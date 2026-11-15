package com.onpath.app.map

import com.onpath.app.data.AtomicFiles
import com.onpath.app.data.Tour
import com.onpath.app.debug.Problems
import com.onpath.app.routing.GeoPoint
import java.io.File

/**
 * The large overview's map pictures, kept on the phone (owner 2026-11-15: swiping through the tours „macht nur Sinn, wenn
 * die Tourgrafiken als Grafik auf dem Handy gespeichert werden“). One file per tour, by uuid, under the app's files
 * (`files/overviews/<uuid>.webp`, not in any backup – they are made again), plus `<uuid>.key`: the key the picture was made
 * for ([OverviewRules.key]: the line, the followed plan under it, the picture's size and the map theme) and where start and
 * goal lie on it. A file's time is its last use (the cap removes the oldest used, [OverviewRules.evict]). `size` keeps the
 * overview's picture size from its last opening, so the background makes pictures that fit. Reading never throws.
 */
class OverviewPictures(val dir: File) {
    /** A stored picture's key and where the route's ends lie on it (bitmap pixels). */
    class Meta(val key: String, val startX: Float, val startY: Float, val goalX: Float, val goalY: Float)

    fun file(uuid: String) = File(dir, "$uuid.webp")
    private fun metaFile(uuid: String) = File(dir, "$uuid.key")
    private val sizeFile get() = File(dir, "size")

    /** The stored picture's key and ends; null = none (or unreadable – made again). */
    fun meta(uuid: String): Meta? = try {
        metaFile(uuid).takeIf { it.isFile && file(uuid).isFile }?.readText()?.let { OverviewRules.parseMeta(it) }
    } catch (e: Exception) { Problems.info("routes", "Overview picture key of $uuid unreadable – made again", e); null }

    /** The stored picture's bytes when it was made for [key]; marks it used. Null = none / another key / unreadable. */
    fun bytes(uuid: String, key: String): Pair<Meta, ByteArray>? = try {
        val m = meta(uuid)?.takeIf { it.key == key }
        m?.let { meta ->
            val f = file(uuid)
            val b = f.readBytes()
            f.setLastModified(System.currentTimeMillis()) // last use: the cap keeps the pictures looked at
            meta to b
        }
    } catch (e: Exception) { Problems.info("routes", "Overview picture of $uuid unreadable – made again", e); null }

    /** Stores the picture [bytes] for [meta]. False on failure (reported; the overview draws it again next time). */
    fun write(uuid: String, meta: Meta, bytes: ByteArray): Boolean = try {
        dir.mkdirs()
        AtomicFiles.writeBytes(file(uuid), bytes)
        AtomicFiles.writeText(metaFile(uuid), OverviewRules.metaText(meta))
        true
    } catch (e: Exception) { Problems.info("routes", "Overview picture of $uuid not stored – drawn again next time", e); false }

    /** The stored pictures: uuid → last use (ms). */
    fun usage(): Map<String, Long> = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".webp") }
        .associate { it.name.removeSuffix(".webp") to it.lastModified() }

    /** Removes the picture of [uuid] (both files). */
    fun remove(uuid: String) {
        // expected: a file already gone is fine – nothing to remove
        file(uuid).delete(); metaFile(uuid).delete()
    }

    /** The overview's picture size at its last opening (width px, height px, padding px); null = never opened yet. */
    fun size(): Triple<Int, Int, Int>? = try {
        sizeFile.takeIf { it.isFile }?.readText()?.trim()?.split(' ')?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 3 && it[0] > 0 && it[1] > 0 }?.let { Triple(it[0], it[1], it[2]) }
    } catch (e: Exception) { Problems.info("routes", "Overview picture size unreadable – the background waits for the next opening", e); null }

    fun setSize(w: Int, h: Int, pad: Int) {
        if (size() == Triple(w, h, pad)) return
        try { dir.mkdirs(); AtomicFiles.writeText(sizeFile, "$w $h $pad") }
        catch (e: Exception) { Problems.info("routes", "Overview picture size not stored – the background waits", e) }
    }
}

/** Which overview pictures to keep and make, and their key (JVM tested). */
object OverviewRules {
    /** Raised when the picture's look changes: every picture is made again once. */
    const val VERSION = 1

    /** The background makes pictures for the newest this many tours … */
    const val NEWEST = 20

    /** … plus every favourite; the folder keeps at most this many (oldest used out) – wanted ones are never taken out. */
    const val CAP = 40

    /**
     * The key of a picture: [lineKey] ([ThumbRules.key] – the line), the followed plan drawn under it ([under]), the
     * picture's size in px and the map [theme]. Any of them changing makes the picture anew.
     */
    fun key(lineKey: String, under: List<GeoPoint>, w: Int, h: Int, theme: String): String =
        "o$VERSION ${w}x$h $theme|" + underSig(under) + "|" + lineKey

    private fun underSig(under: List<GeoPoint>): String = if (under.size < 2) "u0" else
        "u${under.size}:" + "%.6f".format(java.util.Locale.ROOT, under.sumOf { it.lat }) + ":" +
            "%.6f".format(java.util.Locale.ROOT, under.sumOf { it.lon })

    fun metaText(m: OverviewPictures.Meta) = m.key + "\n" + listOf(m.startX, m.startY, m.goalX, m.goalY).joinToString(" ")

    /** The key file's text read back; null = not a key file of this version (made again). */
    fun parseMeta(text: String): OverviewPictures.Meta? {
        val lines = text.trim().lines()
        if (lines.size < 2 || lines[0].isBlank()) return null
        val n = lines[1].trim().split(' ').mapNotNull { it.toFloatOrNull() }
        if (n.size != 4) return null
        return OverviewPictures.Meta(lines[0].trim(), n[0], n[1], n[2], n[3])
    }

    /**
     * The tours the background makes pictures for, in this order: the newest [newest] of My routes (done, with a uuid, not a
     * draft, not in the recycle bin), then every other favourite, newest first.
     */
    fun wanted(tours: List<Tour>, newest: Int = NEWEST): List<Tour> {
        val ok = tours.filter { it.state == "done" && it.uuid.isNotEmpty() && !it.draft && it.deletedMs == null }
            .sortedByDescending { it.createdMs }
        val first = ok.take(newest)
        val ids = first.mapTo(HashSet()) { it.uuid }
        return first + ok.filter { it.favourite && it.uuid !in ids }
    }

    /**
     * The pictures to remove from [usage] (uuid → last use): those of tours no longer on the phone ([keep] = uuids in My
     * routes + the recycle bin), then the oldest used beyond [cap] – never one of [wanted] (a wanted picture taken out
     * would only be made again).
     */
    fun evict(usage: Map<String, Long>, keep: Set<String>, wanted: Set<String>, cap: Int = CAP): List<String> {
        val gone = usage.keys.filter { it !in keep }
        val left = usage.filterKeys { it in keep }
        val over = left.size - cap
        if (over <= 0) return gone
        val extra = left.entries.filter { it.key !in wanted }.sortedBy { it.value }.take(over).map { it.key }
        return gone + extra
    }
}
