package io.github.nithv.braindump.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.PileItem
import io.github.nithv.braindump.data.Kind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class PileRow(
    val id: String,
    val title: String,
    val due: LocalDateTime?,
    val done: Boolean,
    val dueAtMs: Long?,
    val photo: File?,
)

/** `null` lists mean "still loading". */
data class PileUiState(val open: List<PileRow>? = null, val done: List<PileRow> = emptyList())

sealed interface PileEvent {
    data class Archived(val ids: List<String>) : PileEvent
    data class Moved(val id: String, val to: Kind, val dueAtMs: Long?) : PileEvent
    data object Failed : PileEvent
}

class PileViewModel(
    private val repository: BrainDumpRepository,
    val kind: Kind,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    val state: StateFlow<PileUiState> = repository.pile(kind)
        .map { items ->
            // Reminders read best soonest-first (undated last); other piles newest-first, as stored.
            val ordered = if (kind == Kind.REMINDER) {
                items.sortedWith(compareBy<PileItem> { it.item.dueAt == null }.thenBy { it.item.dueAt })
            } else {
                items
            }
            val rows = ordered.map { it.toRow() }
            PileUiState(open = rows.filter { !it.done }, done = rows.filter { it.done })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PileUiState())

    private val _events = Channel<PileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun change(block: suspend () -> PileEvent?) {
        viewModelScope.launch {
            val event = try {
                block()
            } catch (e: Exception) {
                PileEvent.Failed
            }
            event?.let { _events.send(it) }
        }
    }

    fun toggleDone(row: PileRow) = change { repository.setDone(row.id, !row.done); null }

    fun archive(ids: List<String>) = change { repository.setArchived(ids, true); PileEvent.Archived(ids) }

    fun unarchive(ids: List<String>) = change { repository.setArchived(ids, false); null }

    fun move(row: PileRow, to: Kind) = change { repository.move(row.id, to); PileEvent.Moved(row.id, to, row.dueAtMs) }

    fun unmove(id: String, dueAtMs: Long?) = change { repository.unmove(id, kind, dueAtMs); null }

    fun setDue(row: PileRow, due: LocalDateTime?) = change { repository.setDue(row.id, due); null }

    private fun PileItem.toRow() = PileRow(
        id = item.id,
        title = item.title,
        due = item.dueAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone()) },
        done = item.doneAt != null,
        dueAtMs = item.dueAt,
        photo = photoFile?.let(repository::photoFile),
    )
}

/** Open items per pile, for the Piles tiles. */
class PilesViewModel(repository: BrainDumpRepository) : ViewModel() {
    val counts: StateFlow<Map<Kind, Int>?> = repository.pileCounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
