package io.github.nithv.braindump.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables mirror the website's (docs/brain-dump-android/system-design.md §4), minus user_id:
// on a phone-only app, the phone *is* the user. Times are UTC epoch milliseconds.
// Enums are stored by name, so renaming an enum value later needs a migration.

enum class CaptureSource { TEXT, VOICE, PHOTO }

enum class CaptureStatus { INBOX, SORTED, ARCHIVED }

enum class Kind { TASK, IDEA, REMINDER, WORRY }

/** One per dump: the raw thing you typed, said or photographed. */
@Entity(
    tableName = "captures",
    indices = [Index(value = ["status", "created_at"])],
)
data class CaptureEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    val source: CaptureSource,
    /** File name inside the app's private audio/ folder (a name, not a path, so restores work). */
    @ColumnInfo(name = "audio_file") val audioFile: String? = null,
    /** File name inside the app's private photos/ folder. */
    @ColumnInfo(name = "photo_file") val photoFile: String? = null,
    val status: CaptureStatus,
    @ColumnInfo(name = "suggested_kind") val suggestedKind: Kind? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** What a dump was sorted into (used from milestone N2; created now so N2 needs no migration). */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["capture_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("capture_id"), Index(value = ["kind", "archived_at"])],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "capture_id") val captureId: String?,
    val kind: Kind,
    val title: String,
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
