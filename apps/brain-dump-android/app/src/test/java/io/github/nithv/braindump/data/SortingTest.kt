package io.github.nithv.braindump.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Sorting, splitting and piles, against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
class SortingTest {
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository
    private val utc: ZoneId = ZoneOffset.UTC

    /** Saturday 10 Oct 2026, 2 pm (UTC, so local time = stored time in these tests). */
    private var now = LocalDateTime.of(2026, 10, 10, 14, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BrainDumpRepository(db, clock = { now }, newId = { "id-${nextId++}" }, zone = { utc })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun dump(text: String): String = (repo.dumpText(text) as DumpResult.Saved).id

    private fun ms(t: LocalDateTime) = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun dumpsRememberTheirSuggestedPile() = runTest {
        val id = dump("buy milk")
        assertEquals(Kind.TASK, db.captures().get(id)!!.suggestedKind)
    }

    @Test
    fun sortingFilesTheDumpAndPicksUpItsDate() = runTest {
        val id = dump("call mom tomorrow 5pm")
        repo.sort(id, Kind.REMINDER)

        assertTrue(repo.inbox.first().isEmpty())
        assertEquals(CaptureStatus.SORTED, db.captures().get(id)!!.status)
        val item = repo.pile(Kind.REMINDER).first().single().item
        assertEquals("call mom tomorrow 5pm", item.title)
        assertEquals(id, item.captureId)
        assertEquals(ms(LocalDateTime.of(2026, 10, 11, 17, 0)), item.dueAt)
    }

    @Test
    fun ideasAndWorriesNeverGetADate() = runTest {
        repo.sort(dump("maybe paint the room on friday"), Kind.IDEA)
        assertNull(repo.pile(Kind.IDEA).first().single().item.dueAt)
    }

    @Test
    fun undoPutsTheDumpBackAndRemovesTheItem() = runTest {
        val id = dump("buy milk")
        repo.sort(id, Kind.TASK)
        repo.unsort(id)
        assertEquals(listOf("buy milk"), repo.inbox.first().map { it.rawText })
        assertTrue(repo.pile(Kind.TASK).first().isEmpty())
    }

    @Test
    fun sortingTwiceIsRefusedNotDuplicated() = runTest {
        val id = dump("buy milk")
        repo.sort(id, Kind.TASK)
        try {
            repo.sort(id, Kind.IDEA) // a double tap
            fail("expected NotInInboxException")
        } catch (e: NotInInboxException) {
            // expected
        }
        assertEquals(1, repo.pile(Kind.TASK).first().size)
        assertTrue(repo.pile(Kind.IDEA).first().isEmpty())
    }

    /**
     * Fault injection for the transaction. Sorting first marks the dump "sorted", THEN creates the
     * item. We make the second step blow up. Without a transaction the dump would vanish from the
     * Inbox with no item anywhere: lost. With one, the first write is rolled back too.
     */
    @Test
    fun aCrashHalfwayThroughSortingLosesNothing() = runTest {
        var calls = 0
        val flaky = BrainDumpRepository(
            db,
            clock = { now },
            newId = { if (calls++ == 0) "dump-1" else throw IOException("disk full") },
            zone = { utc },
        )
        flaky.dumpText("pay rent")
        try {
            flaky.sort("dump-1", Kind.TASK)
            fail("expected the injected failure")
        } catch (e: IOException) {
            // expected
        }
        assertEquals(CaptureStatus.INBOX, db.captures().get("dump-1")!!.status)
        assertEquals(listOf("pay rent"), repo.inbox.first().map { it.rawText })
        assertTrue(repo.pile(Kind.TASK).first().isEmpty())
    }

    @Test
    fun splitTurnsOneDumpIntoSeveralInReadingOrder() = runTest {
        val id = dump("buy milk\ncall mom\nworried about rent")
        assertEquals(3, repo.split(id))
        val inbox = repo.inbox.first()
        assertEquals(listOf("buy milk", "call mom", "worried about rent"), inbox.map { it.rawText })
        assertEquals(listOf(Kind.TASK, Kind.TASK, Kind.WORRY), inbox.map { it.suggestedKind })
        assertEquals(CaptureStatus.ARCHIVED, db.captures().get(id)!!.status)
    }

    @Test
    fun photosAreNeverSplit() = runTest {
        db.captures().insert(
            CaptureEntity(
                id = "photo-1", rawText = "line one\nline two", source = CaptureSource.PHOTO,
                photoFile = "photo-1.webp", status = CaptureStatus.INBOX, createdAt = now, updatedAt = now,
            ),
        )
        assertTrue(!repo.canSplit(db.captures().get("photo-1")!!))
        try {
            repo.split("photo-1")
            fail("expected a refusal")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        assertEquals(CaptureStatus.INBOX, db.captures().get("photo-1")!!.status)
    }

    @Test
    fun movingToIdeasDropsTheDateAndUndoRestoresIt() = runTest {
        val id = dump("dentist on friday")
        val itemId = repo.sort(id, Kind.REMINDER)
        val due = repo.pile(Kind.REMINDER).first().single().item.dueAt!!

        repo.move(itemId, Kind.IDEA)
        assertNull(repo.pile(Kind.IDEA).first().single().item.dueAt)

        repo.unmove(itemId, Kind.REMINDER, due)
        assertEquals(due, repo.pile(Kind.REMINDER).first().single().item.dueAt)
    }

    @Test
    fun doneItemsStayVisibleForADayThenDropOff() = runTest {
        val itemId = repo.sort(dump("buy milk"), Kind.TASK)
        repo.setDone(itemId, true)
        assertEquals(1, repo.pile(Kind.TASK).first().size)
        assertEquals(0, repo.pileCounts.first()[Kind.TASK]) // counts only open items

        now += 25 * 60 * 60 * 1000L
        assertTrue(repo.pile(Kind.TASK).first().isEmpty())
    }

    @Test
    fun archiveAndUndo() = runTest {
        val a = repo.sort(dump("buy milk"), Kind.TASK)
        val b = repo.sort(dump("fix bike"), Kind.TASK)
        repo.setArchived(listOf(a, b), true)
        assertTrue(repo.pile(Kind.TASK).first().isEmpty())
        repo.setArchived(listOf(a, b), false)
        assertEquals(2, repo.pileCounts.first()[Kind.TASK])
    }

    @Test
    fun settingADateUsesThePhonesTimeZone() = runTest {
        val itemId = repo.sort(dump("buy milk"), Kind.TASK)
        repo.setDue(itemId, LocalDateTime.of(2026, 10, 20, 18, 30))
        assertEquals(ms(LocalDateTime.of(2026, 10, 20, 18, 30)), repo.pile(Kind.TASK).first().single().item.dueAt)
        repo.setDue(itemId, null)
        assertNull(repo.pile(Kind.TASK).first().single().item.dueAt)
    }
}
