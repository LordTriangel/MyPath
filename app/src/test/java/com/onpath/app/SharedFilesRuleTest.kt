package com.onpath.app

import com.onpath.app.testkit.Sources
import com.onpath.app.testkit.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Review Arch 5: the files the web planner compiles from the app are ONE list on main (`app/web-shared-files.txt`),
 * read by the web build too. Every listed file must exist and import only what the web build provides – a shared file
 * that starts to use Android or another app class would break the planner unnoticed until cloud-plan merges.
 */
class SharedFilesRuleTest {
    private val list = File("web-shared-files.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    private val files = list.dropWhile { it != "[files]" }.drop(1).takeWhile { it != "[tests]" }
    private val tests = list.dropWhile { it != "[tests]" }.drop(1)

    /** Outside the app: what the web build stubs or brings (tools/routing/jvm-test/stubs, the web's stand-ins). */
    private val external = listOf(
        "kotlin", "java.", "javax.xml.parsers.", "org.xml.sax.", "org.json.", "com.google.gson.", "org.maplibre.geojson.",
        "btools.", "org.jetbrains.compose.resources.StringResource", "com.onpath.strings.",
        "android.content.Context", "android.content.Intent", "android.content.ActivityNotFoundException", "android.net.Uri",
    )
    /** App classes the web replaces with its own stand-ins. */
    private val standIns = setOf("debug.Problems", "debug.UserText", "recording.GpsStatus")

    @Test fun theListIsThere() {
        assertTrue(files.size > 50)
        assertTrue(tests.isNotEmpty())
    }

    @Test fun everySharedFileImportsOnlyWhatTheWebHas() {
        val listed = files.toSet()
        val bad = ArrayList<String>()
        for (f in files) {
            val src = sourceFile(f)
            if (!src.isFile) { bad += "$f: missing"; continue }
            for (line in src.readLines()) {
                val imp = Regex("""^import ([\w.]+)""").find(line)?.groupValues?.get(1) ?: continue
                if (imp.startsWith("com.onpath.app.")) {
                    val name = imp.removePrefix("com.onpath.app.")
                    if (name in standIns || name.startsWith("debug.")) continue
                    val pkg = name.substringBeforeLast('.').replace('.', '/'); val cls = name.substringAfterLast('.')
                    val dirs = Sources.appRoots.map { File(it, pkg) }
                    // the file named after the class first: `fun Poi.toFeature()` in poi/PoiGeoJson.kt also matches the
                    // pattern, and the directory's order decided which one counted (red on main 2026-11-19)
                    val kts = dirs.flatMap { it.listFiles { x -> x.isFile && x.name.endsWith(".kt") }?.toList().orEmpty() }
                    val home = kts.firstOrNull { it.name == "$cls.kt" } ?: kts.firstOrNull { x ->
                        Regex("""(class|object|interface|fun|val) $cls\b""").containsMatchIn(x.readText())
                    }
                    if (home == null || "$pkg/${home.name}" !in listed) bad += "$f: imports $imp (not shared)"
                } else if (external.none { imp.startsWith(it) }) bad += "$f: imports $imp (the web build has no such class)"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    /**
     * web.yml does not know core/** yet (its path filters and the engine cache key): an edit of a shared file that lives in
     * core/ would neither start the planner run nor change the cache key. web/engine/core-shared.lock is under web/engine/**
     * and holds a fingerprint of those files (tools/dev/core_move.py lock). Goes when web.yml knows core/**.
     */
    @Test fun coreFilesThePlannerCompilesMatchTheLock() {
        val paths = (files.map { "core/src/main/kotlin/com/onpath/app/$it" } +
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
