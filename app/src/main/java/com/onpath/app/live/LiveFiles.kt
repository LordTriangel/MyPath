package com.onpath.app.live

import com.onpath.app.data.AtomicFiles
import com.onpath.app.debug.Problems
import java.io.File

/**
 * The shared tour's remembered state on disk: one text in `files/live/share.txt` (docs/design/live-tracking.md §7.5),
 * replaced atomically so a kill in the middle leaves the old or the new text, never half of one. Not in the Android
 * backup (the backup rules list what is included; `live/` is not) – a restored phone must not reuse a link.
 *
 * Never throws: a file that cannot be read is "no share" and a write that fails is reported – the uploader keeps its
 * memory copy for this process. Only the live lane's thread calls it.
 */
class LiveFiles(private val dir: File) : LiveStore {
    private val file get() = File(dir, FILE)

    override fun read(): String? = try {
        val f = file
        if (f.isFile) f.readText().takeIf { it.isNotBlank() } else null
    } catch (e: Exception) {
        Problems.warn("live", "Live sharing: the saved state could not be read – sharing is off", e)
        null
    }

    override fun write(text: String?) {
        try {
            val f = file
            if (text == null) {
                if (f.exists() && !f.delete()) Problems.warn("live", "Live sharing: the saved state could not be removed")
                return
            }
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                Problems.warn("live", "Live sharing: the folder for the saved state could not be made")
                return
            }
            AtomicFiles.writeText(f, text)
        } catch (e: Exception) {
            Problems.warn("live", "Live sharing: the state could not be saved (the link stays valid while the app runs)", e)
        }
    }

    companion object {
        const val FILE = "share.txt"
    }
}
