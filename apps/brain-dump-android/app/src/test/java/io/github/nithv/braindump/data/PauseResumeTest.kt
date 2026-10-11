package io.github.nithv.braindump.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Pause / Resume: "Where did I leave off?" bookmarks. */
@RunWith(RobolectricTestRunner::class)
class PauseResumeTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: BrainDumpDatabase
    private lateinit var voice: FileStore
    private lateinit var repo: BrainDumpRepository
    private var now = 1_000_000L
    private var nextId = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        voice = FileStore(tmp.newFolder("audio"), ".m4a")
        repo = BrainDumpRepository(db, clock = { now }, newId = { "id-${nextId++}" }, voice = voice)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun pausingSavesYourPlace() = runTest {
        repo.pause(repo.newCheckpointId(), "  halfway through the budget  ", "fill in March", tag = " Taxes ", link = "docs.google.com/sheet")
        val saved = repo.activeCheckpoints.first().single()
        assertEquals("halfway through the budget", saved.transcript)
        assertEquals("fill in March", saved.nextStep)
        assertEquals("Taxes", saved.projectTag)
        assertEquals("https://docs.google.com/sheet", saved.link)
        assertNull(saved.audioFile)
    }

    @Test
    fun anEmptyPauseIsRefused() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.pause("x", " ", " ") } }
        assertTrue(repo.activeCheckpoints.first().isEmpty())
    }

    @Test
    fun onlyWebLinksAreKept() {
        assertEquals("https://example.com/a?b=1", normalizeLink("example.com/a?b=1"))
        assertEquals("http://example.com", normalizeLink("http://example.com"))
        assertNull(normalizeLink("javascript:alert(1)"))
        assertNull(normalizeLink("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(normalizeLink("file:///sdcard/secret.txt"))
        assertNull(normalizeLink("not a link at all"))
        assertNull(normalizeLink(""))
    }

    @Test
    fun resumeAndLetGoBothHaveUndo() = runTest {
        val a = repo.pause(repo.newCheckpointId(), "a", "")
        now += 1
        val b = repo.pause(repo.newCheckpointId(), "b", "")
        assertEquals(listOf(b, a), repo.activeCheckpoints.first().map { it.id }) // newest first

        repo.resume(a)
        assertEquals(listOf(b), repo.activeCheckpoints.first().map { it.id })
        assertEquals(listOf(a), repo.recentlyResumed.first().map { it.id })
        repo.unresume(a)
        assertEquals(setOf(a, b), repo.activeCheckpoints.first().map { it.id }.toSet())

        repo.dismiss(b)
        assertEquals(listOf(a), repo.activeCheckpoints.first().map { it.id })
        assertTrue(repo.recentlyResumed.first().isEmpty()) // let go isn't "resumed"
        repo.undismiss(b)
        assertEquals(2, repo.activeCheckpoints.first().size)
    }

    @Test
    fun keepItResetsTheWeekClock() = runTest {
        val id = repo.pause(repo.newCheckpointId(), "a", "")
        now += 8 * 24 * 60 * 60 * 1000L
        repo.keep(id)
        assertEquals(now, repo.activeCheckpoints.first().single().updatedAt)
    }

    @Test
    fun aRecordingIsCommittedThenTranscribedIntoABlankNote() = runTest {
        val id = repo.newCheckpointId()
        voice.pendingFile(id).writeBytes(ByteArray(2000) { 1 })
        repo.pause(id, "", "", withRecording = true)
        val saved = repo.activeCheckpoints.first().single()
        assertEquals("$id.m4a", saved.audioFile)
        assertEquals(File(voice.dir, "$id.m4a"), repo.recordingOf(id))

        assertTrue(repo.setTranscript(id, "halfway through the budget"))
        assertEquals("halfway through the budget", db.checkpoints().get(id)!!.transcript)
        // Compare-and-set: once there are words, a second transcription doesn't replace them.
        assertFalse(repo.setTranscript(id, "something else"))
        assertEquals("halfway through the budget", db.checkpoints().get(id)!!.transcript)
    }

    @Test
    fun typedWordsAreNeverReplacedByTranscription() = runTest {
        val id = repo.newCheckpointId()
        voice.pendingFile(id).writeBytes(ByteArray(2000) { 1 })
        repo.pause(id, "my own words", "", withRecording = true)
        assertFalse(repo.setTranscript(id, "machine words"))
        assertEquals("my own words", db.checkpoints().get(id)!!.transcript)
    }

    /**
     * The bug this milestone could have shipped: the clean-up job only knew about voice DUMPS, so it
     * would have deleted every Pause recording an hour after it was made.
     */
    @Test
    fun cleanUpKeepsPauseRecordings() = runTest {
        val id = repo.newCheckpointId()
        voice.pendingFile(id).writeBytes(ByteArray(2000) { 1 })
        repo.pause(id, "", "", withRecording = true)
        repo.resume(id) // resumed ones keep theirs too, for Undo and the history
        File(voice.dir, "orphan.m4a").writeBytes(byteArrayOf(1))
        voice.dir.listFiles()!!.forEach { it.setLastModified(0) }
        assertEquals(1, repo.cleanUpFiles())
        assertEquals(listOf("$id.m4a"), voice.dir.list()!!.toList())
    }

    @Test
    fun aFailedSaveGivesTheRecordingBack() = runTest {
        val id = repo.pause(repo.newCheckpointId(), "first", "")
        // Same id again: the insert fails (ABORT), so the committed recording must go back to pending.
        voice.pendingFile(id).writeBytes(ByteArray(2000) { 1 })
        runCatching { repo.pause(id, "", "", withRecording = true) }
        assertTrue(voice.pendingFile(id).isFile)
        assertFalse(File(voice.dir, "$id.m4a").exists())
    }
}
