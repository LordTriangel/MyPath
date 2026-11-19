package com.onpath.app

import com.onpath.app.testkit.Sources
import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the web planner compiles from the app (umbau phase 1, piece D): the WHOLE folder `core/src/main/kotlin`, minus the
 * files a stand-in in `web/engine/src/main/kotlin/com/onpath/app/` replaces and `web/engine/core-excludes.txt`, plus the few
 * files still in `:app` that `app/web-shared-files.txt` ([files]) names one by one (+ its [tests]). The engine build proves
 * the core part itself – but only the planner CI (web.yml) runs it, the gate does NOT build the engine, and the engine's
 * classpath is narrower than `:core`'s (no Compose runtime, only stand-ins for the texts and for `Problems`). So these checks
 * run here: the list exists, every compiled file (core and listed app files) imports only what the web build provides and
 * calls only the `Problems` members the web stand-in declares, every listed test exists, and the lock below.
 */
class SharedFilesRuleTest {
    private val list = File("web-shared-files.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    private val files = list.dropWhile { it != "[files]" }.drop(1).takeWhile { it != "[tests]" }
    private val tests = list.dropWhile { it != "[tests]" }.drop(1)

    /** Outside the app: what the web build stubs or brings (tools/routing/jvm-test/stubs, the web's stand-ins). */
    // "kotlin.", not "kotlin": the web has no kotlinx.* beyond coroutines (serialization, datetime … would slip through)
    private val external = listOf(
        "kotlin.", "java.", "javax.", "kotlinx.coroutines.", "org.xml.sax.", "org.json.", "com.google.gson.",
        "btools.", "org.jetbrains.compose.resources.StringResource", "org.jetbrains.compose.resources.PluralStringResource",
        "com.onpath.strings.",
        "android.content.Context", "android.content.Intent", "android.content.ActivityNotFoundException", "android.net.Uri",
    )

    private fun kt(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()

    /** Stand-ins of app / core classes: the same path below web/engine/src/main/kotlin/com/onpath/app/ replaces the real file. */
    private val standIns = kt(File("../web/engine/src/main/kotlin/com/onpath/app")).toSet()
    private val excludes = File("../web/engine/core-excludes.txt").readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
    /** The core files the engine compiles (path below com/onpath/app): the folder minus stand-ins and excludes. */
    private val coreCompiled = kt(File("../core/src/main/kotlin/com/onpath/app")).filter { it !in standIns && it !in excludes }

    @Test fun theListIsThere() {
        assertTrue(files.isNotEmpty())
        assertTrue(tests.isNotEmpty())
        assertTrue("the core folder is compiled as a whole", coreCompiled.size > 100)
    }

    @Test fun theListNamesOnlyFilesThatAreStillInTheApp() {
        val bad = files.filter { f -> !File("src/main/java/com/onpath/app/$f").isFile || File("../core/src/main/kotlin/com/onpath/app/$f").isFile }
        assertTrue("a listed file must be in :app (a file that moved to :core is covered by the folder – drop it from [files]): $bad", bad.isEmpty())
    }

    /** Imports of [src] the web build lacks (the engine's classpath: JDK, gson, org.json, coroutines, BRouter, its stand-ins). */
    private fun badImports(key: String, src: File, listed: Set<String>): List<String> {
        val bad = ArrayList<String>()
        for (line in src.readLines()) {
            val imp = Regex("""^import ([\w.]+)""").find(line)?.groupValues?.get(1)?.trimEnd('.') ?: continue
            if (imp.startsWith("com.onpath.app.")) {
                val tokens = imp.removePrefix("com.onpath.app.").split('.')
                // longest package first: `debug.Problems.Level` is the class Level inside Problems.kt, `search.Place` a class
                val rel = (tokens.size downTo 2).firstNotNullOfOrNull { n ->
                    val pkg = tokens.take(n - 1).joinToString("/"); val cls = tokens[n - 1]
                    val kts = Sources.appRoots.flatMap { r -> File(r, pkg).listFiles { x -> x.isFile && x.name.endsWith(".kt") }?.toList().orEmpty() }
                    // the file named after the class first: `fun Poi.toFeature()` in poi/PoiGeoJson.kt also matches the
                    // pattern, and the directory's order decided which one counted (red on main 2026-11-19)
                    (kts.firstOrNull { it.name == "$cls.kt" } ?: kts.firstOrNull { x ->
                        Regex("""(class|object|interface|fun|val) $cls\b""").containsMatchIn(x.readText())
                    })?.let { "$pkg/${it.name}" }
                }
                val wholePackage = line.trim().endsWith(".*")
                if (!wholePackage && (rel == null || (rel !in coreCompiled && rel !in standIns && rel !in listed))) bad += "$key: imports $imp (not shared)"
            } else if (imp.startsWith("com.onpath.strings.XmlStrings")) bad += "$key: imports $imp (the planner has no XmlStrings, only the Txt stand-in)"
            else if (external.none { (if (line.trim().endsWith(".*")) "$imp." else imp).startsWith(it) }) bad += "$key: imports $imp (the web build has no such class)"
        }
        return bad
    }

    @Test fun everyListedAppFileImportsOnlyWhatTheWebHas() {
        val listed = files.toSet()
        val bad = files.flatMap { f -> sourceFile(f).let { if (it.isFile) badImports(f, it, listed) else listOf("$f: missing") } }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /** The engine compiles every core file (nothing runs the engine build on the gate): the same import allow-list for all of them. */
    @Test fun everyCompiledCoreFileImportsOnlyWhatTheWebHas() {
        val listed = files.toSet()
        val bad = coreCompiled.flatMap { f -> badImports(f, File("../core/src/main/kotlin/com/onpath/app/$f"), listed) }
        assertTrue("a core file the planner compiles imports something the web build lacks (add a stand-in under web/engine/src/main/kotlin, " +
            "put the file in web/engine/core-excludes.txt with a reason, or split it):\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    /** The planner's Problems stand-in is smaller than core's: a compiled file may call only what it declares. */
    @Test fun compiledFilesCallOnlyProblemsMembersTheStandInDeclares() {
        val standIn = File("../web/engine/src/main/kotlin/com/onpath/app/debug/Problems.kt").readText()
        val declared = Regex("""\b(?:fun|val|var|class|object|interface)\s+(?:[\w.]+\.)?(\w+)""").findAll(standIn).map { it.groupValues[1] }.toSet() - "Problems"
        val bad = ArrayList<String>()
        val all = coreCompiled.map { it to File("../core/src/main/kotlin/com/onpath/app/$it") } + files.map { it to sourceFile(it) }
        for ((key, f) in all) f.readLines().forEachIndexed { i, line ->
            val code = line.substringBefore("//").trim()
            if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
            for (m in Regex("""\bProblems\.(\w+)""").findAll(code)) if (m.groupValues[1] !in declared)
                bad += "$key:${i + 1}: the planner's Problems stand-in lacks ${m.groupValues[1]}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun everySharedTestExists() {
        val missing = tests.filter { t ->
            listOf("src/test/java", "../core/src/test/kotlin", "../core/src/testkit/kotlin").none { File("$it/com/onpath/app/$t").isFile }
        }
        assertTrue("$missing", missing.isEmpty())
    }
}
