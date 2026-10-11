package io.github.nithv.braindump.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.CaptureSource
import io.github.nithv.braindump.data.FileStore
import io.github.nithv.braindump.voice.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VoiceViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository
    private lateinit var audioDir: File

    /** Writes a few bytes like a real recording would. [stopWorks] false = stopped too soon. */
    private class FakeRecorder(val stopWorks: Boolean = true, val micBusy: Boolean = false) : Recorder {
        var file: File? = null
        override val elapsedMs = 1500L
        override fun start(file: File, onLimit: () -> Unit) {
            if (micBusy) throw IOException("mic busy")
            this.file = file.apply { writeBytes(ByteArray(3000)) }
        }
        override fun level() = 0.5f
        override fun stop() = stopWorks
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioDir = tmp.newFolder("audio")
        repo = BrainDumpRepository(db, voice = FileStore(audioDir, ".m4a"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun vm(recorder: Recorder) =
        VoiceViewModel(repo, transcriptions = null, recorder = recorder, saveScope = CoroutineScope(Dispatchers.Main), canTranscribe = { true })

    @Test
    fun recordStopSaved() = runTest {
        val vm = vm(FakeRecorder())
        vm.open()
        vm.start()
        assertTrue(vm.phase is VoicePhase.Recording)
        vm.stopAndSave()
        awaitUntil { vm.phase == VoicePhase.Closed }
        val saved = repo.inbox.first().single()
        assertEquals(CaptureSource.VOICE, saved.source)
        assertEquals(listOf(saved.audioFile), audioDir.list()!!.toList())
    }

    @Test
    fun aSplitSecondTapSaysTooShortAndLeavesNothing() = runTest {
        val vm = vm(FakeRecorder(stopWorks = false))
        vm.open()
        vm.start()
        vm.stopAndSave()
        assertTrue((vm.phase as VoicePhase.Failed).message.contains("too short"))
        assertTrue(audioDir.list()!!.isEmpty())
        assertTrue(repo.inbox.first().isEmpty())
    }

    @Test
    fun cancelThrowsTheRecordingAway() = runTest {
        val vm = vm(FakeRecorder())
        vm.open()
        vm.start()
        vm.cancel()
        assertEquals(VoicePhase.Closed, vm.phase)
        assertTrue(audioDir.list()!!.isEmpty())
    }

    @Test
    fun aBusyMicSaysSo() = runTest {
        val vm = vm(FakeRecorder(micBusy = true))
        vm.open()
        vm.start()
        assertTrue(vm.phase is VoicePhase.Failed)
        assertTrue(audioDir.list()!!.isEmpty())
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within 10 s" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }
}
