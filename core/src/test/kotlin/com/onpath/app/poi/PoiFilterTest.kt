package com.onpath.app.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The POI map filter (test audit G15: the five small tests became one stepped test; every check kept). */
class PoiFilterTest {
    @Test fun poiFilterHidesKindsFromTheLayer() {
        val known = listOf("hut", "bus", "cafe")
        val orig = """["in",["get","kind"],["literal",["hut","bus"]]]"""
        assertEquals("nothing hidden", orig, PoiFilter.layerFilter(orig, known, emptySet()))
        assertNull("nothing hidden, no original", PoiFilter.layerFilter(null, known, emptySet()))
        assertEquals("only an unknown kind hidden", orig, PoiFilter.layerFilter(orig, known, setOf("not-a-kind")))
        assertEquals("wraps the original", """["all",$orig,["in",["get","kind"],["literal",["hut","cafe"]]]]""",
            PoiFilter.layerFilter(orig, known, setOf("bus")))
        assertEquals("no original", """["in",["get","kind"],["literal",["cafe"]]]""", PoiFilter.layerFilter(null, known, setOf("hut", "bus")))
        assertEquals("hide all", """["all",$orig,["in",["get","kind"],["literal",[]]]]""", PoiFilter.layerFilter(orig, known, known.toSet()))
        val keys = PoiKinds.all.map { it.key }.toSet()
        PoiKinds.defaultHidden.forEach { assertTrue("default hidden $it is not a known kind", it in keys) }
    }
}
