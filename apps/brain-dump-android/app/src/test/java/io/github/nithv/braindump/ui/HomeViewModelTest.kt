package io.github.nithv.braindump.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.DraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: BrainDumpDatabase
    private lateinit var repo: BrainDumpRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, BrainDumpDatabase::class.java).allowMainThreadQueries().build()
        repo = BrainDumpRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (db.isOpen) db.close()
    }

    @Test
    fun draftSurvivesTheAppBeingClosed() {
        HomeViewModel(repo, DraftStore(context)).updateDraft("half a thou")
        // A brand-new ViewModel is what you get after a swipe-away or force-stop.
        assertEquals("half a thou", HomeViewModel(repo, DraftStore(context)).draft)
    }

    @Test
    fun dumpingSavesAndClearsTheDraftEverywhere() = runTest {
        val vm = HomeViewModel(repo, DraftStore(context))
        vm.updateDraft("buy milk")
        vm.dump()
        assertEquals("", vm.draft) // the box empties instantly
        awaitUntil { DraftStore(context).load().isEmpty() } // disk copy goes only after the save commits
        assertEquals("", DraftStore(context).load())
        assertEquals(listOf("buy milk"), repo.inbox.first().map { it.rawText })
    }

    @Test
    fun aFailedSaveGivesTheTextBack() = runTest {
        // Fault injection: saving blows up midway, like a full disk or a corrupt database would.
        // (Closing an in-memory database isn't enough: Room quietly opens a fresh one.)
        val broken = BrainDumpRepository(db, newId = { throw java.io.IOException("disk full") })
        val vm = HomeViewModel(broken, DraftStore(context))
        vm.updateDraft("do not lose me")
        vm.dump()
        awaitUntil { vm.draft.isNotEmpty() }
        assertEquals("do not lose me", vm.draft)
        assertEquals("do not lose me", DraftStore(context).load())
    }

    /**
     * Waits for background work (Room runs on its own threads) by checking a condition, not by
     * sleeping a fixed time. Fixed sleeps make tests flaky on a busy machine; this one only fails
     * if the condition is still false after 10 seconds.
     */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within 10 s" }
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }
}
