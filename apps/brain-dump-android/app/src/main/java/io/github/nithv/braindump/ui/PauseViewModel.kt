package io.github.nithv.braindump.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nithv.braindump.BrainDumpApp
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.voice.Recorder
import io.github.nithv.braindump.voice.Transcriptions
import io.github.nithv.braindump.voice.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The Pause note's recording, if any. */
sealed interface PauseRecording {
    data object None : PauseRecording
    data class Recording(val id: String) : PauseRecording
    /** Recorded and waiting (as a pending file) for "Save my place". */
    data class Done(val id: String, val file: File) : PauseRecording
}

sealed interface PauseEvent {
    data object Saved : PauseEvent
}

/**
 * The Pause sheet: "Where are you up to?", "Next, I need to…", an optional voice note, tag and link.
 *
 * Everything typed is kept in [SavedStateHandle], so if Android closes the app while you're in
 * another one, the sheet comes back as you left it. A recording stays a pending file until you
 * save; Cancel deletes it, and if the app dies first, the clean-up job removes it an hour later.
 */
class PauseViewModel(
    private val repository: BrainDumpRepository,
    private val transcriptions: Transcriptions?,
    private val recorder: Recorder,
    private val saveScope: CoroutineScope,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    var isOpen by mutableStateOf(saved.get<Boolean>(KEY_OPEN) ?: false)
        private set
    var whereText by mutableStateOf(saved.get<String>(KEY_WHERE).orEmpty())
        private set
    var nextStep by mutableStateOf(saved.get<String>(KEY_NEXT).orEmpty())
        private set
    var tag by mutableStateOf(saved.get<String>(KEY_TAG).orEmpty())
        private set
    var link by mutableStateOf(saved.get<String>(KEY_LINK).orEmpty())
        private set

    var recording by mutableStateOf(restoredRecording())
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var saving by mutableStateOf(false)
        private set
    /** Shown in the sheet; nothing typed or recorded is lost when this appears. */
    var error by mutableStateOf<String?>(null)
        private set
    var micDenied by mutableStateOf(false)
        private set

    val canSave: Boolean
        get() = !saving && recording !is PauseRecording.Recording &&
            (whereText.isNotBlank() || nextStep.isNotBlank() || recording is PauseRecording.Done)

    private val _events = Channel<PauseEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var ticker: Job? = null

    /** A recording that was finished before the app was closed is still on disk; one cut off mid-way isn't usable. */
    private fun restoredRecording(): PauseRecording {
        val id = saved.get<String>(KEY_RECORDED) ?: return PauseRecording.None
        val file = repository.voice?.pendingFile(id)?.takeIf { it.isFile && it.length() > 0 } ?: return PauseRecording.None
        return PauseRecording.Done(id, file)
    }

    fun open() {
        isOpen = true
        saved[KEY_OPEN] = true
    }

    /** Swiped away: closed, but everything is kept for next time. Only Cancel throws it away. */
    fun hide() {
        isOpen = false
        saved[KEY_OPEN] = false
    }

    fun updateWhere(text: String) {
        whereText = text
        saved[KEY_WHERE] = text
    }

    fun updateNext(text: String) {
        nextStep = text
        saved[KEY_NEXT] = text
    }

    fun updateTag(text: String) {
        tag = text
        saved[KEY_TAG] = text
    }

    fun updateLink(text: String) {
        link = text
        saved[KEY_LINK] = text
    }

    fun permissionDenied() {
        micDenied = true
    }

    /** Starts (or redoes) the voice note. Call once the microphone permission is granted. */
    fun startRecording() {
        if (recording is PauseRecording.Recording || saving) return
        discardRecording()
        micDenied = false
        error = null
        val id = repository.newCheckpointId()
        val file = checkNotNull(repository.voice).pendingFile(id)
        try {
            recorder.start(file, onLimit = ::stopRecording)
        } catch (e: Exception) {
            file.delete()
            error = "Couldn't use the microphone. Is another app recording? You can still type."
            return
        }
        recording = PauseRecording.Recording(id)
        elapsedMs = 0
        ticker = viewModelScope.launch {
            while (isActive) {
                elapsedMs = recorder.elapsedMs
                level = recorder.level()
                delay(100)
            }
        }
    }

    /** Stop button, the time limit, or the app going to the background: keep what we have. */
    fun stopRecording() {
        val current = recording as? PauseRecording.Recording ?: return
        ticker?.cancel()
        level = 0f
        val file = checkNotNull(repository.voice).pendingFile(current.id)
        if (recorder.stop()) {
            recording = PauseRecording.Done(current.id, file)
            saved[KEY_RECORDED] = current.id
        } else {
            file.delete()
            recording = PauseRecording.None
            error = "That was too short to keep. Hold on a moment longer."
        }
    }

    fun save() {
        if (!canSave) return
        saving = true
        error = null
        val recorded = recording as? PauseRecording.Done
        val id = recorded?.id ?: repository.newCheckpointId()
        val where = whereText
        // The app's scope, not the screen's: closing the sheet mid-save must not lose the bookmark.
        saveScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    repository.pause(id, where, nextStep, tag, link, withRecording = recorded != null)
                }
            }.isSuccess
            // Spoken but not typed? Let the phone fill in "where was I" from the recording.
            if (ok && recorded != null && where.isBlank()) transcriptions?.enqueue(id)
            withContext(Dispatchers.Main) {
                saving = false
                if (ok) {
                    reset()
                    _events.trySend(PauseEvent.Saved)
                } else {
                    error = "Couldn't save. Try again. Nothing you entered is lost."
                }
            }
        }
    }

    /** Cancel: throw the note away, recording included. */
    fun cancel() {
        if (saving) return
        if (recording is PauseRecording.Recording) {
            ticker?.cancel()
            recorder.stop()
        }
        discardRecording()
        reset()
    }

    private fun discardRecording() {
        when (val r = recording) {
            is PauseRecording.Recording -> repository.voice?.discard(r.id)
            is PauseRecording.Done -> repository.voice?.discard(r.id)
            PauseRecording.None -> Unit
        }
        recording = PauseRecording.None
        saved.remove<String>(KEY_RECORDED)
    }

    private fun reset() {
        recording = PauseRecording.None
        listOf(KEY_OPEN, KEY_WHERE, KEY_NEXT, KEY_TAG, KEY_LINK, KEY_RECORDED).forEach { saved.remove<Any>(it) }
        isOpen = false
        whereText = ""
        nextStep = ""
        tag = ""
        link = ""
        error = null
        micDenied = false
    }

    override fun onCleared() {
        // Leaving for good mid-recording: finish the file so it's still there if the sheet comes back.
        stopRecording()
    }

    companion object {
        private const val KEY_OPEN = "pause.open"
        private const val KEY_WHERE = "pause.where"
        private const val KEY_NEXT = "pause.next"
        private const val KEY_TAG = "pause.tag"
        private const val KEY_LINK = "pause.link"
        private const val KEY_RECORDED = "pause.recorded"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainDumpApp
                PauseViewModel(app.repository, app.transcriptions, VoiceRecorder(app), app.appScope, createSavedStateHandle())
            }
        }
    }
}
