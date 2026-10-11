package io.github.nithv.braindump.ui

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PauseViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository
    private lateinit var audioDir: File

    private class FakeRecorder(val stopWorks: Boolean = true) : Recorder {
        override val elapsedMs = 1500L
        override fun start(file: File, onLimit: () -> Unit) {
            file.writeBytes(ByteArray(3000))
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

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), recorder: Recorder = FakeRecorder()) =
        PauseViewModel(repo, transcriptions = null, recorder = recorder, saveScope = CoroutineScope(Dispatchers.Main), saved = saved)

    private fun awaitUntil(check: () -> Boolean) {
        repeat(200) {
            ShadowLooper.idleMainLooper()
            if (check()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out")
    }

    @Test
    fun typedPauseIsSaved() = runTest {
        val vm = vm()
        vm.open()
        assertFalse(vm.canSave)
        vm.updateNext("fill in March")
        assertTrue(vm.canSave)
        vm.save()
        awaitUntil { !vm.isOpen }
        assertEquals("fill in March", repo.activeCheckpoints.first().single().nextStep)
        assertEquals("", vm.nextStep) // cleared for next time
    }

    @Test
    fun aRecordedPauseKeepsItsRecording() = runTest {
        val vm = vm()
        vm.open()
        vm.startRecording()
        assertFalse(vm.canSave) // not while still recording
        vm.stopRecording()
        val done = vm.recording as PauseRecording.Done
        assertTrue(vm.canSave) // a recording alone is enough
        vm.save()
        awaitUntil { !vm.isOpen }
        val saved = repo.activeCheckpoints.first().single()
        assertEquals(done.id, saved.id)
        assertEquals(listOf("${done.id}.m4a"), audioDir.list()!!.toList())
    }

    @Test
    fun cancelThrowsTheRecordingAway() {
        val vm = vm()
        vm.open()
        vm.startRecording()
        vm.stopRecording()
        vm.cancel()
        assertFalse(vm.isOpen)
        assertEquals(emptyList<String>(), audioDir.list()!!.toList())
    }

    @Test
    fun aRedoReplacesTheFirstRecording() {
        val vm = vm()
        vm.open()
        vm.startRecording()
        vm.stopRecording()
        val first = (vm.recording as PauseRecording.Done).id
        vm.startRecording()
        vm.stopRecording()
        val second = (vm.recording as PauseRecording.Done).id
        assertTrue(first != second)
        assertEquals(listOf("$second.m4a.tmp"), audioDir.list()!!.toList())
    }

    @Test
    fun tooShortIsNotKept() {
        val vm = vm(recorder = FakeRecorder(stopWorks = false))
        vm.open()
        vm.startRecording()
        vm.stopRecording()
        assertEquals(PauseRecording.None, vm.recording)
        assertTrue(vm.error!!.contains("too short"))
        assertEquals(emptyList<String>(), audioDir.list()!!.toList())
    }

    /** Android closed the app while you were in another one: the half-written note comes back. */
    @Test
    fun theSheetSurvivesTheAppBeingClosed() {
        val saved = SavedStateHandle()
        val before = vm(saved)
        before.open()
        before.updateWhere("halfway through the budget")
        before.updateLink("docs.google.com")
        before.startRecording()
        before.stopRecording()
        val recorded = (before.recording as PauseRecording.Done).id

        val after = vm(saved) // a new view model from the same saved state, as after process death
        assertTrue(after.isOpen)
        assertEquals("halfway through the budget", after.whereText)
        assertEquals("docs.google.com", after.link)
        assertEquals(recorded, (after.recording as PauseRecording.Done).id)
    }

    @Test
    fun swipingAwayKeepsWhatYouTyped() {
        val vm = vm()
        vm.open()
        vm.updateWhere("halfway")
        vm.hide()
        assertFalse(vm.isOpen)
        vm.open()
        assertEquals("halfway", vm.whereText)
    }
}
