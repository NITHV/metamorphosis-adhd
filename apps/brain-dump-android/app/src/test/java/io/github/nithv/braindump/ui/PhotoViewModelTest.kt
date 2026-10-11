package io.github.nithv.braindump.ui

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.CaptureSource
import io.github.nithv.braindump.data.FileStore
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/** The photo sheet's flow, with the image work faked (PhotoShrinkerTest covers the real thing). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PhotoViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository
    private lateinit var photosDir: File
    private val uri = Uri.parse("content://test/photo.jpg")
    private var cleanedUp = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photosDir = tmp.newFolder("photos")
        repo = BrainDumpRepository(db, photos = FileStore(photosDir))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun vm(shrink: (() -> java.io.InputStream, File) -> Unit = { _, out -> out.writeBytes(ByteArray(500)) }) =
        PhotoViewModel(repo, open = { ByteArrayInputStream(ByteArray(10)) }, shrink = shrink)

    @Test
    fun pickReviewCaptionSave() = runTest {
        val vm = vm()
        vm.start()
        vm.onPicked(uri, cleanup = { cleanedUp = true })
        awaitUntil { vm.phase is PhotoPhase.Review }
        assertTrue("the full-size camera file is always deleted", cleanedUp)

        vm.updateCaption("receipt for the blender")
        vm.dump()
        awaitUntil { vm.phase == PhotoPhase.Closed }

        val saved = repo.inbox.first().single()
        assertEquals(CaptureSource.PHOTO, saved.source)
        assertEquals("receipt for the blender", saved.rawText)
        assertEquals(listOf(saved.photoFile), photosDir.list()!!.toList())
    }

    @Test
    fun retakeAndCancelLeaveNoFilesBehind() = runTest {
        val vm = vm()
        vm.start()
        vm.onPicked(uri)
        awaitUntil { vm.phase is PhotoPhase.Review }
        vm.retake()
        assertEquals(PhotoPhase.Picking, vm.phase)
        vm.onPicked(uri)
        awaitUntil { vm.phase is PhotoPhase.Review }
        vm.close()
        assertTrue(photosDir.list()!!.isEmpty())
        assertTrue(repo.inbox.first().isEmpty())
    }

    @Test
    fun anUnreadablePhotoSaysSoAndCleansUp() = runTest {
        val vm = vm(shrink = { _, out -> out.writeBytes(byteArrayOf(1)); throw IOException("Not an image") })
        vm.start()
        vm.onPicked(uri, cleanup = { cleanedUp = true })
        awaitUntil { vm.phase is PhotoPhase.Failed }
        assertTrue(cleanedUp)
        assertTrue("a half-written file must not be left", photosDir.list()!!.isEmpty())
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
