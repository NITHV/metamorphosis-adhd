package io.github.nithv.braindump

import android.app.Application
import android.util.Log
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.DraftStore
import io.github.nithv.braindump.data.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Holds the single database and repository for the whole app. Screens (and, later, widgets and
 * the share sheet) all get the same instances, so they can never disagree about what's saved.
 */
class BrainDumpApp : Application() {
    /** Work that should finish even if the screen that started it goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: BrainDumpRepository by lazy {
        BrainDumpRepository(BrainDumpDatabase.open(this), photos = PhotoStore(File(filesDir, "photos")))
    }

    val drafts: DraftStore by lazy { DraftStore(this) }

    override fun onCreate() {
        super.onCreate()
        // The reconciliation job. Off the main thread, so it never slows the app opening.
        appScope.launch(Dispatchers.IO) {
            runCatching { repository.cleanUpPhotos() }
                .onSuccess { if (it > 0) Log.i("BrainDump", "Cleaned up $it orphan photo file(s)") }
                .onFailure { Log.w("BrainDump", "Photo clean-up failed; will retry next start", it) }
        }
    }
}
