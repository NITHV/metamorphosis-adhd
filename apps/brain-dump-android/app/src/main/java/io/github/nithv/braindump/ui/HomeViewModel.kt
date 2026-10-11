package io.github.nithv.braindump.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nithv.braindump.BrainDumpApp
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.DraftStore
import io.github.nithv.braindump.data.DumpResult
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.data.NotInInboxException
import io.github.nithv.braindump.sort.findDate
import io.github.nithv.braindump.sort.guessKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime

data class InboxRow(
    val id: String,
    val text: String,
    val createdAt: Long,
    /** The chip to highlight. Only a hint: any chip can be tapped. */
    val suggested: Kind?,
    /** A date spotted in the text; tasks and reminders keep it when filed. */
    val due: LocalDateTime?,
    val canSplit: Boolean,
    /** The photo of a photo dump. */
    val photo: File?,
    /** The recording of a voice dump. */
    val audio: File? = null,
    /** A voice note whose words are still being worked out. */
    val transcribing: Boolean = false,
)

/** What the Home screen shows. `null` inbox means "still loading" (so we don't flash "empty"). */
data class HomeUiState(val inbox: List<InboxRow>? = null)

/** One-off messages for the screen (snackbars), as opposed to state that persists. */
sealed interface HomeEvent {
    data object Saved : HomeEvent
    data class SaveFailed(val reason: String) : HomeEvent
    data class Cleared(val id: String) : HomeEvent
    data class Filed(val captureId: String, val kind: Kind) : HomeEvent
    data class Split(val parts: Int) : HomeEvent
    data class Failed(val message: String) : HomeEvent
}

class HomeViewModel(
    private val repository: BrainDumpRepository,
    private val drafts: DraftStore,
    /** Voice notes still being transcribed (ids). */
    pendingTranscripts: Flow<Set<String>> = flowOf(emptySet()),
) : ViewModel() {

    /** What is in the Dump box right now. Restored from disk, so it survives the app being closed. */
    var draft by mutableStateOf(drafts.load())
        private set

    fun updateDraft(text: String) {
        draft = text
        drafts.save(text)
    }

    val state: StateFlow<HomeUiState> = repository.inbox
        .combine(pendingTranscripts) { rows, pending -> rows to pending }
        .map { (rows, pending) ->
            val now = repository.localNow()
            HomeUiState(
                rows.map { c ->
                    InboxRow(
                        id = c.id,
                        text = c.rawText,
                        createdAt = c.createdAt,
                        suggested = c.suggestedKind ?: guessKind(c.rawText, now),
                        due = findDate(c.rawText, now),
                        canSplit = repository.canSplit(c),
                        photo = c.photoFile?.let(repository::photoFile),
                        audio = c.audioFile?.let(repository::voiceFile),
                        transcribing = c.id in pending,
                    )
                },
            )
        }
        .flowOn(Dispatchers.Default) // text rules run off the main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * Empties the box immediately, so capture feels instant, but the copy on disk is only cleared
     * once the dump is committed to the database. If saving fails, the text goes back in the box.
     * Either way, the thought is always stored somewhere.
     */
    fun dump() {
        val text = draft
        if (text.isBlank()) return
        draft = ""
        viewModelScope.launch {
            val failure = try {
                when (repository.dumpText(text)) {
                    is DumpResult.Saved -> null
                    DumpResult.Empty -> null
                    DumpResult.TooLong -> "That dump is too long"
                }
            } catch (e: Exception) {
                "Couldn't save. Your text is back in the box."
            }
            if (failure == null) {
                if (draft.isEmpty()) drafts.save("") // only now is it safe to forget the draft
                _events.send(HomeEvent.Saved)
            } else {
                if (draft.isBlank()) updateDraft(text) // give it back so nothing is lost
                _events.send(HomeEvent.SaveFailed(failure))
            }
        }
    }

    fun clear(id: String) {
        viewModelScope.launch {
            repository.clear(id)
            _events.send(HomeEvent.Cleared(id))
        }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun sort(id: String, kind: Kind) = attempt("Couldn't file that one. Try again.") {
        repository.sort(id, kind)
        HomeEvent.Filed(id, kind)
    }

    fun unsort(id: String) {
        viewModelScope.launch { repository.unsort(id) }
    }

    fun split(id: String) = attempt("Couldn't split that one. Try again.") {
        HomeEvent.Split(repository.split(id))
    }

    /** Runs a change; a double tap on something already gone is silently ignored, not an error. */
    private fun attempt(failure: String, block: suspend () -> HomeEvent) {
        viewModelScope.launch {
            val event = try {
                block()
            } catch (e: NotInInboxException) {
                null
            } catch (e: Exception) {
                HomeEvent.Failed(failure)
            }
            event?.let { _events.send(it) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainDumpApp
                HomeViewModel(app.repository, app.drafts, app.transcriptions.pending)
            }
        }
    }
}
