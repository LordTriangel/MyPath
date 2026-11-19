package com.onpath.app.search

import com.onpath.app.poi.PoiFilter
import com.onpath.app.poi.PoiKinds
import com.onpath.app.search.SearchText.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Search rules as tables (test audit G15, replaces PoiFilterTest, SearchCategoryTest, SearchNotesTest, SearchTextTest,
 * SharedRecentTest): the text normalisation and word match, the POI map filter, the six chips (owner 2026-10-31), the
 * two owner decisions in search notes, and "a place shared into OnPath goes to Recent" (owner 2026-11-02).
 */
class SearchRulesTest {

    // ---- search text ----

    @Test fun normaliseStripsAccentsAndPunctuation() {
        val table = listOf(
            "Meraner Hütte" to "meraner hutte",
            "Weißkugel" to "weisskugel",
            "Pippo’s Mountain Lodge" to "pippos mountain lodge",
            "Rifugio Città di Merano" to "rifugio citta di merano",
            "Hohe Weiße - Cima Bianca" to "hohe weisse cima bianca",
            null to "",
        )
        for ((raw, want) in table) assertEquals("normalize($raw)", want, SearchText.normalize(raw))
        assertEquals(listOf("st", "leonhard"), SearchText.tokens("St. Leonhard"))
    }

    @Test fun wordMatchTable() {
        // query, word, match (exact / prefix / inside the word / one typo for long words / none)
        val table = listOf(
            Triple("meran", "meran", Match.EXACT),
            Triple("mer", "meraner", Match.PREFIX),
            Triple("joch", "hochjoch", Match.INFIX),
            Triple("jo", "hochjoch", Match.NONE),            // too short for inside-word
            Triple("merna", "meran", Match.TYPO),            // swap
            Triple("rifugo", "rifugio", Match.TYPO),         // missing letter
            Triple("huette", "hutte", Match.TYPO),           // "ue" spelling
            Triple("hochganh", "hochganghaus", Match.TYPO),  // typo in a prefix
            Triple("mera", "mira", Match.NONE),              // 4 letters: no typo tolerance
            Triple("merxxn", "meran", Match.NONE),           // two edits
        )
        for ((q, w, want) in table) assertEquals("matchWord($q, $w)", want, SearchText.matchWord(q, w))
    }

    @Test fun osaDistanceAtMostOne() {
        val table = listOf(
            Triple("abcde", "abdce", true), Triple("abcde", "abcd", true), Triple("abcd", "xabcd", true),
            Triple("abcde", "badce", false), Triple("abc", "abcde", false),
        )
        for ((a, b, want) in table) assertEquals("osaAtMostOne($a, $b)", want, SearchText.osaAtMostOne(a, b))
    }

    @Test fun kindWords() {
        for ((word, kind) in listOf("hutte" to "hut", "rifugio" to "hut", "bahnh" to "train" /* prefix of bahnhof */, "station" to "lift"))
            assertTrue("$word → $kind", kind in SearchText.kindsFor(word))
        assertTrue(SearchText.kindsFor("ba").isEmpty())
    }

    // ---- POI filter ----

    @Test fun poiFilterHidesKindsFromTheLayer() {
        val known = listOf("hut", "bus", "cafe")
        val orig = """["in",["get","kind"],["literal",["hut","bus"]]]"""
        // nothing hidden (or only an unknown kind): the original stays
        assertEquals(orig, PoiFilter.layerFilter(orig, known, emptySet()))
        assertNull(PoiFilter.layerFilter(null, known, emptySet()))
        assertEquals(orig, PoiFilter.layerFilter(orig, known, setOf("not-a-kind")))
        // wraps an original filter; builds its own without one
        assertEquals("""["all",$orig,["in",["get","kind"],["literal",["hut","cafe"]]]]""", PoiFilter.layerFilter(orig, known, setOf("bus")))
        assertEquals("""["in",["get","kind"],["literal",["cafe"]]]""", PoiFilter.layerFilter(null, known, setOf("hut", "bus")))
        // hide all: an empty list
        assertEquals("""["all",$orig,["in",["get","kind"],["literal",[]]]]""", PoiFilter.layerFilter(orig, known, known.toSet()))
        // the kinds hidden by default exist
        val keys = PoiKinds.all.map { it.key }.toSet()
        PoiKinds.defaultHidden.forEach { assertTrue("$it is not a known kind", it in keys) }
    }

    // ---- the six chips (owner 2026-10-31) ----

    /** Every kind a search result can have: the POI kinds plus the search-only ones. */
    private val searchable = PoiKinds.all.map { it.key }.toSet() +
        setOf("peak", "saddle", "place", "trail", "address", "street", "store", "other")

    @Test fun theOwnersSixChips() {
        // every kind in at most one chip; every chip names only kinds that exist
        for (k in searchable) {
            val chips = SearchKinds.Category.entries.filter { k in it.kinds }
            assertTrue("$k is in ${chips.size} chips: $chips", chips.size <= 1)
            assertEquals(chips.firstOrNull(), SearchKinds.Category.of(k))
        }
        for (c in SearchKinds.Category.entries) for (k in c.kinds) assertTrue("$c: unknown kind $k", k in searchable)
        assertEquals(listOf("PLACES", "FOOD", "SIGHTS", "WATER", "TRANSIT", "PARKING"), SearchKinds.Category.entries.map { it.name })
        assertEquals(listOf("Places", "Food", "Sights", "Water", "Transit", "Parking"), SearchKinds.Category.entries.map { it.label })
        assertTrue(SearchKinds.Category.entries.all { ' ' !in it.label })
        // what the owner listed per chip
        val table = listOf(
            "street" to SearchKinds.Category.PLACES, "address" to SearchKinds.Category.PLACES,
            "hut" to SearchKinds.Category.FOOD, "bar" to SearchKinds.Category.FOOD /* beer gardens */,
            "peak" to SearchKinds.Category.SIGHTS, "waterfall" to SearchKinds.Category.SIGHTS,
            "spring" to SearchKinds.Category.WATER, "lift" to SearchKinds.Category.TRANSIT,
            "parking" to SearchKinds.Category.PARKING, "toilets" to null,
        )
        for ((kind, chip) in table) assertEquals("chip of $kind", chip, SearchKinds.Category.of(kind))
    }

    @Test fun placesChipFindsStreetsAndAddresses() {
        val street = Place("a:x:1", "Mittelstraße · Hilden", "street", 51.166, 6.94, source = Place.Source.MAP)
        val addr = AddressHit(street, 1.0, 2)
        val idx = OfflineIndex(listOf(OfflineIndex.Entry(Place("n1", "Mittelalm", "hut", 51.17, 6.95))))
        fun q(c: SearchKinds.Category?) = SearchQuery("mittel", c, 51.17, 6.94)
        assertEquals(listOf("Mittelstraße · Hilden"), idx.search(q(SearchKinds.Category.PLACES), listOf(addr)).map { it.place.name })
        assertEquals(listOf("Mittelalm"), idx.search(q(SearchKinds.Category.FOOD), listOf(addr)).map { it.place.name })
        assertEquals(2, idx.search(q(null), listOf(addr)).size)
    }

    // ---- owner decisions in search notes, and Recent ----

    @Test fun sharedPositionNoteOnlyWhenPositionedFromTheAddress() {
        assertEquals(SharedPosition.LINK, SharedPosition.of(point = true))
        assertNull(SharedPosition.of(point = true).note)
        // no coordinate at all: found by searching the address
        assertEquals("Position from the address", SharedPosition.of(point = false).note)
    }

    @Test fun recentsKeepVillageAndReadOldLines() {
        val p = Place("a:x:1:5", "Hauptstraße 5 · Völlan", "address", 46.61, 11.12, source = Place.Source.MAP,
            town = "Lana", village = "Völlan")
        val back = SearchStore.decode(SearchStore.encode(p))!!
        assertEquals("Völlan", back.village)
        assertEquals("Lana", back.municipality)
        // an older line (9 fields)
        val old = SearchStore.decode("n1\tMeraner Hütte\thut\t\t46.732000\t11.091000\t1960\t\tPOI")!!
        assertEquals("Meraner Hütte", old.name)
        assertNull(old.village); assertNull(old.municipality)
    }

    private val sharedPlace = Place("shared:51.78,7.54", "Restaurant Schloss", "other", 51.78, 7.54, address = "Schloßstraße 1, Nordkirchen")

    /** Owner 2026-11-02 ("zuletzt habe ich es ja gesucht"): a shared place goes to Recent too, with a share sign. */
    @Test fun theSharedMarkSurvivesTheStore() {
        val back = SearchStore.decode(SearchStore.encode(sharedPlace.copy(shared = true)))!!
        assertTrue(back.shared)
        assertEquals("Restaurant Schloss", back.name)
        assertFalse(SearchStore.decode(SearchStore.encode(sharedPlace))!!.shared)
        // an entry written before (11 fields) reads as not shared
        val old = SearchStore.encode(sharedPlace).split('\t').take(11).joinToString("\t")
        assertFalse(SearchStore.decode(old)!!.shared)
    }

    /** Source-text wiring check kept from SharedRecentTest (no behaviour test drives showSharedPlace; test-audit G15). */
    @Test fun aSharedPlaceIsRememberedAndShownWithTheShareSign() {
        val flow = File("src/main/java/com/onpath/app/ui/SearchFlow.kt").readText()
        val block = flow.substringAfter("fun showSharedPlace(").substringBefore("\n    }\n")
        assertTrue(block, block.contains("search.remember(p.copy(shared = true))"))
        val screen = File("src/main/java/com/onpath/app/ui/search/SearchScreen.kt").readText()
        assertTrue(screen.contains("if (h.place.shared) Icon(Icons.Outlined.Share"))
    }
}
