// :core – the part of OnPath that knows no Android: plain Kotlin on the JVM (docs/design/umbau-phase-1.md). Package
// names are the app's (com.onpath.app.<pkg>): a package may live in :core and in :app. Tests run here without the
// Android build ("core tests in about a minute"). Dependencies: only what the moved code uses.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// test helpers shared with :app's tests (Sources.kt ...): the same folder is a test source dir of :app too
sourceSets.test { kotlin.srcDir("src/testkit/kotlin") }

dependencies {
    // the texts; Gradle picks the JVM variant here and the Android variant for :app (one copy in the APK)
    api(project(":strings"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("com.google.code.gson:gson:2.10.1")
    // Android has org.json built in: compile against the jar, never ship it
    compileOnly("org.json:json:20240303")
    // BRouter, until :engine-brouter exists: the app's own jars
    compileOnly(fileTree(mapOf("dir" to "../app/libs", "include" to listOf("*.jar"))))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(fileTree(mapOf("dir" to "../app/libs", "include" to listOf("*.jar"))))
}

tasks.test {
    // like :app (app/build.gradle.kts testOptions): UTC, English, the texts straight from the XML files
    systemProperty("user.timezone", "UTC")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("onpath.stringsDir", rootProject.file("strings/src/commonMain/composeResources").absolutePath)
}

// The name CI knows: `./gradlew testDebugUnitTest` (unit.yml) picks this up beside the app's own. :app:testDebugUnitTest
// (release.yml, the gate) depends on :core:test (app/build.gradle.kts), so a `--tests` filter on the app task never
// reaches the core task and cannot fail it.
tasks.register("testDebugUnitTest") { dependsOn(tasks.test) }
