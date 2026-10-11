package io.github.nithv.braindump.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/** Photo dumps: write order, atomic commit, and the clean-up (reconciliation) job. */
@RunWith(RobolectricTestRunner::class)
class PhotoDumpTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: BrainDumpDatabase
    private lateinit var store: PhotoStore
    private lateinit var repo: BrainDumpRepository
    private var now = 10 * PhotoStore.GRACE_MS
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PhotoStore(tmp.newFolder("photos"), clock = { now })
        repo = BrainDumpRepository(db, clock = { now }, newId = { "id-${nextId++}" }, photos = store)
    }

    @After
    fun tearDown() = db.close()

    /** Stands in for the shrinker: some bytes in the pending file. */
    private fun pendingPhoto(id: String): File = store.pendingFile(id).apply { writeBytes(ByteArray(1000) { 7 }) }

    private fun names() = store.dir.list()!!.sorted()

    @Test
    fun aPhotoDumpPointsAtACommittedFile() = runTest {
        pendingPhoto("p1")
        repo.dumpPhoto("p1", "  concert saturday 8pm ")

        val saved = db.captures().get("p1")!!
        assertEquals("concert saturday 8pm", saved.rawText)
        assertEquals(CaptureSource.PHOTO, saved.source)
        assertEquals("p1.webp", saved.photoFile)
        assertEquals(Kind.REMINDER, saved.suggestedKind) // the caption drives the guess
        assertEquals(listOf("p1.webp"), names()) // committed, no .tmp left
        assertEquals(1000L, repo.photoFile(saved.photoFile!!)!!.length())
    }

    @Test
    fun noCaptionMeansAPlaceholderAndNoGuess() = runTest {
        pendingPhoto("p1")
        repo.dumpPhoto("p1", "   ")
        val saved = db.captures().get("p1")!!
        assertEquals(PHOTO_PLACEHOLDER, saved.rawText)
        assertNull(saved.suggestedKind)
        assertFalse(repo.canSplit(saved))
    }

    @Test
    fun aMissingOrEmptyPhotoIsNeverSaved() = runTest {
        store.pendingFile("p1").writeBytes(ByteArray(0))
        try {
            repo.dumpPhoto("p1", "x")
            fail("expected a refusal")
        } catch (e: IOException) {
            // expected
        }
        assertNull(db.captures().get("p1"))
    }

    /**
     * Fault injection: the database write fails AFTER the file was committed (here, a clashing id).
     * The rename must be undone, so no committed file is left without a row and "Dump it" can retry.
     */
    @Test
    fun ifTheRowFailsTheFileGoesBackToPending() = runTest {
        db.captures().insert(
            CaptureEntity(id = "p1", rawText = "already here", source = CaptureSource.TEXT, status = CaptureStatus.INBOX, createdAt = now, updatedAt = now),
        )
        pendingPhoto("p1")
        try {
            repo.dumpPhoto("p1", "x")
            fail("expected the injected failure")
        } catch (e: Exception) {
            // expected
        }
        assertEquals(listOf("p1.webp.tmp"), names())
        assertEquals("already here", db.captures().get("p1")!!.rawText)
    }

    @Test
    fun cleanUpRemovesOnlyOldOrphans() = runTest {
        pendingPhoto("kept")
        repo.dumpPhoto("kept", "") // referenced by a dump: must stay, however old
        File(store.dir, "orphan.webp").writeBytes(byteArrayOf(1)) // a crash between rename and row
        pendingPhoto("abandoned") // reviewed, never saved
        store.dir.listFiles()!!.forEach { it.setLastModified(now - 2 * PhotoStore.GRACE_MS) }
        File(store.dir, "just-renamed.webp").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 1000) } // a save in progress

        assertEquals(2, repo.cleanUpPhotos())
        assertEquals(listOf("just-renamed.webp", "kept.webp"), names())
        assertEquals(0, repo.cleanUpPhotos()) // running it again changes nothing (idempotent)
    }

    @Test
    fun archivedPhotoDumpsKeepTheirFile() = runTest {
        pendingPhoto("p1")
        repo.dumpPhoto("p1", "")
        repo.clear("p1")
        store.dir.listFiles()!!.forEach { it.setLastModified(0) }
        assertEquals(0, repo.cleanUpPhotos()) // Undo must still find the photo
        assertTrue(File(store.dir, "p1.webp").exists())
    }

    @Test
    fun sortedPhotosShowInTheirPile() = runTest {
        pendingPhoto("p1")
        repo.dumpPhoto("p1", "whiteboard notes")
        repo.sort("p1", Kind.IDEA)
        assertEquals("p1.webp", repo.pile(Kind.IDEA).first().single().photoFile)
    }
}
