package com.onpath.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapServerTest {
    @Test fun emptyKeyIsNotSet() = assertEquals("not set", MapServer.maskKey(""))

    @Test fun shortKeyIsNeverShown() = assertEquals("set", MapServer.maskKey("abc123"))

    @Test fun longKeyShowsOnlyTheLastFour() {
        val key = "k3y-0123456789abcdef"
        val m = MapServer.maskKey(key)
        assertEquals("set (…cdef)", m)
        assertFalse(m.contains("0123456789"))
    }

    @Test fun describeLine() = assertEquals("test · https://maps-test.example/v1 · key not set",
        MapServer.describe("test", "https://maps-test.example/v1", ""))

    @Test fun myPathBuildsNameNoMapServerByDefault() {
        // unit tests run against the debug build type; MyPath ships without the original's map server
        assertEquals("test", MapServer.environment)
        assertEquals("", MapServer.apiUrl)
        assertEquals("test · no map server (own map packages only)", MapServer.describe("test", "", ""))
        assertTrue(MapServer.describe().contains("no map server"))
    }
}
