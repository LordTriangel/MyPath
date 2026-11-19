package com.onpath.app.testkit

import java.io.File

/**
 * Where the app's main sources are. Since :core and :state exist a package may live in all three modules (`:app`
 * src/main/java, `:core` and `:state` src/main/kotlin), and a JVM test runs with the module folder as its working
 * directory – so a test that reads source text (rule tests, "the screen calls X" guards) looks in all three through this
 * file instead of a literal path.
 *
 * This file sits in core/src/testkit/kotlin, a test source dir of ALL THREE modules (app / core / state build.gradle.kts).
 */
object Sources {
    private fun existing(vararg paths: String): List<File> =
        paths.map { File(it) }.filter { it.isDirectory }.distinctBy { it.canonicalPath }

    /** The `com/onpath/app` package folders of the three modules (:core first, then :state, then :app). */
    val appRoots: List<File> = existing(
        "src/main/kotlin/com/onpath/app", "../core/src/main/kotlin/com/onpath/app", "../state/src/main/kotlin/com/onpath/app",
        "src/main/java/com/onpath/app", "../app/src/main/java/com/onpath/app",
    ).also {
        // a test started from another folder would scan nothing and pass: fail loudly instead
        require(it.size >= 3) { "Sources: expected the main sources of :app, :core and :state next to the working directory ${File(".").canonicalPath}, found $it" }
    }

    /** All main source roots (`src/main/java` with `btools`, `src/main/kotlin`) of the three modules. */
    val roots: List<File> = existing("src/main/kotlin", "../core/src/main/kotlin", "../state/src/main/kotlin", "src/main/java", "../app/src/main/java")
        .also { require(it.size >= 3) { "Sources: expected the main source roots of :app, :core and :state, found $it" } }

    /** A file below com/onpath/app, e.g. `routing/BreakdownRules.kt`: whichever module has it (search order everywhere: :core, :state, then :app). */
    fun file(rel: String): File =
        appRoots.map { File(it, rel) }.firstOrNull { it.isFile } ?: File(appRoots.first(), rel)

    /** The Kotlin files of a package folder (and below) in the three modules; "" = every package. */
    fun kotlinFiles(pkg: String = ""): List<File> =
        appRoots.map { if (pkg.isEmpty()) it else File(it, pkg) }.filter { it.isDirectory }
            .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    /** Kotlin + Java files of every main source root (BRouter patches included). */
    fun allFiles(extensions: Set<String> = setOf("kt", "java")): List<File> =
        roots.flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension in extensions }.toList() }
}

fun sourceFile(rel: String): File = Sources.file(rel)
