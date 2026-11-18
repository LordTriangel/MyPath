package com.onpath.app.testkit

/**
 * The table of a `Broken…FilesTest`: one row per (file kind x damage). [runAll] runs every row, so one failing row
 * does not hide the others, and fails with the NAME of each failed row ("follow.json / truncated: …").
 */
class BrokenRows {
    private val rows = mutableListOf<Pair<String, () -> Unit>>()

    val size get() = rows.size

    fun row(kind: String, damage: String, check: () -> Unit) { rows += "$kind / $damage" to check }

    fun runAll() {
        val failed = mutableListOf<Pair<String, Throwable>>()
        for ((name, check) in rows) try { check() } catch (t: Throwable) { failed += name to t }
        if (failed.isNotEmpty()) {
            val msg = "${failed.size} of ${rows.size} rows failed:\n" + failed.joinToString("\n") { (n, t) -> "  [$n] ${t.javaClass.simpleName}: ${t.message}" }
            throw AssertionError(msg, failed.first().second)
        }
    }
}
