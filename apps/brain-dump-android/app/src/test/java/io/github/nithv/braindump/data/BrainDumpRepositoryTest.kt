package io.github.nithv.braindump.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real repository against a real (in-memory) Room database on the JVM. */
@RunWith(RobolectricTestRunner::class)
class BrainDumpRepositoryTest {
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository
    private var now = 1_000_000L
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BrainDumpRepository(db, clock = { now }, newId = { "id-${nextId++}" })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savedDumpShowsUpInInboxNewestFirst() = runTest {
        repo.dumpText("first")
        now += 1000
        repo.dumpText("second")
        assertEquals(listOf("second", "first"), repo.inbox.first().map { it.rawText })
    }

    @Test
    fun dumpIsTrimmedAndStoredAsTextInInbox() = runTest {
        val result = repo.dumpText("  call the dentist \n")
        assertEquals(DumpResult.Saved("id-0"), result)
        val saved = db.captures().get("id-0")!!
        assertEquals("call the dentist", saved.rawText)
        assertEquals(CaptureSource.TEXT, saved.source)
        assertEquals(CaptureStatus.INBOX, saved.status)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun blankDumpsAreIgnored() = runTest {
        assertEquals(DumpResult.Empty, repo.dumpText("   \n "))
        assertTrue(repo.inbox.first().isEmpty())
    }

    @Test
    fun overlongDumpsAreRejectedNotTruncated() = runTest {
        assertEquals(DumpResult.TooLong, repo.dumpText("x".repeat(MAX_CAPTURE_LENGTH + 1)))
        assertEquals(DumpResult.Saved("id-0"), repo.dumpText("x".repeat(MAX_CAPTURE_LENGTH)))
    }

    @Test
    fun clearHidesFromInboxAndRestoreBringsItBack() = runTest {
        repo.dumpText("keep me")
        repo.clear("id-0")
        assertTrue(repo.inbox.first().isEmpty())
        // Archived, not deleted: the row is still there for Undo.
        assertEquals(CaptureStatus.ARCHIVED, db.captures().get("id-0")!!.status)
        repo.restore("id-0")
        assertEquals(listOf("keep me"), repo.inbox.first().map { it.rawText })
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun aDuplicateIdFailsLoudlyInsteadOfOverwriting() = runTest {
        val sameId = BrainDumpRepository(db, clock = { now }, newId = { "same" })
        sameId.dumpText("one")
        sameId.dumpText("two") // must throw, never replace "one"
    }
}
