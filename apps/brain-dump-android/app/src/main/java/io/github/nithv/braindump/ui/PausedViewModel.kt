package io.github.nithv.braindump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.CheckpointEntity
import io.github.nithv.braindump.data.VOICE_PLACEHOLDER
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

/** After a week, a paused thing asks "still relevant?" (same as the website). */
const val STALE_AFTER_MS = 7 * 24 * 60 * 60 * 1000L

data class CheckpointRow(
    val id: String,
    /** What you were up to: typed, transcribed, or "🎙️ Voice note" while the words are on their way. */
    val body: String,
    val nextStep: String?,
    val tag: String?,
    val link: String?,
    /** "docs.google.com", for the link button. */
    val linkHost: String?,
    val audio: File?,
    val transcribing: Boolean,
    val createdAt: Long,
    /** Paused (or last kept) over a week ago. */
    val stale: Boolean,
    val resumedAt: Long?,
)

/** `null` active list means "still loading". */
data class PausedUiState(val active: List<CheckpointRow>? = null, val resumed: List<CheckpointRow> = emptyList())

sealed interface PausedEvent {
    data class Resumed(val id: String) : PausedEvent
    data class LetGo(val id: String) : PausedEvent
    data object Kept : PausedEvent
    data object Failed : PausedEvent
}

/** The Paused screen and Home's "Where you left off" card share this one. */
class PausedViewModel(
    private val repository: BrainDumpRepository,
    pendingTranscripts: Flow<Set<String>> = flowOf(emptySet()),
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<PausedUiState> = combine(
        repository.activeCheckpoints,
        repository.recentlyResumed,
        pendingTranscripts,
    ) { active, resumed, pending ->
        val now = clock()
        PausedUiState(active = active.map { it.toRow(now, pending) }, resumed = resumed.map { it.toRow(now, pending) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PausedUiState())

    /** Just the newest, for Home. */
    val latest: StateFlow<Pair<CheckpointRow, Int>?> = state
        .map { s -> s.active?.firstOrNull()?.let { it to s.active.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<PausedEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun change(block: suspend () -> PausedEvent?) {
        viewModelScope.launch {
            val event = try {
                block()
            } catch (e: Exception) {
                PausedEvent.Failed
            }
            event?.let { _events.send(it) }
        }
    }

    fun resume(id: String) = change { repository.resume(id); PausedEvent.Resumed(id) }

    fun unresume(id: String) = change { repository.unresume(id); null }

    fun letGo(id: String) = change { repository.dismiss(id); PausedEvent.LetGo(id) }

    fun undoLetGo(id: String) = change { repository.undismiss(id); null }

    fun keep(id: String) = change { repository.keep(id); PausedEvent.Kept }

    private fun CheckpointEntity.toRow(now: Long, pending: Set<String>): CheckpointRow {
        val audio = audioFile?.let(repository::voiceFile)
        return CheckpointRow(
            id = id,
            body = transcript.ifBlank { label ?: if (audio != null) VOICE_PLACEHOLDER else "" },
            nextStep = nextStep,
            tag = projectTag,
            link = link,
            linkHost = link?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() },
            audio = audio,
            transcribing = id in pending,
            createdAt = createdAt,
            stale = now - updatedAt > STALE_AFTER_MS,
            resumedAt = resumedAt,
        )
    }
}
