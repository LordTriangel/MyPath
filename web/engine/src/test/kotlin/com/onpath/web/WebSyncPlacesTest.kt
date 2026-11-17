package com.onpath.web

import com.onpath.app.search.Place
import com.onpath.app.sync.SyncFormat
import com.onpath.app.sync.SyncLedger
import com.onpath.app.sync.SyncPlaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kept places ⇄ the sync in the planner (web-sync.md §5.4, web-planner.md §32): the page's calls over the shared
 * PlaceSync with WebKept's line codec – the same lines and item ids as the app, export before the round, import after.
 */
class WebSyncPlacesTest {
    private val web = SyncFormat.newDevice(ByteArray(SyncFormat.DEVICE_BYTES) { 2 })
    private val phone = SyncFormat.newDevice(ByteArray(SyncFormat.DEVICE_BYTES) { 1 })

    private val hut = WebKept.encode(Place("n:4711", "Leiter Alm", "alpine_hut", 46.689, 11.14, ele = 1522))
    private val hutOnline = WebKept.encode(Place("photon:N4711", "Leiter Alm", "alpine_hut", 46.68915, 11.14005, address = "Algund"))
    private val peak = WebKept.encode(Place("n:12", "Mutspitze", "peak", 46.7203, 11.1251, ele = 2295))
    private val home = WebKept.encode(Place("addr:1", "Hauptstraße 3", "address", 46.6712, 11.1500, village = "Algund"))

    private fun items(ledger: String) = Regex("\\{[^{}]*\\}").findAll(WebSync.placeItems(ledger)).map { it.value }.toList()
    private fun item(ledger: String, id: String) = items(ledger).firstOrNull { it.contains("\"id\":\"$id\"") }
    private fun homeOf(json: String) = Regex("\"home\":(null|\"(.*?)\"),").find(json)!!.groupValues[2].replace("\\u0009", "\t").ifEmpty { null }
    private fun favs(json: String) = Regex("\"favourites\":\\[(.*?)]").find(json)!!.groupValues[1]
        .split("\",\"").filter { it.isNotBlank() }.map { it.trim('"').replace("\\u0009", "\t") }

    @Test fun thePlannersLineIsTheAppsLineAndItem() {
        // SyncPlacesTest.theFormatIsFrozen: the same line, the same item id on both sides
        assertEquals("n:4711\tLeiter Alm\talpine_hut\t\t46.689000\t11.140000\t1522\t\tPOI\t\t\t", hut)
        assertEquals("COgvE3Dj8pbgW9pnl8DPxw", SyncPlaces.itemId(hut))
        val l = WebSync.placesExport("", "", hut, 1000.0, web, "")
        val i = item(l, "COgvE3Dj8pbgW9pnl8DPxw")!!
        assertTrue(i, i.contains("\"deleted\":false") && i.contains("\"dirty\":true") && i.contains("\"home\":false"))
    }

    @Test fun exportKeepsHomeAndFavouritesAndTombstonesWhatIsGone() {
        var l = WebSync.placesExport("", home, listOf(hut, peak).joinToString("\n"), 1000.0, web, "")
        assertEquals(3, items(l).size)
        assertTrue(item(l, SyncPlaces.HOME_ID)!!.contains("\"home\":true"))
        // nothing changed → the same text (nothing to send)
        assertEquals(l, WebSync.placesExport(l, home, listOf(hut, peak).joinToString("\n"), 2000.0, web, ""))
        // the hut no longer kept, Zuhause removed → their tombstones, carrying the line
        l = WebSync.placesExport(l, "", peak, 3000.0, web, "")
        val gone = item(l, SyncPlaces.itemId(hut))!!
        assertTrue(gone, gone.contains("\"deleted\":true") && gone.contains("Leiter Alm"))
        assertTrue(item(l, SyncPlaces.HOME_ID)!!.contains("\"deleted\":true"))
        assertTrue(item(l, SyncPlaces.itemId(peak))!!.contains("\"deleted\":false"))
    }

    @Test fun theSamePlaceFromTheSearchIsNoSecondItem() {
        val l = WebSync.placesExport("", "", hut, 1000.0, web, "")
        assertEquals(l, WebSync.placesExport(l, "", hutOnline, 2000.0, web, ""))
    }

    @Test fun waitingItemsAreLeftAlone() {
        // the phone's favourite came in but is not in the browser's places yet: no tombstone for it
        val phoneLedger = SyncLedger.read("").also { it.keepPlace(peak, 1000, phone) }.write()
        val l = WebSync.placesExport(phoneLedger, "", "", 2000.0, web, SyncPlaces.itemId(peak))
        assertEquals(phoneLedger, l)
        // without the wait it would be taken for a delete
        assertTrue(item(WebSync.placesExport(phoneLedger, "", "", 2000.0, web, ""), SyncPlaces.itemId(peak))!!.contains("\"deleted\":true"))
    }

    @Test fun importBringsThePhonesHomeAndFavouritesAndItsDeletes() {
        val before = WebSync.placesExport("", "", hut, 1000.0, web, "")
        // the round: the phone's Zuhause and Mutspitze arrive, the hut is unkept on the phone
        val after = SyncLedger.read(before).also {
            it.setHome(home, 2000, phone); it.keepPlace(peak, 2000, phone); it.unkeepPlace(SyncPlaces.itemId(hut), 2000, phone)
        }.write()
        val ids = WebSync.placesChanged(before, after)
        assertEquals(setOf(SyncPlaces.HOME_ID, SyncPlaces.itemId(peak), SyncPlaces.itemId(hut)), ids.split(',').toSet())
        val out = WebSync.placesImport(after, ids, "", hut, "", hut, true)
        assertEquals(home, homeOf(out))
        assertEquals(listOf(peak), favs(out))
    }

    @Test fun aPlaceTheUserChangedSinceTheExportStaysAsHeHasIt() {
        val l = SyncLedger.read("").also { it.setHome(home, 2000, phone) }.write()
        // the browser set the peak as Zuhause during the round: it stays (the next export sends it)
        val out = WebSync.placesImport(l, SyncPlaces.HOME_ID, peak, "", "", "", true)
        assertEquals(peak, homeOf(out))
        // not known what was kept (a leftover): the phone's home comes in
        assertEquals(home, homeOf(WebSync.placesImport(l, SyncPlaces.HOME_ID, "", "", "", "", false)))
    }

    @Test fun importKeepsTheLinesAsTheyCame() {
        val newer = "$peak\tx=1" // a newer app's extra field
        val unreadable = "n:9\tA\tpeak\t\tnorth\t11.1\t\t\tPOI"
        val l = SyncLedger.read("").also { it.keepPlace(newer, 1000, phone) }.write()
        val out = WebSync.placesImport(l, SyncPlaces.itemId(newer), "", listOf(hut, unreadable).joinToString("\n"), "", "", false)
        assertEquals(listOf(newer, hut, unreadable), favs(out))
        assertNull(homeOf(out))
    }

    @Test fun forgetDropsThePlacesAndReadsFromTheStart() {
        var l = WebSync.save("", SyncFormat.newId(ByteArray(16) { 5 }), "AQ", "Waalweg", 1000.0, web)
        l = WebSync.placesExport(l, home, hut, 1000.0, web, "")
        val led = SyncLedger.read(l)
        val forgot = WebSync.placesForget(l)
        assertTrue(items(forgot).isEmpty())
        assertEquals(0L, SyncLedger.read(forgot).since)
        assertEquals(1, SyncLedger.read(forgot).plans().size)
        assertEquals(led.entries.size - 2, SyncLedger.read(forgot).entries.size)
        assertFalse(WebSync.placesChanged(l, forgot).isNotEmpty())
    }
}
