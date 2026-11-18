package com.onpath.app.suggest

/**
 * A set of non-negative ints that iterates in the order CPython 3.12's `set` does (setobject.c: open addressing,
 * 9 linear probes, then perturbation; resize at 60 % fill). The method chooses "the first of equals" in a few places
 * (two lines of the same rank, two nodes at the same point of an official loop), and the POC's first is the order of
 * its Python sets – this keeps the port's choices the POC's, so parity is exact. Any fixed order would do for the app.
 */
class PyIntSet {
    private var keys = IntArray(8) { EMPTY }
    private var mask = 7
    private var fill = 0
    var size = 0; private set

    fun isEmpty() = size == 0

    operator fun contains(k: Int): Boolean = slotOf(k) >= 0

    private fun slotOf(k: Int): Int {
        var i = k and mask
        var perturb = k.toLong()
        while (true) {
            var probes = if (i + LINEAR <= mask) LINEAR else 0
            var j = i
            while (true) {
                val e = keys[j]
                if (e == EMPTY) return -1
                if (e == k) return j
                j++
                if (probes-- == 0) break
            }
            perturb = perturb ushr SHIFT
            i = ((i.toLong() * 5 + 1 + perturb) and mask.toLong()).toInt()
        }
    }

    fun add(k: Int) {
        var i = k and mask
        var perturb = k.toLong()
        var free = -1
        while (true) {
            var probes = if (i + LINEAR <= mask) LINEAR else 0
            var j = i
            while (true) {
                val e = keys[j]
                if (e == EMPTY) {
                    if (free >= 0) { keys[free] = k; size++; return }
                    keys[j] = k; fill++; size++
                    if (fill.toLong() * 5 >= mask.toLong() * 3) resize(if (size > 50000) size * 2 else size * 4)
                    return
                }
                if (e == k) return
                if (e == DUMMY && free < 0) free = j
                j++
                if (probes-- == 0) break
            }
            perturb = perturb ushr SHIFT
            i = ((i.toLong() * 5 + 1 + perturb) and mask.toLong()).toInt()
        }
    }

    fun remove(k: Int) {
        val j = slotOf(k)
        if (j >= 0) { keys[j] = DUMMY; size-- }
    }

    /** `self |= other` (set_merge). */
    fun addAll(other: PyIntSet) {
        if (other === this || other.size == 0) return
        if ((fill + other.size).toLong() * 5 >= mask.toLong() * 3) resize((size + other.size) * 2)
        if (fill == 0 && mask == other.mask && other.fill == other.size) {
            other.keys.copyInto(keys)
            fill = other.size; size = other.size
            return
        }
        if (fill == 0) {
            for (k in other.keys) if (k >= 0) insertClean(keys, mask, k)
            fill = other.size; size = other.size
            return
        }
        for (k in other.keys) if (k >= 0) add(k)
    }

    private fun resize(minUsed: Int) {
        var n = 8
        while (n <= minUsed) n = n shl 1
        val old = keys
        if (n == 8 && mask == 7 && fill == size) return
        keys = IntArray(n) { EMPTY }
        mask = n - 1
        fill = size
        for (k in old) if (k >= 0) insertClean(keys, mask, k)
    }

    private fun insertClean(table: IntArray, m: Int, k: Int) {
        var i = k and m
        var perturb = k.toLong()
        while (true) {
            if (table[i] == EMPTY) { table[i] = k; return }
            if (i + LINEAR <= m) {
                for (j in 1..LINEAR) if (table[i + j] == EMPTY) { table[i + j] = k; return }
            }
            perturb = perturb ushr SHIFT
            i = ((i.toLong() * 5 + 1 + perturb) and m.toLong()).toInt()
        }
    }

    inline fun forEach(f: (Int) -> Unit) {
        val t = table()
        for (k in t) if (k >= 0) f(k)
    }

    /** The elements in Python's iteration order. */
    fun toIntArray(): IntArray {
        val out = IntArray(size)
        var n = 0
        for (k in keys) if (k >= 0) out[n++] = k
        return out
    }

    @PublishedApi internal fun table(): IntArray = keys

    companion object {
        private const val EMPTY = -1
        private const val DUMMY = -2
        private const val LINEAR = 9
        private const val SHIFT = 5

        fun of(items: IntArray): PyIntSet = PyIntSet().also { s -> for (k in items) s.add(k) }
        fun of(items: Iterable<Int>): PyIntSet = PyIntSet().also { s -> for (k in items) s.add(k) }
    }
}
