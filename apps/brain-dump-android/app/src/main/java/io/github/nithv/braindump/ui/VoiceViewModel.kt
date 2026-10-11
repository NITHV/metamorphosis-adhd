package io.github.nithv.braindump.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nithv.braindump.BrainDumpApp
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.voice.Transcriber
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

sealed interface VoicePhase {
    data object Closed : VoicePhase
    /** Sheet open, waiting for the big button (or for the microphone permission). */
    data object Ready : VoicePhase
    data object PermissionDenied : VoicePhase
    data class Recording(val id: String) : VoicePhase
    data object Saving : VoicePhase
    data class Failed(val message: String) : VoicePhase
}

sealed interface VoiceEvent {
    data class Saved(val transcribing: Boolean) : VoiceEvent
    data object SaveFailed : VoiceEvent
}

/**
 * The voice sheet: record → stop → saved at once → words filled in later, in the background.
 * Saving runs on [saveScope] (the app's scope), so a note is kept even if the screen goes away.
 */
class VoiceViewModel(
    private val repository: BrainDumpRepository,
    private val transcriptions: Transcriptions?,
    private val recorder: Recorder,
    private val saveScope: CoroutineScope,
    private val canTranscribe: () -> Boolean,
) : ViewModel() {

    var phase by mutableStateOf<VoicePhase>(VoicePhase.Closed)
        private set

    var elapsedMs by mutableLongStateOf(0L)
        private set

    var level by mutableFloatStateOf(0f)
        private set

    private val _events = Channel<VoiceEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var ticker: Job? = null

    fun open() {
        if (phase == VoicePhase.Closed || phase is VoicePhase.Failed || phase == VoicePhase.PermissionDenied) phase = VoicePhase.Ready
    }

    fun permissionDenied() {
        phase = VoicePhase.PermissionDenied
    }

    fun start() {
        if (phase != VoicePhase.Ready) return
        val id = repository.newVoiceId()
        val file = checkNotNull(repository.voice).pendingFile(id)
        try {
            recorder.start(file, onLimit = ::stopAndSave)
        } catch (e: Exception) {
            file.delete()
            phase = VoicePhase.Failed("Couldn't use the microphone. Is another app recording?")
            return
        }
        phase = VoicePhase.Recording(id)
        elapsedMs = 0
        ticker = viewModelScope.launch {
            while (isActive) {
                elapsedMs = recorder.elapsedMs
                level = recorder.level()
                delay(100)
            }
        }
    }

    /** Stop button, the 2-minute limit, or the app going to the background: keep what we have. */
    fun stopAndSave() {
        val recording = phase as? VoicePhase.Recording ?: return
        ticker?.cancel()
        level = 0f
        if (!recorder.stop()) {
            repository.voice?.discard(recording.id)
            phase = VoicePhase.Failed("That was too short to save. Hold on a moment longer.")
            return
        }
        phase = VoicePhase.Saving
        saveScope.launch {
            val saved = runCatching { withContext(Dispatchers.IO) { repository.dumpVoice(recording.id) } }.isSuccess
            if (saved) transcriptions?.enqueue(recording.id)
            withContext(Dispatchers.Main) {
                phase = if (saved) VoicePhase.Closed else VoicePhase.Failed("Couldn't save the recording. Try again.")
                _events.trySend(if (saved) VoiceEvent.Saved(transcribing = canTranscribe()) else VoiceEvent.SaveFailed)
            }
        }
    }

    fun cancel() {
        val recording = phase as? VoicePhase.Recording
        if (recording != null) {
            ticker?.cancel()
            recorder.stop()
            repository.voice?.discard(recording.id)
        }
        if (phase != VoicePhase.Saving) phase = VoicePhase.Closed
    }

    override fun onCleared() {
        // Leaving for good mid-recording: keep it, like the Stop button would.
        stopAndSave()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainDumpApp
                VoiceViewModel(
                    repository = app.repository,
                    transcriptions = app.transcriptions,
                    recorder = VoiceRecorder(app),
                    saveScope = app.appScope,
                    canTranscribe = { Transcriber.isSupported(app) },
                )
            }
        }
    }
}
