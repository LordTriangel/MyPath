package com.onpath.web

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.onpath.app.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Compose stand-in's SnapshotStateList (web/engine/src/main/kotlin/androidx/compose/runtime): a file of :state that
 * names the type – PlanDocument's `val points: SnapshotStateList<GeoPoint> = mutableStateListOf<GeoPoint>().apply { … }` –
 * compiles for the planner and the list behaves as a list.
 */
class StateListStandInTest {
    /** PlanDocument's shape, line for line. */
    private class Doc(stored: List<GeoPoint>) {
        val points: SnapshotStateList<GeoPoint> = mutableStateListOf<GeoPoint>().apply { addAll(stored) }
        var saved: List<GeoPoint> = emptyList()
        fun stored() { saved = points.toList() }
    }

    private val a = GeoPoint(46.67, 11.16)
    private val b = GeoPoint(46.70, 11.20)
    private val c = GeoPoint(46.72, 11.25)

    @Test fun aPlanDocumentShapedFileCompilesAndItsPointsAreAList() {
        val d = Doc(listOf(a, b))
        assertEquals(listOf(a, b), d.points)
        d.points.add(1, c); d.points[0] = b; d.points.removeAt(2)
        assertEquals(listOf(b, c), d.points)
        d.stored()
        d.points.clear()
        assertEquals(listOf(b, c), d.saved) // toList() is a copy
        assertTrue(d.points.isEmpty())
    }

    @Test fun theFactoriesAndListBehaviour() {
        val l = mutableStateListOf(a, b, c)
        assertEquals(3, l.size); assertEquals(1, l.indexOf(b)); assertTrue(c in l)
        assertEquals(listOf(b, c), l.subList(1, 3))
        l.removeAll { it == a }
        assertEquals(listOf(b, c), l.toList())
        val m = listOf(a, b).toMutableStateList()
        assertEquals(listOf(a, b), m.toList())
        assertNotSame(m, listOf(a, b).toMutableStateList())
        // as in Compose: identity, not contents
        assertNotEquals(m, listOf(a, b).toMutableStateList())
        val it = m.listIterator(); it.next(); it.set(c)
        assertEquals(listOf(c, b), m.toList())
    }
}
