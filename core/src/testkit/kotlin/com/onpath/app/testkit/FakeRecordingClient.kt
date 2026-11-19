package com.onpath.app.testkit

import com.onpath.app.recording.RecState
import com.onpath.app.recording.RecordingClient
import com.onpath.app.recording.RecordingCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's side of the recording service for a state holder under test (test kit): every command the holder sends
 * is kept in [sent], in order, as the value the real client turns into an Intent; the test plays the service with
 * [publish], [finish] and [fail]. Nothing is recorded here – a holder that needs real tables behind the commands takes
 * [FakeRecording] (the tour's four commands) instead.
 *
 * [onSend] runs inside every [send], after the command is in [sent]: a test reads the state AT THE MOMENT a command goes
 * out (the files on disk, the mode, [state]) – what an order rule is about. [finish] ends a tour in the service's order
 * (`finish()` in RecordingService: the split-off parts, then [finished], then [state] = null; a pin on the service's
 * source keeps this true – RecordingClientTest), so a collector of [finished] sees the tour still running, as in the app.
 */
class FakeRecordingClient : RecordingClient {
    private val _state = MutableStateFlow<RecState?>(null)
    private val _finished = MutableStateFlow<Long?>(null)
    private val _startFailed = MutableStateFlow<String?>(null)
    private var parts: List<Long> = emptyList()

    override val state: StateFlow<RecState?> get() = _state
    override val finished: StateFlow<Long?> get() = _finished
    override val startFailed: StateFlow<String?> get() = _startFailed

    val sent = ArrayList<RecordingCommand>()
    /** The actions sent, without the package prefix ("START", "FOLLOW_CHANGED" …). */
    val actions: List<String> get() = sent.map { it.action.substringAfterLast('.') }

    /** Called with every command after it was kept in [sent]. */
    var onSend: (RecordingCommand) -> Unit = {}

    override fun send(command: RecordingCommand) { sent += command; onSend(command) }
    override fun finishedSeen() { _finished.value = null }
    override fun startFailedSeen() { _startFailed.value = null }
    override fun takeFinishedParts(): List<Long> = parts.also { parts = emptyList() }

    // --- the service's side, played by the test ---
    fun publish(s: RecState?) { _state.value = s }
    /** The tour ended, in the service's order: the split-off [parts], then the id, then the state goes null. */
    fun finish(tourId: Long, parts: List<Long> = emptyList()) { this.parts = parts; _finished.value = tourId; _state.value = null }
    fun fail(why: String) { _startFailed.value = why }
}
