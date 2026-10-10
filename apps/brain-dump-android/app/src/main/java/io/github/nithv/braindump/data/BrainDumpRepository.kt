package io.github.nithv.braindump.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Same limit as the website (apps/brain-dump/src/lib/limits.ts). */
const val MAX_CAPTURE_LENGTH = 5000

sealed interface DumpResult {
    data class Saved(val id: String) : DumpResult
    data object Empty : DumpResult
    data object TooLong : DumpResult
}

/**
 * The ONLY code that writes Brain Dump's data (design doc §3, "one writer, many readers").
 * Screens and widgets read through it and ask it to change things, so rules like
 * "never lose a dump" live in exactly one place.
 */
class BrainDumpRepository(
    private val db: BrainDumpDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val captures = db.captures()

    val inbox: Flow<List<CaptureEntity>> = captures.inbox()

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
}
