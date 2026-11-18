package com.onpath.app.recording

import android.content.Context
import android.content.Intent
import com.onpath.app.debug.Problems
import kotlinx.coroutines.flow.StateFlow

/**
 * One message to [RecordingService]: the Intent's action and its extras, Android-free – so what the app sends is a
 * value a JVM test can compare (RecordingClientTest pins every action and extra). The notification's buttons are the
 * same commands ([RecordingService] builds its PendingIntents from them).
 */
data class RecordingCommand(val action: String, val extras: Map<String, Any> = emptyMap()) {

    /** The Intent for the service, exactly action + extras. */
    fun intent(context: Context): Intent = Intent(context, RecordingService::class.java).setAction(action).also { i ->
        extras.forEach { (k, v) ->
            when (v) {
                is String -> i.putExtra(k, v)
                is Int -> i.putExtra(k, v)
                is Long -> i.putExtra(k, v)
                is Boolean -> i.putExtra(k, v)
                is Double -> i.putExtra(k, v)
                else -> Problems.error("recording", "Service command $action: the extra \"$k\" has a type that is not carried; sent without it")
            }
        }
    }

    companion object {
        fun start(activity: String, sac: Int) = RecordingCommand(RecordingService.ACTION_START,
            mapOf(RecordingService.EXTRA_ACTIVITY to activity, RecordingService.EXTRA_SAC to sac))
        fun continueTour(tourId: Long) = RecordingCommand(RecordingService.ACTION_START, mapOf(RecordingService.EXTRA_CONTINUE_ID to tourId))
        val pause = RecordingCommand(RecordingService.ACTION_PAUSE)
        val resume = RecordingCommand(RecordingService.ACTION_RESUME)
        val stop = RecordingCommand(RecordingService.ACTION_STOP)
        /** [checkNow] null = no extra (the service reads "false"). */
        fun followChanged(checkNow: Boolean? = null) = RecordingCommand(RecordingService.ACTION_FOLLOW_CHANGED,
            if (checkNow == null) emptyMap() else mapOf(RecordingService.EXTRA_CHECK_NOW to checkNow))
        fun navigating(on: Boolean) = RecordingCommand(RecordingService.ACTION_NAVIGATING, mapOf(RecordingService.EXTRA_ON to on))
        val settingsChanged = RecordingCommand(RecordingService.ACTION_SETTINGS)
        val checkNow = RecordingCommand(RecordingService.ACTION_CHECK_NOW)
        fun say(what: String) = RecordingCommand(RecordingService.ACTION_SAY, mapOf(RecordingService.EXTRA_SAY to what))
        fun proposeSwitch(timeS: Double) = RecordingCommand(RecordingService.ACTION_PROPOSE_SWITCH, mapOf(RecordingService.EXTRA_TIME_S to timeS))
        // answers to the question [questionId]
        fun takeRoute(questionId: Int) = answer(RecordingService.ACTION_TAKE_ROUTE, questionId)
        fun takeVia(questionId: Int) = answer(RecordingService.ACTION_TAKE_VIA, questionId)
        fun keepRoute(questionId: Int) = answer(RecordingService.ACTION_KEEP_ROUTE, questionId)
        fun leadThere(questionId: Int) = answer(RecordingService.ACTION_LEAD_THERE, questionId)
        fun justRecord(questionId: Int) = answer(RecordingService.ACTION_JUST_RECORD, questionId)
        fun joinHere(questionId: Int) = answer(RecordingService.ACTION_JOIN_HERE, questionId)
        fun leadFailedOk(questionId: Int) = answer(RecordingService.ACTION_LEAD_FAILED_OK, questionId)
        private fun answer(action: String, questionId: Int) = RecordingCommand(action, mapOf(RecordingService.EXTRA_QUESTION to questionId))
    }
}

/**
 * The contract between the app (view model, state holders) and the recording service: what the app may ask of the
 * service – one function per Intent action – and what it reads from it. The service is the only one that writes
 * [state], [finished] and [startFailed]; the app says "seen", it never clears the value itself.
 * [ServiceRecordingClient] is the app's; tests take a fake (testkit).
 *
 * A command does not start a service that is not recording (except [start] / [continueTour]): the service stops
 * itself again. Callers that sent only during a tour still ask [state] first.
 */
interface RecordingClient {
    /** The running tour (null = none). */
    val state: StateFlow<RecState?>
    /** Id of the last tour stopped; the app shows its summary and says [finishedSeen]. */
    val finished: StateFlow<Long?>
    /** Why the service stopped by itself (plain text); the app shows it and says [startFailedSeen]. */
    val startFailed: StateFlow<String?>
    fun finishedSeen()
    fun startFailedSeen()
    /** Tours split off the last stopped tour ("Split into two tours"), handed out once. */
    fun takeFinishedParts(): List<Long>

    fun send(command: RecordingCommand)

    fun start(activity: String, sac: Int) = send(RecordingCommand.start(activity, sac))
    fun continueTour(tourId: Long) = send(RecordingCommand.continueTour(tourId))
    fun pause(pause: Boolean) = send(if (pause) RecordingCommand.pause else RecordingCommand.resume)
    fun stop() = send(RecordingCommand.stop)
    /** What is followed changed on disk (FollowStore): read it again. [checkNow]: check the next fix at once. */
    fun followChanged(checkNow: Boolean? = null) = send(RecordingCommand.followChanged(checkNow))
    /** The app is in Navigate ([on]) or in Plan. */
    fun navigating(on: Boolean) = send(RecordingCommand.navigating(on))
    fun settingsChanged() = send(RecordingCommand.settingsChanged)
    /** "Weiter geht's" back on the map: check the next fix at once. */
    fun checkNow() = send(RecordingCommand.checkNow)
    /** Speak an app event ([RecordingService.SAY_ARRIVED] / [RecordingService.SAY_NO_DESTINATION]). */
    fun say(what: String) = send(RecordingCommand.say(what))
    /** Activity check: a proposed session waits in the hand-over file (FollowStore.propose); [timeS] = its time. */
    fun proposeSwitch(timeS: Double) = send(RecordingCommand.proposeSwitch(timeS))
    fun takeRoute(questionId: Int) = send(RecordingCommand.takeRoute(questionId))
    fun takeVia(questionId: Int) = send(RecordingCommand.takeVia(questionId))
    fun keepRoute(questionId: Int) = send(RecordingCommand.keepRoute(questionId))
    fun leadThere(questionId: Int) = send(RecordingCommand.leadThere(questionId))
    fun justRecord(questionId: Int) = send(RecordingCommand.justRecord(questionId))
    fun joinHere(questionId: Int) = send(RecordingCommand.joinHere(questionId))
    fun leadFailedOk(questionId: Int) = send(RecordingCommand.leadFailedOk(questionId))
}

/** The app's client: every command is the Intent it always was, delivered by [RecordingService.send]. */
class ServiceRecordingClient(context: Context) : RecordingClient {
    private val app = context.applicationContext
    override val state: StateFlow<RecState?> get() = RecordingService.state
    override val finished: StateFlow<Long?> get() = RecordingService.finished
    override val startFailed: StateFlow<String?> get() = RecordingService.startFailed
    override fun finishedSeen() = RecordingService.finishedSeen()
    override fun startFailedSeen() = RecordingService.startFailedSeen()
    override fun takeFinishedParts(): List<Long> = RecordingService.takeFinishedParts()
    override fun send(command: RecordingCommand) = RecordingService.send(app, command)
}
