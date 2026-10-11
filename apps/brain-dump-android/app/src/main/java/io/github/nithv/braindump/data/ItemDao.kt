package io.github.nithv.braindump.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class KindCount(val kind: Kind, val n: Int)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ItemEntity)

    /** One pile: everything not archived, except things finished before [doneSince]. */
    @Query(
        """SELECT * FROM items WHERE kind = :kind AND archived_at IS NULL
           AND (done_at IS NULL OR done_at >= :doneSince)
           ORDER BY created_at DESC, id LIMIT 500""",
    )
    fun pile(kind: Kind, doneSince: Long): Flow<List<ItemEntity>>

    /** Open (not done, not archived) items per pile. */
    @Query("SELECT kind, COUNT(*) AS n FROM items WHERE archived_at IS NULL AND done_at IS NULL GROUP BY kind")
    fun openCounts(): Flow<List<KindCount>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("UPDATE items SET done_at = :doneAt, updated_at = :now WHERE id = :id")
    suspend fun setDone(id: String, doneAt: Long?, now: Long): Int

    @Query("UPDATE items SET archived_at = :archivedAt, updated_at = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archivedAt: Long?, now: Long): Int

    @Query("UPDATE items SET kind = :kind, due_at = :dueAt, updated_at = :now WHERE id = :id")
    suspend fun move(id: String, kind: Kind, dueAt: Long?, now: Long): Int

    @Query("UPDATE items SET due_at = :dueAt, updated_at = :now WHERE id = :id")
    suspend fun setDue(id: String, dueAt: Long?, now: Long): Int

    @Query("DELETE FROM items WHERE capture_id = :captureId")
    suspend fun deleteForCapture(captureId: String): Int
}
