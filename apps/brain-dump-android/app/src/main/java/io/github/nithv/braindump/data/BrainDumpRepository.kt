package io.github.nithv.braindump.data

import androidx.room.withTransaction
import io.github.nithv.braindump.sort.findDate
import io.github.nithv.braindump.sort.guessKind
import io.github.nithv.braindump.sort.splitDump
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Same limit as the website (apps/brain-dump/src/lib/limits.ts). */
const val MAX_CAPTURE_LENGTH = 5000

/** What a photo dump without a caption says (same as the website). */
const val PHOTO_PLACEHOLDER = "📷 Photo"

/** What a voice dump says until (unless) its words are transcribed (same as the website). */
const val VOICE_PLACEHOLDER = "🎙️ Voice note"

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
    /** Where photo files live. Null in tests that never touch photos. */
    val photos: FileStore? = null,
    /** Where voice notes live. Null in tests that never touch voice. */
    val voice: FileStore? = null,
) {
    private val captures = db.captures()
    private val items = db.items()

    val inbox: Flow<List<CaptureEntity>> = captures.inbox()

    /** Open items per pile (all four piles, zeros included). */
    val pileCounts: Flow<Map<Kind, Int>> = items.openCounts().map { rows ->
        Kind.entries.associateWith { kind -> rows.firstOrNull { it.kind == kind }?.n ?: 0 }
    }

    /** One pile, plus anything ticked off in the last day (so finishing something feels good). */
    fun pile(kind: Kind): Flow<List<PileItem>> = items.pile(kind, clock() - DAY_MS)

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

    /** A fresh id for a photo that's about to be shrunk into [FileStore.pendingFile]. */
    fun newPhotoId(): String = newId()

    /**
     * Saves a photo dump whose shrunk file is waiting in [FileStore.pendingFile]. Write order
     * (design doc §5 B): first the file is committed (atomic rename), THEN the row is written. If the
     * row fails, the rename is undone so tapping "Dump it" again still works.
     */
    suspend fun dumpPhoto(id: String, caption: String): String {
        val store = checkNotNull(photos) { "No photo store" }
        val text = caption.trim().take(MAX_CAPTURE_LENGTH)
        return SloTimer.measure("save_photo", SloTimer.SAVE_PHOTO_TARGET_MS) {
            val fileName = store.commit(id)
            val now = clock()
            try {
                captures.insert(
                    CaptureEntity(
                        id = id,
                        rawText = text.ifEmpty { PHOTO_PLACEHOLDER },
                        source = CaptureSource.PHOTO,
                        photoFile = fileName,
                        status = CaptureStatus.INBOX,
                        // Only words can be guessed from; a bare photo gets no suggestion.
                        suggestedKind = if (text.isEmpty()) null else guessKind(text, localNow()),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } catch (e: Exception) {
                runCatching { store.uncommit(id) }
                throw e
            }
            id
        }
    }

    /** A fresh id for a voice note about to be recorded into [FileStore.pendingFile]. */
    fun newVoiceId(): String = newId()

    /**
     * Saves a recorded voice note waiting in [FileStore.pendingFile], straight away and before any
     * transcription: the recording is the dump, the words are a bonus. Same write order as photos.
     */
    suspend fun dumpVoice(id: String): String {
        val store = checkNotNull(voice) { "No voice store" }
        return SloTimer.measure("save_voice", SloTimer.SAVE_PHOTO_TARGET_MS) {
            val fileName = store.commit(id)
            val now = clock()
            try {
                captures.insert(
                    CaptureEntity(
                        id = id,
                        rawText = VOICE_PLACEHOLDER,
                        source = CaptureSource.VOICE,
                        audioFile = fileName,
                        status = CaptureStatus.INBOX,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } catch (e: Exception) {
                runCatching { store.uncommit(id) }
                throw e
            }
            id
        }
    }

    /**
     * Fills in a voice note's words once transcription finishes. Compare-and-set: it only replaces
     * the placeholder, so if you already renamed or filed the note your words win. Returns whether
     * anything changed.
     */
    suspend fun setTranscript(id: String, text: String): Boolean {
        val words = text.trim().take(MAX_CAPTURE_LENGTH)
        if (words.isEmpty()) return false
        return db.withTransaction {
            val now = clock()
            val changed = captures.replacePlaceholder(id, VOICE_PLACEHOLDER, words, guessKind(words, localNow()), now)
            // Already filed into a pile while it was transcribing? Give the item the words too.
            items.replacePlaceholderTitle(id, VOICE_PLACEHOLDER, words, now)
            changed > 0
        }
    }

    suspend fun getCapture(id: String): CaptureEntity? = captures.get(id)

    fun voiceFile(name: String): File? = voice?.file(name)

    /** The file behind a photo dump, for showing it. */
    fun photoFile(name: String): File? = photos?.file(name)

    /**
     * The reconciliation job (run at app start): deletes photo and voice files that no dump points
     * at, and pending ones nobody finished. Returns how many files it removed.
     */
    suspend fun cleanUpFiles(): Int {
        val photoCount = photos?.reconcile(captures.photoFiles().toSet()) ?: 0
        val voiceCount = voice?.reconcile(captures.audioFiles().toSet()) ?: 0
        return photoCount + voiceCount
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
