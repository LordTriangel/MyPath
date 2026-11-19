package com.onpath.app.debug

import com.onpath.app.testkit.Sources
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Architecture rule (like SharedCoreRuleTest): no bare `viewModelScope.launch` in the app – a failure there used to
 * end the app after the crash log (audit 2026-11-07). Use [launchSafe] (own words in the problem log) or a scope made
 * with [reported].
 */
class SafeLaunchRuleTest {
    @Test fun noBareViewModelScopeLaunch() {
        val hits = Sources.kotlinFiles().asSequence().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val code = line.substringBefore("//")
                if ("viewModelScope.launch(" in code || "viewModelScope.launch {" in code) "${Sources.appRoots.firstOrNull { r -> f.startsWith(r) }?.let { r -> f.relativeTo(r) } ?: f.name}:${i + 1}" else null
            }
        }.toList()
        assertEquals("use launchSafe or a reported scope: $hits", emptyList<String>(), hits)
    }

    /** The same for the slices in :state (docs/design/umbau-phase-2.md §3.3): they get the view model's scope as `scope`. */
    @Test fun noBareScopeLaunchInState() {
        val state = Sources.roots.filter { it.invariantSeparatorsPath.endsWith("state/src/main/kotlin") }
        assertEquals("expected the :state sources next to the working directory", 1, state.size)
        val bare = Regex("""\bscope\.launch\s*[({]""")
        val hits = state.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line -> if (bare.containsMatchIn(line.substringBefore("//"))) "${f.relativeTo(root)}:${i + 1}" else null }
            }.toList()
        }
        assertEquals("use scope.launchSafe(area, what): $hits", emptyList<String>(), hits)
    }
}
