package io.github.nithv.braindump.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckpointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(checkpoint: CheckpointEntity)

    /** Places you paused and haven't come back to, newest first. */
    @Query("SELECT * FROM checkpoints WHERE resumed_at IS NULL AND dismissed_at IS NULL ORDER BY created_at DESC, id LIMIT 100")
    fun active(): Flow<List<CheckpointEntity>>

    /** The last few you resumed, for a small history list. */
    @Query("SELECT * FROM checkpoints WHERE resumed_at IS NOT NULL ORDER BY resumed_at DESC LIMIT 5")
    fun recentlyResumed(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints WHERE id = :id")
    suspend fun get(id: String): CheckpointEntity?

    @Query("UPDATE checkpoints SET resumed_at = :at WHERE id = :id")
    suspend fun setResumed(id: String, at: Long?): Int

    @Query("UPDATE checkpoints SET dismissed_at = :at WHERE id = :id")
    suspend fun setDismissed(id: String, at: Long?): Int

    /** "Keep it": resets the 7-day "still relevant?" clock. */
    @Query("UPDATE checkpoints SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long): Int

    /** Compare-and-set, like voice dumps: transcribed words only fill a blank "where was I". */
    @Query("UPDATE checkpoints SET transcript = :text, updated_at = :now WHERE id = :id AND transcript = ''")
    suspend fun fillTranscript(id: String, text: String, now: Long): Int

    /** Every recording a checkpoint points at, resumed or not (the clean-up job must keep them). */
    @Query("SELECT audio_file FROM checkpoints WHERE audio_file IS NOT NULL")
    suspend fun audioFiles(): List<String>
}
