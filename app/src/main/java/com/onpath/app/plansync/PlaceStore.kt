package com.onpath.app.plansync

import com.onpath.app.search.Place
import com.onpath.app.search.SavedPlaces
import com.onpath.app.search.SearchStore

/**
 * The phone's kept places (Zuhause + favourites, F-PLAN-14) as the browser sync sees them (docs/design/web-sync.md §5.4;
 * AppSync: [SearchPlaceStore]; tests: in memory). [read] and [update] throw when the places cannot be read or written –
 * [PlanSyncPass] then leaves the places out of this pass (never „none kept“, which would delete them on the other side).
 */
interface PlaceStore {
    /** What the phone keeps now. */
    fun read(): SavedPlaces

    /** Read, [change], write in one step, alone among every writer of the places; true when they changed. */
    fun update(change: (SavedPlaces) -> SavedPlaces): Boolean

    /** A place as its SearchStore line (the sync carries it as it is, field 11). */
    fun encode(p: Place): String = SearchStore.encode(p)

    /** A SearchStore line as a place; null when it does not read. */
    fun decode(line: String): Place? = SearchStore.decode(line)
}

/** The app's kept places: SearchStore's `onpath-places` (written to disk before the ledger counts an import as done). */
class SearchPlaceStore(private val search: SearchStore) : PlaceStore {
    override fun read(): SavedPlaces = search.savedOrThrow()

    override fun update(change: (SavedPlaces) -> SavedPlaces): Boolean {
        var changed = false
        search.update(durable = true) { cur -> change(cur).also { changed = it != cur } }
        return changed
    }
}
