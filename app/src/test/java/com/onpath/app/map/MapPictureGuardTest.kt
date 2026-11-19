package com.onpath.app.map

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Field report on 0.18.1 (owner 2026-11-19): My routes' pictures showed the line on an empty map. A picture drawn while
 * its map folder was replaced (map areas joined, the old generation deleted) is not kept: drawn again on the map as it
 * is then, or none now.
 */
class MapPictureGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Map(var dir: File) {
        val styles = mutableListOf<String>()
        fun style() = MapPictureGuard.Style("style of ${dir.name}", dir).also { styles += it.json }
    }

    @Test fun aPictureOnTheMapAsItStillIsIsKept() = runBlocking {
        val m = Map(tmp.newFolder("g1"))
        val dropped = mutableListOf<String>()
        val out = MapPictureGuard.drawn(null, { m.style() }, { m.dir }, { json -> "picture on $json" }, { dropped += it })
        assertEquals("picture on style of g1", out)
        assertEquals(emptyList<String>(), dropped)
    }

    @Test fun theMapReplacedWhileDrawingTheFirstIsDroppedAndDrawnOnTheNewMap() = runBlocking {
        val m = Map(tmp.newFolder("g1"))
        val g2 = tmp.newFolder("g2")
        val dropped = mutableListOf<String>()
        var n = 0
        val out = MapPictureGuard.drawn(m.style(), { m.style() }, { m.dir }, { json ->
            // the first snapshot: the cells are joined meanwhile, g1 goes
            if (n++ == 0) { m.dir.deleteRecursively(); m.dir = g2 }
            "picture on $json"
        }, { dropped += it })
        assertEquals("picture on style of g2", out)
        assertEquals(listOf("picture on style of g1"), dropped)
    }

    @Test fun aFolderGoneUnderThePictureIsNeverKept() = runBlocking {
        val g1 = tmp.newFolder("g1")
        val m = Map(g1)
        // the same folder named, but deleted (pruned) while drawing – and again on the second try
        val out = MapPictureGuard.drawn(null, { m.style() }, { m.dir }, { json -> g1.deleteRecursively(); "picture on $json" })
        assertNull(out)
        assertEquals(2, m.styles.size)
    }

    @Test fun noStyleOrNoPictureIsNone() = runBlocking {
        assertNull(MapPictureGuard.drawn<String>(null, { null }, { null }, { "x" }))
        val m = Map(tmp.newFolder("g1"))
        assertNull(MapPictureGuard.drawn<String>(null, { m.style() }, { m.dir }, { null }))
    }
}
