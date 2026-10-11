package io.github.nithv.braindump.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PausedViewModelTest {
    private lateinit var db: BrainDumpDatabase
    private var now = 1_000_000_000L
    private lateinit var repo: BrainDumpRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BrainDumpDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BrainDumpRepository(db, clock = { now })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun afterAWeekItAsksIfStillRelevant() = runBlocking {
        val id = repo.pause(repo.newCheckpointId(), "the budget", "fill in March", link = "https://www.example.com/x")
        val vm = PausedViewModel(repo, flowOf(emptySet()), clock = { now })
        val fresh = vm.state.first { it.active != null }.active!!.single()
        assertFalse(fresh.stale)
        assertEquals("example.com", fresh.linkHost)

        now += STALE_AFTER_MS + 1
        val old = PausedViewModel(repo, flowOf(emptySet()), clock = { now })
        assertTrue(old.state.first { it.active != null }.active!!.single().stale)

        repo.keep(id) // "Keep it"
        assertFalse(old.state.first { s -> s.active?.single()?.stale == false }.active!!.single().stale)
    }

    @Test
    fun aVoiceOnlyPauseShowsAPlaceholderUntilItsWords() = runBlocking {
        val id = repo.pause(repo.newCheckpointId(), "", "next bit")
        val vm = PausedViewModel(repo, flowOf(setOf(id)), clock = { now })
        val row = vm.state.first { it.active != null }.active!!.single()
        assertEquals("", row.body) // no recording here, so nothing to show
        assertTrue(row.transcribing)
        assertEquals(row to 1, vm.latest.first { it != null })
    }
}
