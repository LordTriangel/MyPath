package com.onpath.app.suggest.app

import com.onpath.app.cells.CellApi
import com.onpath.app.cells.CellId
import com.onpath.app.cells.CellManifest
import com.onpath.app.cells.CellStore
import com.onpath.app.debug.Problems
import com.onpath.app.suggest.SuggestFile
import java.io.File

/**
 * Fetches only the suggest file (`suggest.bin`, ~0.2–0.4 MB) of installed cells that have none or an old one – the
 * answer to [com.onpath.app.suggest.SuggestCards.cellsToFetch]. Cells installed before this app kept the file (or by a
 * released app, which drops it) would otherwise wait for a whole new cell version (9–23 MB): the map cell flow skips a
 * cell whose version did not change. Blocking (network); call from a background thread. No Wi-Fi rule here – the
 * caller decides (the files are small).
 *
 * Per cell: the current manifest; no `suggest.bin` in it (the server's cell is older than schema 9) → [Outcome.NOT_ON_SERVER]
 * (the map there is being updated – the server rebuilds it); else download (size + sha256 checked by [download]) and
 * install into the store ([CellStore.installSuggest]).
 */
class SuggestFetch(
    private val store: CellStore,
    private val manifest: (CellId) -> CellManifest,
    private val download: (CellManifest.File, File) -> Unit,
) {
    constructor(store: CellStore, api: CellApi) : this(store, { api.manifest(it) }, { f, part -> api.download(f, part) })

    enum class Outcome { FETCHED, NOT_ON_SERVER, FAILED }

    /** [cells] as [x, y] of zoom 10 (as [com.onpath.app.suggest.SuggestCards.cellsToFetch]). */
    fun fetch(cells: List<IntArray>, check: () -> Unit = {}): Map<CellId, Outcome> {
        val out = LinkedHashMap<CellId, Outcome>()
        for (xy in cells) {
            check()
            val id = CellId(xy[0], xy[1])
            out[id] = fetchOne(id)
        }
        return out
    }

    private fun fetchOne(id: CellId): Outcome {
        val part = File(store.suggestDir, ".${id.id}.bin.part")
        return try {
            val m = manifest(id)
            val f = m.files.firstOrNull { it.name == CellStore.SUGGEST } ?: return Outcome.NOT_ON_SERVER
            store.suggestDir.mkdirs()
            download(f, part)
            // a file the reader cannot use is not installed (the cell stays "being updated")
            val content = SuggestFile.read(part.readBytes()).content
            if (content < SuggestFile.CONTENT) {
                part.delete()
                Problems.info("suggest", "Suggest file of cell $id on the server is content $content (< ${SuggestFile.CONTENT}); not installed")
                return Outcome.NOT_ON_SERVER
            }
            if (store.installSuggest(id, part)) Outcome.FETCHED else Outcome.FAILED
        } catch (e: CellApi.ApiException) {
            if (e.failure == CellApi.Failure.NOT_READY) Outcome.NOT_ON_SERVER.also { part.delete() } else {
                Problems.info("suggest", "Suggest file of cell $id not fetched (${e.failure}); asked again with the next wish", e)
                Outcome.FAILED
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            part.delete()
            Problems.warn("suggest", "Suggest file of cell $id not fetched; asked again with the next wish", e)
            Outcome.FAILED
        }
    }
}
