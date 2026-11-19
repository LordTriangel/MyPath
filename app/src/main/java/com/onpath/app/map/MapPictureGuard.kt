package com.onpath.app.map

import com.onpath.app.debug.Problems
import java.io.File

/**
 * A map picture of My routes (the rows' small ones, the overview's large ones) is kept only when it was drawn on the map
 * as it still is (field report on 0.18.1, owner 2026-11-19: „der Kartenhintergrund hinter den Routenbildern fehlt“ – the
 * line on an empty map). A snapshot reads the map files of one folder (the data folder or a generation of map cells,
 * `DataFiles.mapDir`); when map areas are joined meanwhile, a new folder becomes the map's and the old one is deleted –
 * a snapshot still drawing on it gets no tiles, only the background, the cover over the detailed areas and the line. Its
 * key knows the line, not the map, so such a picture was kept for good. Now it is dropped and drawn once more on the map
 * as it is then; a map still changing → no picture now (made later). Pure apart from the lambdas; JVM tested.
 */
object MapPictureGuard {
    /** A snapshot's style and the map folder its files are read from. */
    class Style(val json: String, val mapDir: File)

    /**
     * [make] on [first] (else a new [style]); the result only when [mapDirNow] afterwards is still that style's folder and
     * the folder is there. Else the result goes to [drop] (a bitmap is recycled) and the next try reads the style anew –
     * [tries] in all. Null = no style, nothing drawn, or the map kept changing (reported as INFO).
     */
    suspend fun <T : Any> drawn(first: Style?, style: suspend () -> Style?, mapDirNow: suspend () -> File?,
                                make: suspend (json: String) -> T?, drop: (T) -> Unit = {}, tries: Int = 2): T? {
        var s = first
        for (i in 1..tries) {
            val st = s ?: style() ?: return null
            s = null
            val out = make(st.json) ?: return null
            val now = mapDirNow()
            if (now == st.mapDir && st.mapDir.isDirectory) return out
            drop(out)
            Problems.info("routes", "The map changed while a map picture was drawn (${st.mapDir.name} → ${now?.name}) – " +
                if (i < tries) "drawn again" else "made later")
        }
        return null
    }
}
