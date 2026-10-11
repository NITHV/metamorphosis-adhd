package io.github.nithv.braindump

import android.app.Application
import android.util.Log
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.DraftStore
import io.github.nithv.braindump.data.FileStore
import io.github.nithv.braindump.voice.Transcriber
import io.github.nithv.braindump.voice.Transcriptions
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
        BrainDumpRepository(
            BrainDumpDatabase.open(this),
            photos = FileStore(File(filesDir, "photos"), ".webp"),
            voice = FileStore(File(filesDir, "audio"), ".m4a"),
        )
    }

    val drafts: DraftStore by lazy { DraftStore(this) }

    val transcriptions: Transcriptions by lazy {
        Transcriptions(repository, Transcriptions.prefs(this), appScope) { file -> Transcriber.transcribe(this, file) }
    }

    override fun onCreate() {
        super.onCreate()
        // Background housekeeping, off the main thread so it never slows the app opening:
        // the reconciliation job, then any voice notes an earlier run didn't finish transcribing.
        appScope.launch(Dispatchers.IO) {
            runCatching { repository.cleanUpFiles() }
                .onSuccess { if (it > 0) Log.i("BrainDump", "Cleaned up $it orphan file(s)") }
                .onFailure { Log.w("BrainDump", "File clean-up failed; will retry next start", it) }
            transcriptions.resume()
        }
    }
}
