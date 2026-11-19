package com.onpath.app.testkit

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * For a plain JVM test that writes Compose state (`mutableStateOf` holders such as TourController) outside any
 * composition. Such a write lands in Compose's global snapshot, where Compose UI's process-wide watcher (started by
 * the first screen composed in the JVM – a Paparazzi test) picks it up and waits for a main loop that no plain test
 * runs. With such a test between screenshot classes, the screens composed later in the same JVM no longer got their
 * state changes: 19 navigate screenshots and 4 recomposition tests failed in the full suite, each green alone, and
 * which ones depends on the order of the test classes.
 *
 * The rule runs each test inside its own snapshot and throws that snapshot away: nothing is written to the global
 * snapshot, so nothing can reach another test class whatever the order. `@get:Rule val composeState = OwnComposeSnapshot()`.
 */
class OwnComposeSnapshot : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val own = Snapshot.takeMutableSnapshot()
            try { own.enter { base.evaluate() } } finally { own.dispose() }
        }
    }
}
