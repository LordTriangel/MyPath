package com.onpath.app.recording

import com.onpath.app.testkit.Sources
import com.onpath.app.testkit.sourceFile
import com.onpath.app.testkit.FakeRecordingClient
import com.onpath.app.ui.tour.tourRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The typed contract app → recording service (cleanup piece 5, code audit 2026-11-12 point c). The Intents are what
 * they always were: every action string and every extra (name and type) is written out here – a pending notification
 * button of an older build and a sticky restart keep meaning the same. And the rule: outside `recording/` nobody
 * builds a service Intent or writes the service's flows.
 */
class RecordingClientTest {
    private val client = FakeRecordingClient()
    private fun last() = client.sent.last().let { it.action to it.extras }

    @Test fun everyCommandIsTheIntentItAlwaysWas() {
        client.start("HIKE", 2); assertEquals("com.onpath.app.START" to mapOf<String, Any>("activity" to "HIKE", "sac" to 2), last())
        client.continueTour(7L); assertEquals("com.onpath.app.START" to mapOf<String, Any>("continueId" to 7L), last())
        client.pause(true); assertEquals("com.onpath.app.PAUSE" to emptyMap<String, Any>(), last())
        client.pause(false); assertEquals("com.onpath.app.RESUME" to emptyMap<String, Any>(), last())
        client.stop(); assertEquals("com.onpath.app.STOP" to emptyMap<String, Any>(), last())
        client.followChanged(); assertEquals("com.onpath.app.FOLLOW_CHANGED" to emptyMap<String, Any>(), last())
        client.followChanged(checkNow = true); assertEquals("com.onpath.app.FOLLOW_CHANGED" to mapOf<String, Any>("checkNow" to true), last())
        client.followChanged(checkNow = false); assertEquals("com.onpath.app.FOLLOW_CHANGED" to mapOf<String, Any>("checkNow" to false), last())
        client.navigating(true); assertEquals("com.onpath.app.NAVIGATING" to mapOf<String, Any>("on" to true), last())
        client.navigating(false); assertEquals("com.onpath.app.NAVIGATING" to mapOf<String, Any>("on" to false), last())
        client.settingsChanged(); assertEquals("com.onpath.app.SETTINGS" to emptyMap<String, Any>(), last())
        client.checkNow(); assertEquals("com.onpath.app.CHECK_NOW" to emptyMap<String, Any>(), last())
        client.say(RecordingService.SAY_ARRIVED); assertEquals("com.onpath.app.SAY" to mapOf<String, Any>("say" to "arrived"), last())
        client.proposeSwitch(1234.5); assertEquals("com.onpath.app.PROPOSE_SWITCH" to mapOf<String, Any>("timeS" to 1234.5), last())
        val answers = listOf<Pair<(Int) -> Unit, String>>(
            client::takeRoute to "TAKE_ROUTE", client::takeVia to "TAKE_VIA", client::keepRoute to "KEEP_ROUTE",
            client::leadThere to "LEAD_THERE", client::justRecord to "JUST_RECORD", client::joinHere to "JOIN_HERE",
            client::leadFailedOk to "LEAD_FAILED_OK")
        answers.forEachIndexed { i, (answer, action) ->
            answer(40 + i)
            assertEquals("com.onpath.app.$action" to mapOf<String, Any>("question" to 40 + i), last())
        }
    }

    @Test fun everyActionTheServiceKnowsHasItsCommandAndTheOtherWayRound() {
        val svc = File("src/main/java/com/onpath/app/recording/RecordingService.kt").readText()
        val declared = Regex("""const val (ACTION_\w+) = "com\.onpath\.app\.""").findAll(svc).map { it.groupValues[1] }.toSet()
        assertEquals("the service's actions", 17, declared.size)
        val contract = File("src/main/java/com/onpath/app/recording/RecordingClient.kt").readText()
        assertEquals("actions without a typed command", emptySet<String>(), declared.filterNot { contract.contains("RecordingService.$it") }.toSet())
        // … and each is taken by onStartCommand: a control action in its set and its branch (START is the fall-through)
        val control = svc.substringAfter("val control = intent?.action in setOf(").substringBefore("\n        //")
        val branches = svc.substringAfter("when (intent?.action) {").substringBefore("// Already recording")
        for (a in declared - "ACTION_START") {
            assertTrue("$a is a control action", Regex("""\b$a\b""").containsMatchIn(control))
            assertTrue("$a has its branch", branches.contains("$a ->"))
        }
    }

    @Test fun theTourControllersFourCommandsGoThroughTheClient() {
        val r = tourRecorder(client)
        r.start("BIKE_TOURING", 3); r.pause(true); r.pause(false); r.continueTour(9); r.stop()
        assertEquals(listOf("START", "PAUSE", "RESUME", "START", "STOP"), client.actions)
        assertEquals(mapOf<String, Any>("continueId" to 9L), client.sent[3].extras)
    }

    @Test fun theAppSaysSeenAndOnlyTheServiceSideSetsTheValues() {
        client.finish(12, parts = listOf(13, 14))
        assertEquals(12L, client.finished.value)
        client.finishedSeen()
        assertNull(client.finished.value)
        assertEquals(listOf(13L, 14L), client.takeFinishedParts())
        assertEquals("handed out once", emptyList<Long>(), client.takeFinishedParts())
        client.fail("no permission"); client.startFailedSeen()
        assertNull(client.startFailed.value)
    }

    // ---- the rule, in the source ----

    private fun sources() = Sources.kotlinFiles().asSequence()

    @Test fun outsideRecordingNobodyBuildsAServiceIntentOrWritesItsFlows() {
        val outside = sources().filter { !it.path.replace('\\', '/').contains("/recording/") }.toList()
        assertTrue("sources found", outside.size > 100)
        val bad = Regex("""RecordingService\.(send\(|ACTION_|EXTRA_|finishedParts|\w+\.value\s*=[^=])""")
        val hits = outside.flatMap { f -> f.readLines().filter { bad.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" } }
        assertEquals("the app talks to the service through RecordingClient", emptyList<String>(), hits)
    }

    @Test fun theServicesFlowsAreReadOnlyOutsideIt() {
        val svc = sourceFile("recording/RecordingService.kt").readText()
        val open = Regex("""^\s*(?!private )(?:@Volatile )?(?:internal )?va[lr] \w+\s*(?::\s*MutableStateFlow|=\s*MutableStateFlow)""", RegexOption.MULTILINE)
        assertEquals("a MutableStateFlow the app could write", emptyList<String>(), open.findAll(svc).map { it.value.trim() }.toList())
        assertTrue(svc.contains("val state: StateFlow<RecState?> = _state"))
        assertTrue(svc.contains("val finished: StateFlow<Long?> = _finished"))
        assertTrue(svc.contains("val startFailed: StateFlow<String?> = _startFailed"))
        assertTrue("no public var in the companion for the app to set", !Regex("""^\s*@Volatile var """, RegexOption.MULTILINE).containsMatchIn(svc.substringAfter("companion object {")))
    }

    @Test fun theFollowedRouteFilesHaveOneStore() {
        // nav-session.json / follow.json: only FollowStore opens them in the app (NavSession.kt and data/FollowFile.kt hold the two file codecs)
        val opens = Regex("""NavSessionStore\(|FollowFile\(|"follow\.json"|NavSession\.FILE""")
        val hits = sources().filter { it.name !in setOf("FollowStore.kt", "FollowFile.kt", "NavSession.kt") }
            .flatMap { f -> f.readLines().filter { l -> opens.containsMatchIn(l) && !l.trim().startsWith("*") && !l.trim().startsWith("//") }.map { "${f.name}: ${it.trim()}" } }.toList()
        assertEquals("a second way to the followed route's files", emptyList<String>(), hits)
    }
}
