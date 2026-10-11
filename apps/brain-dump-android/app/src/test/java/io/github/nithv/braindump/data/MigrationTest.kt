package io.github.nithv.braindump.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Schema migrations are forever" (design doc Â§4). Each test builds a database exactly as an OLD
 * version of the app left it (from the committed schemas/â€¦/N.json), fills it with data, upgrades
 * it, and checks two things: Room agrees the result is the new shape, column for column, and every
 * row someone had is still there.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BrainDumpDatabase::class.java)

    /** A v1 database as 0.5.0 left it: a voice dump in the Inbox, a photo dump filed as a task. */
    private fun createV1() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """INSERT INTO captures (id, raw_text, source, audio_file, photo_file, status, suggested_kind, created_at, updated_at)
                   VALUES ('c1', 'call the dentist tomorrow', 'VOICE', 'c1.m4a', NULL, 'INBOX', 'REMINDER', 1000, 1000),
                          ('c2', '📷 Photo', 'PHOTO', NULL, 'c2.webp', 'SORTED', NULL, 2000, 2500)""",
            )
            db.execSQL(
                """INSERT INTO items (id, capture_id, kind, title, due_at, done_at, archived_at, created_at, updated_at)
                   VALUES ('i1', 'c2', 'TASK', '📷 Photo', 99000, NULL, NULL, 2500, 2500)""",
            )
        }
    }

    @Test
    fun v1ToV2KeepsEverythingAndAddsCheckpoints() {
        createV1()
        // validateDroppedTables + Room's own check: fails if the migrated shape differs from 2.json at all.
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT id, raw_text, audio_file, photo_file, status FROM captures ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(listOf("c1", "call the dentist tomorrow", "c1.m4a", null, "INBOX"), (0..4).map(c::getString))
            c.moveToNext()
            assertEquals(listOf("c2", "📷 Photo", null, "c2.webp", "SORTED"), (0..4).map(c::getString))
        }
        db.query("SELECT capture_id, kind, due_at FROM items").use { c ->
            c.moveToFirst()
            assertEquals("c2", c.getString(0))
            assertEquals("TASK", c.getString(1))
            assertEquals(99000L, c.getLong(2))
        }
        db.query("SELECT COUNT(*) FROM checkpoints").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    /**
     * The real app's path: Room opens the old file with [MIGRATIONS] and the actual DAOs read and
     * write through it. Catches a migration that's missing from the list the app uses.
     */
    @Test
    fun theAppOpensAnUpgradedDatabase() = runBlocking {
        createV1()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(context, BrainDumpDatabase::class.java, dbName)
            .addMigrations(*MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = BrainDumpRepository(db, newId = { "cp-1" }, clock = { 5000 })
            assertEquals(listOf("c1"), repo.inbox.first().map { it.id })
            assertEquals("📷 Photo", repo.pile(Kind.TASK).first().single().item.title)
            repo.pause(repo.newCheckpointId(), "halfway through the budget", "fill in March")
            val saved = repo.activeCheckpoints.first().single()
            assertEquals("halfway through the budget", saved.transcript)
            assertNull(saved.resumedAt)
        } finally {
            db.close()
        }
    }

    /** Every version ever shipped can reach the newest one (more lines here as versions are added). */
    @Test
    fun everyOldVersionReachesTheLatest() {
        for (from in 1 until BrainDumpDatabase.VERSION) {
            val name = "from-v$from.db"
            helper.createDatabase(name, from).close()
            helper.runMigrationsAndValidate(name, BrainDumpDatabase.VERSION, true, *MIGRATIONS).close()
        }
    }
}
