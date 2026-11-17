package com.onpath.app.testkit

import com.onpath.app.debug.Problems
import org.junit.rules.ExternalResource
import java.nio.file.Files

/**
 * The problem log for one test: its own empty folder, cleared before the test – so "was it reported?" and "nothing
 * was reported" can be asserted ([said], [none]). `@get:Rule val problems = ProblemsRule()`.
 */
class ProblemsRule : ExternalResource() {
    override fun before() { Problems.init(Files.createTempDirectory("problems").toFile()); Problems.clear() }

    /** Messages reported for [area] (all areas when null), newest first; INFO lines too. */
    fun said(area: String? = null, atLeast: Problems.Level = Problems.Level.INFO): List<String> =
        Problems.snapshot().filter { (area == null || it.area == area) && it.level >= atLeast }.map { it.message }

    /** Nothing at WARN or above was reported (for [area]). */
    fun none(area: String? = null): Boolean = said(area, Problems.Level.WARN).isEmpty()

    /** The gesture / event log lines for [area], oldest first. */
    fun traced(area: String): List<String> = Problems.events().filter { it.area == area }.map { it.message }.reversed()
}
