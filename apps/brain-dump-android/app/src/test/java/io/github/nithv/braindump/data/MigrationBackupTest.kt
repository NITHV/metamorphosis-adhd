package io.github.nithv.braindump.data

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The copy made before any upgrade (design doc §9): made once, never overwritten, cleaned up later. */
@RunWith(RobolectricTestRunner::class)
class MigrationBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun database(version: Int, note: String): File {
        val file = File(tmp.root, "brain-dump.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS t (note TEXT)")
            db.execSQL("DELETE FROM t")
            db.execSQL("INSERT INTO t VALUES (?)", arrayOf(note))
            db.version = version
        }
        return file
    }

    private fun noteIn(file: File): String =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT note FROM t", null).use { c -> c.moveToFirst(); c.getString(0) }
        }

    @Test
    fun aFreshInstallHasNothingToCopy() {
        assertNull(MigrationBackup(File(tmp.root, "brain-dump.db")).beforeOpen(2))
    }

    @Test
    fun anOlderDatabaseIsCopiedBeforeUpgrading() {
        val backup = MigrationBackup(database(1, "my dumps"))
        assertEquals(1, backup.versionOnDisk())
        val copy = backup.beforeOpen(2)
        assertNotNull(copy)
        assertEquals("brain-dump.db.before-v1", copy!!.name)
        assertEquals("my dumps", noteIn(copy))
        assertTrue(backup.copies().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun aRetriedUpgradeKeepsTheOriginalCopy() {
        val file = database(1, "original")
        MigrationBackup(file).beforeOpen(2)
        // Say the upgrade crashed half-way and changed something; the next try must not overwrite the true original.
        database(1, "half-migrated")
        assertNull(MigrationBackup(file).beforeOpen(2))
        assertEquals("original", noteIn(File(tmp.root, "brain-dump.db.before-v1")))
    }

    @Test
    fun theCopyIsRemovedOnceTheUpgradeHasWorked() {
        val file = database(1, "x")
        val backup = MigrationBackup(file)
        backup.beforeOpen(2)
        database(2, "x") // the upgrade ran
        assertEquals(1, backup.copies().size) // kept through the launch that upgraded…
        assertNull(backup.beforeOpen(2)) // …and removed at the next one, which finds it up to date
        assertEquals(emptyList<File>(), backup.copies())
    }
}
