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

    override fun send(command: RecordingCommand) { sent += command }
    override fun finishedSeen() { _finished.value = null }
    override fun startFailedSeen() { _startFailed.value = null }
    override fun takeFinishedParts(): List<Long> = parts.also { parts = emptyList() }

    // --- the service's side, played by the test ---
    fun publish(s: RecState?) { _state.value = s }
    /** The tour ended: the split-off [parts] are set before the id, as the service does. */
    fun finish(tourId: Long, parts: List<Long> = emptyList()) { this.parts = parts; _state.value = null; _finished.value = tourId }
    fun fail(why: String) { _startFailed.value = why }
}
