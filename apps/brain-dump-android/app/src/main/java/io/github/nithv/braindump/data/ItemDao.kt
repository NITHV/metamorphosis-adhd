package io.github.nithv.braindump.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class KindCount(val kind: Kind, val n: Int)

/** A pile item plus the photo of the dump it came from, if any. */
data class PileItem(
    @Embedded val item: ItemEntity,
    @ColumnInfo(name = "photo_file") val photoFile: String?,
)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ItemEntity)

    /** One pile: everything not archived, except things finished before [doneSince]. */
    @Query(
        """SELECT items.*, captures.photo_file AS photo_file FROM items
           LEFT JOIN captures ON captures.id = items.capture_id
           WHERE items.kind = :kind AND items.archived_at IS NULL
           AND (items.done_at IS NULL OR items.done_at >= :doneSince)
           ORDER BY items.created_at DESC, items.id LIMIT 500""",
    )
    fun pile(kind: Kind, doneSince: Long): Flow<List<PileItem>>

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
