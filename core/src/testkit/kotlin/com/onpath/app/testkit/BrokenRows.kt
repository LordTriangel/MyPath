package com.onpath.app.testkit

/**
 * The table of a `Broken…FilesTest`: one row per (file kind x damage). [runAll] runs every row, so one failing row
 * does not hide the others, and fails with the NAME of each failed row ("follow.json / truncated: …").
 */
class BrokenRows {
    private val rows = mutableListOf<Pair<String, () -> Unit>>()

    val size get() = rows.size

    private val groups = mutableListOf<Triple<String, Int, Int>>() // name, expected, actual

    /** A group of rows (one stored-file kind) with its exact row count: a dropped or added row turns [runAll] red and names the group. */
    fun group(name: String, expected: Int, add: () -> Unit) {
        val before = rows.size
        add()
        groups += Triple(name, expected, rows.size - before)
    }

    fun row(kind: String, damage: String, check: () -> Unit) { rows += "$kind / $damage" to check }

    fun runAll() {
        val short = groups.filter { it.second != it.third }
        if (short.isNotEmpty()) throw AssertionError("row count differs from the expected one: " +
            short.joinToString { "${it.first} has ${it.third} rows, expected ${it.second}" })
        val failed = mutableListOf<Pair<String, Throwable>>()
        for ((name, check) in rows) try { check() } catch (t: Throwable) { failed += name to t }
        if (failed.isNotEmpty()) {
            val msg = "${failed.size} of ${rows.size} rows failed:\n" + failed.joinToString("\n") { (n, t) -> "  [$n] ${t.javaClass.simpleName}: ${t.message}" }
            throw AssertionError(msg, failed.first().second)
        }
    }
}
