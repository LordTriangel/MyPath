package com.onpath.app.recording

import android.content.Context
import android.content.Intent
import com.onpath.app.debug.Problems
import kotlinx.coroutines.flow.StateFlow

/** The Intent for the service, exactly action + extras. */
fun RecordingCommand.intent(context: Context): Intent = Intent(context, RecordingService::class.java).setAction(action).also { i ->
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
