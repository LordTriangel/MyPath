package com.onpath.app.routing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The way-type / surface rules shared by the app's plan sheet and the web planner (pure: no Android, no Compose UI). */
class BreakdownRulesTest {
    private fun <T> inGerman(block: () -> T): T {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try { return block() } finally { Locale.setDefault(before) }
    }

    @Test fun smallSharesFoldIntoOther() {
        val f = BreakdownRules.foldSmall(listOf(Share("path", 9000.0), Share("road", 800.0), Share("steps", 100.0), Share("ferry", 100.0)))
        assertEquals(listOf(Share("path", 9000.0), Share("road", 800.0), Share("other", 200.0)), f)
    }

    @Test fun emptyStaysEmpty() = assertEquals(emptyList<Share>(), BreakdownRules.foldSmall(emptyList()))

    @Test fun summaryLineNamesLargestShares() = assertEquals(
        "Path 61 % · Natural trail 52 %",
        BreakdownRules.summaryLine(listOf(Share("track", 3000.0), Share("path", 6100.0), Share("road", 900.0)),
            listOf(Share("natural_trail", 5200.0), Share("gravel", 4800.0))))

    @Test fun summaryLineInGerman() = assertEquals("Radweg 92 % · Asphalt 98 %", inGerman {
        BreakdownRules.summaryLine(listOf(Share("cycleway", 9200.0), Share("road", 800.0)),
            listOf(Share("asphalt", 9800.0), Share("gravel", 200.0)))
    })

    @Test fun summaryLineEdgeCases() {
        assertEquals("", BreakdownRules.summaryLine(emptyList(), emptyList()))
        assertEquals("Asphalt 100 %", BreakdownRules.summaryLine(emptyList(), listOf(Share("asphalt", 10.0))))
        assertEquals("", BreakdownRules.summaryLine(listOf(Share("road", 0.0)), emptyList()))
        // a key the table does not know: its name with spaces
        assertEquals("mud bath 75 %", BreakdownRules.summaryLine(listOf(Share("mud_bath", 3.0), Share("road", 1.0)), emptyList()))
        // 66.7 → 67, 12.5 → 13 (Math.round: half up)
        assertEquals("Road 67 %", BreakdownRules.summaryLine(listOf(Share("road", 2.0), Share("path", 1.0)), emptyList()))
        assertEquals("Road 13 %", BreakdownRules.summaryLine(listOf(Share("road", 1.0)) + List(7) { Share("x$it", 1.0) }, emptyList()))
    }

    @Test fun tablesInOrderWithColours() {
        assertEquals(listOf("cycleway", "path", "footway", "track", "quiet_road", "road", "busy_road", "steps", "ferry"),
            BreakdownRules.WAY.map { it.key })
        assertEquals(listOf("asphalt", "paved", "gravel", "unpaved", "ground", "natural_trail", "alpine", "unknown"),
            BreakdownRules.SURFACE.map { it.key })
        assertEquals("#3B8E3B", BreakdownRules.entry(BreakdownRules.Kind.WAY, "cycleway").hex)
        assertEquals("#E6E6E6", BreakdownRules.entry(BreakdownRules.Kind.SURFACE, "unknown").hex)
        assertEquals(BreakdownRules.OTHER, BreakdownRules.entry(BreakdownRules.Kind.WAY, "other"))
        assertEquals(BreakdownRules.OTHER, BreakdownRules.entry(BreakdownRules.Kind.SURFACE, "cycleway"))
        assertEquals("#D5D5D5", BreakdownRules.OTHER.hex)
        assertEquals("Sonstige", inGerman { BreakdownRules.OTHER.text })
    }

    /**
     * Risk: a swapped colour or label of a way type / surface shows wrong bars in the app AND the planner (both draw
     * from these tables) and no other check sees it – the screenshot of the breakdown draws only 8 of the 18 entries.
     * The rows are the tables as frozen in the former ui/BreakdownTest (2026-10-31).
     */
    @Test fun everyEntryKeepsItsColourAndItsLabel() {
        val want = mapOf(
        "cycleway" to ("#3B8E3B" to "plan_way_cycleway"),
        "path" to ("#8D5A3B" to "plan_way_path"),
        "footway" to ("#C08A5B" to "plan_way_footway"),
        "track" to ("#C9A64E" to "plan_way_track"),
        "quiet_road" to ("#9AA5B1" to "plan_way_quiet_road"),
        "road" to ("#5F6B78" to "plan_way_road"),
        "busy_road" to ("#C62828" to "plan_way_busy_road"),
        "steps" to ("#4E342E" to "plan_way_steps"),
        "ferry" to ("#2F86C9" to "plan_way_ferry"),
        "asphalt" to ("#546E7A" to "plan_surface_asphalt"),
        "paved" to ("#90A4AE" to "plan_surface_paved"),
        "gravel" to ("#D1B074" to "plan_surface_gravel"),
        "unpaved" to ("#A1887F" to "plan_surface_unpaved"),
        "ground" to ("#8D6E63" to "plan_surface_ground"),
        "natural_trail" to ("#6D8B3A" to "plan_surface_natural_trail"),
        "alpine" to ("#7B7F86" to "plan_surface_alpine"),
        "unknown" to ("#E6E6E6" to "plan_surface_unknown"),
        "other" to ("#D5D5D5" to "plan_other"),
        )
        val all = BreakdownRules.WAY + BreakdownRules.SURFACE + BreakdownRules.OTHER
        assertEquals(want.keys.toList(), all.map { it.key })
        all.forEach { e -> assertEquals(e.key, want.getValue(e.key), e.hex to e.label.key) }
    }
}
