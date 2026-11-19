package com.onpath.app.plansync

/** A pass, pairing or unpairing ended. */
data class SyncReport(val manual: Boolean, val result: SyncPassResult?, val ended: Boolean = false, val error: Boolean = false)
