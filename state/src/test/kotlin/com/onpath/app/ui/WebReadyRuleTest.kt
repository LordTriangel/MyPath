package com.onpath.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The files named in `state/web-ready.txt` are the ones the planner (TeaVM, no coroutines, no Compose runtime jar) may take
 * as they are, with a stand-in for the five Compose runtime names (docs/design/umbau-phase-2.md §2.2, rules 1 + 2): they
 * import nothing but `com.onpath.app.*`, `com.onpath.strings.*`, `java.*` / `kotlin.*` and those five names, and have no
 * coroutine in them. The test reads the list from this module's folder (a Gradle test runs with it as working directory).
 */
class WebReadyRuleTest {
    private val runtimeNames = setOf("mutableStateOf", "getValue", "setValue", "mutableStateListOf", "mutableIntStateOf")
    private val allowedPrefixes = listOf("com.onpath.app.", "com.onpath.strings.", "java.", "kotlin.")

    private fun listed(): List<String> = File("web-ready.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test fun theListIsNotEmptyAndItsFilesExist() {
        val files = listed()
        assertTrue("state/web-ready.txt names no file", files.isNotEmpty())
        for (f in files) assertTrue("$f is listed but not in :state", File("src/main/kotlin/com/onpath/app/$f").isFile)
    }

    @Test fun listedFilesImportOnlyWhatThePlannerHas() {
        val bad = mutableListOf<String>()
        for (f in listed()) {
            File("src/main/kotlin/com/onpath/app/$f").readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("import ")) {
                    val name = code.removePrefix("import ").substringBefore(" as ").trim()
                    val ok = name.startsWith("androidx.compose.runtime.") && name.removePrefix("androidx.compose.runtime.") in runtimeNames ||
                        allowedPrefixes.any { name.startsWith(it) }
                    if (!ok) bad += "$f:${i + 1} import $name"
                }
                if ("kotlinx.coroutines" in code || ("androidx." in code && !code.startsWith("import androidx.compose.runtime."))) bad += "$f:${i + 1} $code"
            }
        }
        assertEquals("not web-ready (docs/design/umbau-phase-2.md §2.2): $bad", emptyList<String>(), bad)
    }
}
