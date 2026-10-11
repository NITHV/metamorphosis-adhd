package io.github.nithv.braindump.data

import androidx.room.withTransaction
import io.github.nithv.braindump.sort.findDate
import io.github.nithv.braindump.sort.guessKind
import io.github.nithv.braindump.sort.splitDump
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Same limit as the website (apps/brain-dump/src/lib/limits.ts). */
const val MAX_CAPTURE_LENGTH = 5000

private const val DAY_MS = 24 * 60 * 60 * 1000L

sealed interface DumpResult {
    data class Saved(val id: String) : DumpResult
    data object Empty : DumpResult
    data object TooLong : DumpResult
}

/** The dump was already sorted, cleared or split (e.g. by a double tap), so there's nothing to do. */
class NotInInboxException(id: String) : IllegalStateException("Capture $id isn't in the inbox")

/**
 * The ONLY code that writes Brain Dump's data (design doc §3, "one writer, many readers").
 * Screens and widgets read through it and ask it to change things, so rules like
 * "never lose a dump" live in exactly one place.
 */
class BrainDumpRepository(
    private val db: BrainDumpDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val captures = db.captures()
    private val items = db.items()

    val inbox: Flow<List<CaptureEntity>> = captures.inbox()

    /** Open items per pile (all four piles, zeros included). */
    val pileCounts: Flow<Map<Kind, Int>> = items.openCounts().map { rows ->
        Kind.entries.associateWith { kind -> rows.firstOrNull { it.kind == kind }?.n ?: 0 }
    }

    /** One pile, plus anything ticked off in the last day (so finishing something feels good). */
    fun pile(kind: Kind): Flow<List<ItemEntity>> = items.pile(kind, clock() - DAY_MS)

    /** The phone's wall-clock time, for reading "tomorrow 5pm" the way the person meant it. */
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), zone())

    private fun LocalDateTime.toEpochMs(): Long = atZone(zone()).toInstant().toEpochMilli()

    /**
     * Saves a text dump. Returns only after the row is committed to the database, so the UI may
     * say "Saved ✓" the moment this returns. If it throws, nothing was saved and the caller must
     * give the text back to the user.
     */
    suspend fun dumpText(text: String): DumpResult {
        val rawText = text.trim()
        if (rawText.isEmpty()) return DumpResult.Empty
        if (rawText.length > MAX_CAPTURE_LENGTH) return DumpResult.TooLong
        return SloTimer.measure("save_text", SloTimer.SAVE_TEXT_TARGET_MS) {
            val now = clock()
            val capture = CaptureEntity(
                id = newId(),
                rawText = rawText,
                source = CaptureSource.TEXT,
                status = CaptureStatus.INBOX,
                suggestedKind = guessKind(rawText, localNow()),
                createdAt = now,
                updatedAt = now,
            )
            captures.insert(capture)
            DumpResult.Saved(capture.id)
        }
    }

    /** "Clear from inbox": archived, not deleted, so Undo can bring it back. */
    suspend fun clear(id: String) {
        captures.setStatus(id, CaptureStatus.ARCHIVED, clock())
    }

    /** Undo for [clear]. */
    suspend fun restore(id: String) {
        captures.setStatus(id, CaptureStatus.INBOX, clock())
    }

    /**
     * Files an inbox dump into a pile. Two writes, "dump is sorted" and "item exists", in ONE
     * transaction: both happen or neither, so a crash can never leave a dump in the Inbox *and* a
     * pile, or in neither (design doc §5 D). Tasks and reminders pick up any date in the text.
     */
    suspend fun sort(captureId: String, kind: Kind): String = db.withTransaction {
        val capture = captures.get(captureId)?.takeIf { it.status == CaptureStatus.INBOX }
            ?: throw NotInInboxException(captureId)
        val now = clock()
        captures.setStatus(captureId, CaptureStatus.SORTED, now)
        val due = if (kind.hasDates) findDate(capture.rawText, localNow())?.toEpochMs() else null
        val item = ItemEntity(
            id = newId(),
            captureId = captureId,
            kind = kind,
            title = capture.rawText,
            dueAt = due,
            createdAt = now,
            updatedAt = now,
        )
        items.insert(item)
        item.id
    }

    /** Undo for [sort]: removes the filed item and puts the dump back in the Inbox, together. */
    suspend fun unsort(captureId: String) {
        db.withTransaction {
            items.deleteForCapture(captureId)
            captures.setStatus(captureId, CaptureStatus.INBOX, clock())
        }
    }

    /** Whether [splitDump] would turn this dump into several (photos never split). */
    fun canSplit(capture: CaptureEntity): Boolean = capture.photoFile == null && splitDump(capture.rawText).size > 1

    /**
     * Turns one long dump into separate Inbox dumps (one per line or sentence) and archives the
     * original, in one transaction. Returns how many dumps it became.
     */
    suspend fun split(captureId: String): Int = db.withTransaction {
        val capture = captures.get(captureId)?.takeIf { it.status == CaptureStatus.INBOX }
            ?: throw NotInInboxException(captureId)
        require(canSplit(capture)) { "Nothing to split" }
        val parts = splitDump(capture.rawText)
        val now = clock()
        val local = localNow()
        // The Inbox is newest first, so earlier parts get slightly later times to keep reading order.
        captures.insertAll(
            parts.mapIndexed { i, rawText ->
                CaptureEntity(
                    id = newId(),
                    rawText = rawText,
                    source = CaptureSource.TEXT,
                    status = CaptureStatus.INBOX,
                    suggestedKind = guessKind(rawText, local),
                    createdAt = capture.createdAt + (parts.size - i),
                    updatedAt = now,
                )
            },
        )
        captures.setStatus(captureId, CaptureStatus.ARCHIVED, now)
        parts.size
    }

    // --- Piles -----------------------------------------------------------------------------------

    suspend fun setDone(itemId: String, done: Boolean) {
        val now = clock()
        items.setDone(itemId, if (done) now else null, now)
    }

    suspend fun setArchived(itemIds: List<String>, archived: Boolean) {
        if (itemIds.isEmpty()) return
        val now = clock()
        items.setArchived(itemIds, if (archived) now else null, now)
    }

    /** Moves an item to another pile. Ideas and worries never carry a date, so it's dropped. */
    suspend fun move(itemId: String, to: Kind) {
        val item = items.get(itemId) ?: return
        items.move(itemId, to, if (to.hasDates) item.dueAt else null, clock())
    }

    /** Undo for [move]: back to the old pile with the old date, exactly as it was. */
    suspend fun unmove(itemId: String, from: Kind, dueAt: Long?) {
        items.move(itemId, from, dueAt, clock())
    }

    suspend fun setDue(itemId: String, dueAt: LocalDateTime?) {
        items.setDue(itemId, dueAt?.toEpochMs(), clock())
    }
}

/** Only tasks and reminders have dates (same rule as the website). */
val Kind.hasDates: Boolean get() = this == Kind.TASK || this == Kind.REMINDER
