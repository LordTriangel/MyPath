package com.onpath.app.suggest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * The small pieces of the tour-suggestion core against the POC's own output (fixtures by
 * tools/suggest-poc/port/fixtures.py): the suggest file reader, Python's set order, the character, the settings file.
 * The whole method's parity with the frozen POC on real data is tools/suggest-poc/port/parity.sh (README there).
 */
class SuggestCoreTest {
    private fun res(name: String) = File("src/test/resources/suggest/$name")
    private val settings by lazy { SuggestSettings.parse(File("../tools/suggest-poc/settings.json").readText()) }

    @Test fun readsTheFormatOfSuggestFilePy() {
        val f = SuggestFile.read(res("tiny-suggest.bin").readBytes())
        assertEquals(1, f.version)
        assertEquals(SuggestFile.CONTENT, f.content)
        assertEquals(SuggestFile.HAS_HEIGHTS or SuggestFile.HAS_ROUTELINES or SuggestFile.HAS_LOOPS, f.flags)
        assertEquals(listOf(531, 342, 5, 4), listOf(f.x, f.y, f.w, f.h))
        assertEquals(6.6796875, f.west, 0.0)
        assertEquals(51.17934, f.north, 0.0)
        assertEquals("2026-11-15", f.osmDate)
        assertArrayEquals(ByteArray(20) { it.toByte() }, f.grid)
        assertEquals(listOf("", "Ohligser Heide"), f.labels)
        val nh = SuggestFile.NO_HEIGHT.toInt()
        assertEquals(listOf(nh, 1200, 1210, -5, 0, 3, 2, nh, 32000, -32000) + listOf(nh, 1200, 1210, -5, 0, 3, 2, nh, 32000, -32000),
            f.heights!!.map { it.toInt() })
        assertEquals("Köln", f.places[0].name); assertEquals("city", f.places[0].cls)
        assertEquals(6.95, f.places[0].lon, 0.0)
        val r = f.routes.single()
        assertEquals(listOf("mtb", "X30 · Neandertalweg", "2", "true"), listOf(r.kind, r.label, r.net.toString(), r.trail.toString()))
        assertArrayEquals(doubleArrayOf(6.9, 51.1, 6.900001, 51.099999, -0.5, -70.25), r.lines[0], 0.0)
        assertEquals("Elbsee", f.lakes[0].name)
        assertEquals("ruins", f.sights[0].kind); assertEquals("", f.sights[0].name)
        val (a, b) = f.loops!!
        assertEquals(12290089L, a.id); assertEquals("bicycle", a.kind); assertEquals(3, a.t)
        assertNull(a.parkingM); assertEquals(0, a.ascent); assertEquals("Rund um Haßloch", a.name)
        assertEquals(17641.24, a.m, 1e-9)
        assertArrayEquals(doubleArrayOf(6.9, 51.1, 6.91, 51.1, 6.9, 51.1), a.line, 0.0)
        assertEquals(12, b.parkingM); assertNull(b.ascent); assertEquals(0, b.line.size)
    }

    @Test fun skipsUnknownSectionsAndRefusesNewerFormats() {
        val data = res("tiny-suggest.bin").readBytes()
        val inf = Inflater(); inf.setInput(data, 8, data.size - 8)
        val body = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inf.finished()) body.write(buf, 0, inf.inflate(buf))
        inf.end()
        body.write("NEW!".toByteArray()); body.write(byteArrayOf(3, 0, 0, 0)); body.write("abc".toByteArray())
        val def = Deflater(); def.setInput(body.toByteArray()); def.finish()
        val z = ByteArrayOutputStream()
        while (!def.finished()) z.write(buf, 0, def.deflate(buf))
        def.end()
        val more = data.copyOf(8) + z.toByteArray()
        assertEquals("Rund um Haßloch", SuggestFile.read(more).loops!![0].name)
        val newer = data.copyOf(); newer[4] = 2
        try { SuggestFile.read(newer); fail("a newer format must be refused") } catch (e: SuggestFile.NotASuggestFile) { /* expected */ }
        try { SuggestFile.read(data.copyOf(40)); fail("half a file must be refused") } catch (e: SuggestFile.NotASuggestFile) { /* expected */ }
        assertNull(SuggestFile.versionOf("PK\u0003\u0004xxxx".toByteArray()))
    }

    @Test fun iteratesLikePythonsSet() {
        val lines = res("pyset-order.txt").readLines().filter { !it.startsWith("#") }
        var cases = 0
        for (i in lines.indices step 2) {
            val ops = lines[i].substringAfter(' ').split(' ').filter { it.isNotEmpty() }
            val want = lines[i + 1].removePrefix("R").trim().split(' ').filter { it.isNotEmpty() }.map { it.toInt() }
            val a = PyIntSet()
            var b: PyIntSet? = null
            for (op in ops) when (op[0]) {
                'a' -> a.add(op.substring(1).toInt())
                'r' -> a.remove(op.substring(1).toInt())
                'u' -> b = PyIntSet().also { s -> op.substring(1).split(',').filter { it.isNotEmpty() }.forEach { s.add(it.toInt()) } }
            }
            val got = if (lines[i].startsWith("UNION")) PyIntSet().also { it.addAll(b!!); it.addAll(a) } else a
            assertEquals("case ${i / 2}: ${lines[i].take(80)}", want, got.toIntArray().toList())
            cases++
        }
        assertEquals(60, cases)
    }

    @Test fun characterAsThePoc() {
        val lines = res("character.txt").readLines().filter { !it.startsWith("#") }
        for (i in lines.indices step 4) {
            val body = SuggestSettings.parse(lines[i].removePrefix("WISH ")).root
            val asc = body["ascent"]
            val w = Wish.of(settings, 6.9, 51.1, body["activity"].v as String?, (body["t_level"].v as Double?)?.toInt(),
                if (body["hours"].isList) body["hours"].doubles() else doubleArrayOf(body["hours"].d), null,
                if (asc.isNull) null else arrayOf(asc[0].v as Double?, asc[1].v as Double?),
                body["natur"].v as String?, body["ruhe"].v as String?, body["bekannt"].v as String?, body["wasser"].v == true)
            val ch = Character(w, settings)
            val r = lines[i + 1].removePrefix("R ").split(' ').map { it.toDouble() }
            assertEquals(r, listOf(ch.wild, ch.depth, ch.signed, ch.minM, ch.maxM, ch.usualUp, ch.up[0], ch.up[1], ch.hours[0], ch.hours[1]))
            val axes = lines[i + 2].removePrefix("AXES ").split(' ').associate { it.substringBefore('=') to it.substringAfter('=').toDouble() }
            assertEquals(axes, ch.axes)
            assertEquals(lines[i + 3].removePrefix("GROUND ").split(' ').map { it.toDouble() }, ch.ground.toList())
        }
    }

    @Test fun settingsAreTheOneFile() {
        assertTrue(settings["version"].s.startsWith("v2"))
        assertEquals(0.8, settings["place"]["top"]["natur"].d, 0.0)
        assertTrue(settings["family"]["foot"]["coarse"].isNull)
        assertEquals("touring", settings["activities"]["bike"]["profile"].s)
    }

    @Test fun pythonsNumberRules() {
        assertEquals(0.2, SuggestGeo.pyRound(0.25, 1), 0.0)
        assertEquals(680.0, SuggestGeo.pyRound(675.0, -1), 0.0)
        assertEquals(2, SuggestGeo.pyRoundInt(2.5))
        assertEquals(350.0, SuggestGeo.pyMod(-10.0, 360.0), 0.0)
        assertEquals("1,8", SuggestGeo.comma(SuggestGeo.pyRound(1.75, 1)))
        assertEquals("3.0", SuggestGeo.pyStr(3.0))
        assertEquals("Westen", SuggestGeo.direction(7.0, 51.0, 6.9, 51.0))
        assertEquals("Hirschpark", Score.short("Hirschpark (Nordkirchen) mit Umgebung"))
        assertEquals("am Elbsee", Score.am("Elbsee"))
        assertEquals("an der Dhünntalsperre", Score.am("Dhünntalsperre"))
        assertEquals("X30 Neandertalweg", Named.label("[Hilden] X30 Neandertalweg - Teilstrecke 2"))
    }
}
