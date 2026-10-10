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
import io.github.nithv.braindump.data.CaptureEntity
import io.github.nithv.braindump.data.DraftStore
import io.github.nithv.braindump.data.DumpResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InboxRow(val id: String, val text: String, val createdAt: Long)

/** What the Home screen shows. `null` inbox means "still loading" (so we don't flash "empty"). */
data class HomeUiState(val inbox: List<InboxRow>? = null)

/** One-off messages for the screen (snackbars), as opposed to state that persists. */
sealed interface HomeEvent {
    data object Saved : HomeEvent
    data class SaveFailed(val reason: String) : HomeEvent
    data class Cleared(val id: String) : HomeEvent
}

class HomeViewModel(
    private val repository: BrainDumpRepository,
    private val drafts: DraftStore,
) : ViewModel() {

    /** What is in the Dump box right now. Restored from disk, so it survives the app being closed. */
    var draft by mutableStateOf(drafts.load())
        private set

    fun updateDraft(text: String) {
        draft = text
        drafts.save(text)
    }

    val state: StateFlow<HomeUiState> = repository.inbox
        .map { rows -> HomeUiState(rows.map(CaptureEntity::toRow)) }
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainDumpApp
                HomeViewModel(app.repository, app.drafts)
            }
        }
    }
}

private fun CaptureEntity.toRow() = InboxRow(id = id, text = rawText, createdAt = createdAt)
