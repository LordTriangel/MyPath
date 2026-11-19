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
 * the core part itself (it compiles the folder; the planner CI and the gate run it), so these checks guard the rest:
 * the list exists, a listed app file imports only what the web build provides, every listed test exists, and the lock below.
 */
class SharedFilesRuleTest {
    private val list = File("web-shared-files.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    private val files = list.dropWhile { it != "[files]" }.drop(1).takeWhile { it != "[tests]" }
    private val tests = list.dropWhile { it != "[tests]" }.drop(1)

    /** Outside the app: what the web build stubs or brings (tools/routing/jvm-test/stubs, the web's stand-ins). */
    private val external = listOf(
        "kotlin", "java.", "javax.xml.parsers.", "org.xml.sax.", "org.json.", "com.google.gson.",
        "btools.", "org.jetbrains.compose.resources.StringResource", "com.onpath.strings.",
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

    @Test fun everyListedAppFileImportsOnlyWhatTheWebHas() {
        val listed = files.toSet()
        val bad = ArrayList<String>()
        for (f in files) {
            val src = sourceFile(f)
            if (!src.isFile) { bad += "$f: missing"; continue }
            for (line in src.readLines()) {
                val imp = Regex("""^import ([\w.]+)""").find(line)?.groupValues?.get(1) ?: continue
                if (imp.startsWith("com.onpath.app.")) {
                    val name = imp.removePrefix("com.onpath.app.")
                    val pkg = name.substringBeforeLast('.').replace('.', '/'); val cls = name.substringAfterLast('.')
                    val dirs = Sources.appRoots.map { File(it, pkg) }
                    // the file named after the class first: `fun Poi.toFeature()` in poi/PoiGeoJson.kt also matches the
                    // pattern, and the directory's order decided which one counted (red on main 2026-11-19)
                    val kts = dirs.flatMap { it.listFiles { x -> x.isFile && x.name.endsWith(".kt") }?.toList().orEmpty() }
                    val home = kts.firstOrNull { it.name == "$cls.kt" } ?: kts.firstOrNull { x ->
                        Regex("""(class|object|interface|fun|val) $cls\b""").containsMatchIn(x.readText())
                    }
                    val rel = home?.let { "$pkg/${it.name}" }
                    if (rel == null || (rel !in coreCompiled && rel !in standIns && rel !in listed)) bad += "$f: imports $imp (not shared)"
                } else if (external.none { imp.startsWith(it) }) bad += "$f: imports $imp (the web build has no such class)"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * web.yml does not know the core folder yet (its path filters and the engine cache key): an edit of a core file the
     * planner compiles would neither start the planner run nor change the cache key. web/engine/core-shared.lock is in
     * web/engine and holds a fingerprint of those files (tools/dev/core_move.py lock; the set = core/src/main/kotlin minus
     * stand-ins minus core-excludes.txt, + the [tests] that live in core). Goes when web.yml knows the core folder.
     */
    @Test fun coreFilesThePlannerCompilesMatchTheLock() {
        val paths = (coreCompiled.map { "core/src/main/kotlin/com/onpath/app/$it" } +
            tests.flatMap { t -> listOf("core/src/test/kotlin/com/onpath/app/$t", "core/src/testkit/kotlin/com/onpath/app/$t") })
            .filter { File("../$it").isFile }.sorted()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        for (p in paths) { md.update(p.toByteArray()); md.update(0); md.update(File("../$p").readBytes()); md.update(0) }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        val want = File("../web/engine/core-shared.lock").readLines().firstOrNull { it.startsWith("sha256 ") }?.removePrefix("sha256 ")?.trim()
        assertTrue("core/ files the planner compiles changed – run python3 tools/dev/core_move.py lock and commit web/engine/core-shared.lock",
            got == want)
    }

    @Test fun everySharedTestExists() {
        val missing = tests.filter { t ->
            listOf("src/test/java", "../core/src/test/kotlin", "../core/src/testkit/kotlin").none { File("$it/com/onpath/app/$t").isFile }
        }
        assertTrue("$missing", missing.isEmpty())
    }
}
