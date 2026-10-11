package io.github.nithv.braindump.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    /** Unsorted dumps, newest first. A Flow re-emits whenever the table changes. */
    @Query("SELECT * FROM captures WHERE status = 'INBOX' ORDER BY created_at DESC, id")
    fun inbox(): Flow<List<CaptureEntity>>

    /** ABORT on a duplicate id: a dump must never silently overwrite another. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: CaptureEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(captures: List<CaptureEntity>)

    @Query("UPDATE captures SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: CaptureStatus, now: Long): Int

    @Query("SELECT audio_file FROM captures WHERE audio_file IS NOT NULL")
    suspend fun audioFiles(): List<String>

    @Query(
        """UPDATE captures SET raw_text = :text, suggested_kind = :kind, updated_at = :now
           WHERE id = :id AND raw_text = :placeholder""",
    )
    suspend fun replacePlaceholder(id: String, placeholder: String, text: String, kind: Kind?, now: Long): Int

    /** Every photo file a dump points at, whatever its status (archived dumps keep their photo). */
    @Query("SELECT photo_file FROM captures WHERE photo_file IS NOT NULL")
    suspend fun photoFiles(): List<String>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: String): CaptureEntity?
}
