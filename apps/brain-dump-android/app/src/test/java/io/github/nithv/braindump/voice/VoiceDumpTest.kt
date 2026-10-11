package io.github.nithv.braindump.voice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.CaptureSource
import io.github.nithv.braindump.data.FileStore
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.data.VOICE_PLACEHOLDER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Voice dumps: saved before transcription, words filled in safely, and the durable queue. */
@RunWith(RobolectricTestRunner::class)
class VoiceDumpTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: BrainDumpDatabase
    private lateinit var voice: FileStore
    private lateinit var repo: BrainDumpRepository
    private var nextId = 0
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, BrainDumpDatabase::class.java).allowMainThreadQueries().build()
        voice = FileStore(tmp.newFolder("audio"), ".m4a")
        repo = BrainDumpRepository(db, newId = { "id-${nextId++}" }, voice = voice)
        Transcriptions.prefs(context).edit().clear().commit()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun recorded(): String {
        val id = repo.newVoiceId()
        voice.pendingFile(id).writeBytes(ByteArray(4000) { 1 })
        repo.dumpVoice(id)
        return id
    }

    @Test
    fun aVoiceNoteIsSavedBeforeAnyTranscription() = runTest {
        val id = recorded()
        val saved = db.captures().get(id)!!
        assertEquals(VOICE_PLACEHOLDER, saved.rawText)
        assertEquals(CaptureSource.VOICE, saved.source)
        assertEquals("$id.m4a", saved.audioFile)
        assertEquals(listOf("$id.m4a"), voice.dir.list()!!.toList())
    }

    @Test
    fun theWordsReplaceThePlaceholderAndDriveTheGuess() = runTest {
        val id = recorded()
        assertTrue(repo.setTranscript(id, "call the dentist tomorrow"))
        val saved = db.captures().get(id)!!
        assertEquals("call the dentist tomorrow", saved.rawText)
        assertEquals(Kind.REMINDER, saved.suggestedKind)
    }

    @Test
    fun yourOwnWordsAreNeverOverwritten() = runTest {
        // Filed (and so titled) before transcription finished? Fine: the item gets the words.
        val filed = recorded()
        repo.sort(filed, Kind.TASK)
        repo.setTranscript(filed, "buy milk")
        assertEquals("buy milk", repo.pile(Kind.TASK).first().single().item.title)

        // But text that isn't the placeholder any more is left alone (compare-and-set).
        val edited = recorded()
        db.captures().replacePlaceholder(edited, VOICE_PLACEHOLDER, "my own title", null, 0)
        assertFalse(repo.setTranscript(edited, "machine words"))
        assertEquals("my own title", db.captures().get(edited)!!.rawText)
    }

    @Test
    fun cleanUpKeepsVoiceNotesAndRemovesOrphans() = runTest {
        val id = recorded()
        File(voice.dir, "orphan.m4a").writeBytes(byteArrayOf(1))
        voice.dir.listFiles()!!.forEach { it.setLastModified(0) }
        assertEquals(1, repo.cleanUpFiles())
        assertEquals(listOf("$id.m4a"), voice.dir.list()!!.toList())
    }

    private fun queue(result: (File) -> Transcript) =
        Transcriptions(repo, Transcriptions.prefs(context), CoroutineScope(Dispatchers.Unconfined)) { result(it) }

    @Test
    fun theQueueFillsInTheWords() = runTest {
        val id = recorded()
        val q = queue { Transcript.Text("remember the milk") }
        q.enqueue(id).join()
        assertEquals("remember the milk", db.captures().get(id)!!.rawText)
        assertTrue(q.pending.value.isEmpty())
    }

    /** Durability: the app dies mid-transcription; the next start (a new queue object) finishes it. */
    @Test
    fun anInterruptedTranscriptionFinishesNextStart() = runTest {
        val id = recorded()
        val crashing = queue { throw IllegalStateException("app killed") }
        crashing.enqueue(id).join()
        assertEquals(setOf(id), crashing.pending.value) // still queued, on disk

        val nextStart = queue { Transcript.Text("finished later") }
        assertEquals(setOf(id), nextStart.pending.value)
        nextStart.process(id)
        assertEquals("finished later", db.captures().get(id)!!.rawText)
        assertTrue(nextStart.pending.value.isEmpty())
    }

    @Test
    fun failuresAreRetriedButNotForever() = runTest {
        val id = recorded()
        var attempts = 0
        val q = queue { attempts++; Transcript.Failed("busy") }
        q.enqueue(id).join() // attempt 1
        q.process(id) // attempt 2
        q.process(id) // attempt 3: gives up
        q.process(id) // no attempt: already given up
        assertEquals(Transcriptions.MAX_ATTEMPTS, attempts)
        assertTrue(q.pending.value.isEmpty())
        assertEquals(VOICE_PLACEHOLDER, db.captures().get(id)!!.rawText) // still a playable voice note
    }

    @Test
    fun aPhoneThatCantTranscribeKeepsTheRecording() = runTest {
        val id = recorded()
        val q = queue { Transcript.Unavailable("No offline speech pack for en-IN") }
        q.enqueue(id).join()
        assertTrue(q.pending.value.isEmpty())
        assertEquals("No offline speech pack for en-IN", q.unavailable.value)
        assertEquals(VOICE_PLACEHOLDER, db.captures().get(id)!!.rawText)
        assertTrue(File(voice.dir, "$id.m4a").exists())
    }
}
