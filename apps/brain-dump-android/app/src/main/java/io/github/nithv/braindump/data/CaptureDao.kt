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

    @Query("UPDATE captures SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: CaptureStatus, now: Long): Int

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun get(id: String): CaptureEntity?
}
