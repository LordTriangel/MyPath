package com.onpath.app.ui

/**
 * The pure decisions of the imports ([ImportExport] in `:app` runs them): which name an imported GPX gets, what a map-data
 * import tells the user, which text a failed GPX import shows. Expressions as they were in `AppViewModel.importGpx` /
 * `importData` (texts and the lazily read facts passed in).
 */
object ImportRules {
    /** The imported track's name: the file's own, else its file name without ".gpx", else [default] (read only then). */
    fun gpxName(dataName: String?, fileName: String?, default: () -> String): String =
        dataName ?: fileName?.removeSuffix(".gpx")?.removeSuffix(".GPX") ?: default()

    /** The map-data import's summary + the mixed-package note + (new .pmtiles while a tour runs) the restart-after-tour hint. */
    fun dataImportMessage(summary: String, packageNote: () -> String, newTiles: Boolean, recording: () -> Boolean,
        restartAfterTour: () -> String): String =
        summary + packageNote() + if (newTiles && recording()) " " + restartAfterTour() else ""

    /** A failed GPX import: our own plain reason (GpxException) when there is one, else the general text. */
    fun gpxFailureText(own: String, withReason: (String) -> String, plain: () -> String): String =
        if (own.isNotEmpty()) withReason(own) else plain()
}
