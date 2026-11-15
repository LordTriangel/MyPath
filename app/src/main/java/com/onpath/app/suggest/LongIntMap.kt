package com.onpath.app.suggest

/**
 * long → int map without an object per entry (open addressing, linear probing) – node positions → node numbers while
 * the network is read. Keys must not be [Long.MIN_VALUE] (the empty mark); values are ≥ 0 by convention, [get] answers
 * −1 for a missing key.
 */
internal class LongIntMap(expected: Int = 1024) {
    private var keys: LongArray
    private var vals: IntArray
    private var mask: Int
    var size = 0; private set

    init {
        var cap = 16
        while (cap < expected * 2) cap = cap shl 1
        keys = LongArray(cap) { EMPTY }
        vals = IntArray(cap)
        mask = cap - 1
    }

    operator fun get(key: Long): Int {
        var i = hash(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) return vals[i]
            if (k == EMPTY) return -1
            i = (i + 1) and mask
        }
    }

    fun put(key: Long, value: Int) {
        if ((size + 1) * 2 > keys.size) rehash(keys.size * 2)
        var i = hash(key) and mask
        while (true) {
            val k = keys[i]
            if (k == key) { vals[i] = value; return }
            if (k == EMPTY) { keys[i] = key; vals[i] = value; size++; return }
            i = (i + 1) and mask
        }
    }

    fun bytes(): Long = keys.size * 12L

    /** Empty, and gives the memory back (the map stays usable). */
    fun release() {
        keys = LongArray(16) { EMPTY }; vals = IntArray(16); mask = 15; size = 0
    }

    fun clear() {
        if (size == 0) return
        keys.fill(EMPTY)
        size = 0
    }

    private fun rehash(cap: Int) {
        val ok = keys; val ov = vals
        keys = LongArray(cap) { EMPTY }; vals = IntArray(cap); mask = cap - 1; size = 0
        for (i in ok.indices) if (ok[i] != EMPTY) put(ok[i], ov[i])
    }

    private fun hash(key: Long): Int {
        val h = key * -0x61c8864680b583ebL
        return (h xor (h ushr 29)).toInt()
    }

    private companion object {
        const val EMPTY = Long.MIN_VALUE
    }
}
