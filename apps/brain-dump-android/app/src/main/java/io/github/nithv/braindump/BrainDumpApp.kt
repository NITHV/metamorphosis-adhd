package io.github.nithv.braindump

import android.app.Application
import io.github.nithv.braindump.data.BrainDumpDatabase
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.DraftStore

/**
 * Holds the single database and repository for the whole app. Screens (and, later, widgets and
 * the share sheet) all get the same instances, so they can never disagree about what's saved.
 */
class BrainDumpApp : Application() {
    val repository: BrainDumpRepository by lazy {
        BrainDumpRepository(BrainDumpDatabase.open(this))
    }

    val drafts: DraftStore by lazy { DraftStore(this) }
}
