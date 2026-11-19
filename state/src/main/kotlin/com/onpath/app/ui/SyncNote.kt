package com.onpath.app.ui

/** The strip at the top of the Plan map after a pairing was made or ended here (owner 2026-10-30), until OK. */
enum class SyncNote {
    /** "Paired. What you save in the browser comes to your phone – …" */
    PAIRED,
    /** "Unpaired. Your routes stay on your phone." */
    UNPAIRED,
    /** Unpaired here, the mailbox could not be ended: "… – the browser couldn't be reached, unpair it there too." */
    UNPAIRED_OFFLINE,
}
