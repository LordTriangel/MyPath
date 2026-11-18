package com.onpath.app.data

/** Whether the storage holding the offline data is there right now; [whenReady] runs a block once it is. */
interface StorageGate {
    fun ready(): Boolean
    /** Runs [block] once the storage is ready: at once when it is, else later on a background thread. */
    fun whenReady(block: () -> Unit)

    companion object {
        /** Always there (tests, internal folders). */
        val ALWAYS = object : StorageGate {
            override fun ready() = true
            override fun whenReady(block: () -> Unit) = block()
        }
    }
}
