package io.github.nithv.braindump.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * The safety net under every migration (design doc §9): before an app update upgrades the database,
 * copy the old one aside. If a migration ever turns out to be wrong, a fix-forward release can
 * rebuild from the copy instead of the damage being permanent.
 *
 * Lifecycle: made when the file on disk is older than the app ([beforeOpen] with a higher version),
 * kept through that first launch, and deleted at the next launch that finds the database already
 * up to date, i.e. once the upgraded app has opened it successfully.
 */
class MigrationBackup(private val dbFile: File) {

    /** The `user_version` stamped in the file, or null if there's no database yet (fresh install). */
    fun versionOnDisk(): Int? {
        if (!dbFile.isFile) return null
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    }

    /** Copies made so far (the database file plus its write-ahead log, when there is one). */
    fun copies(): List<File> =
        dbFile.parentFile?.listFiles { f -> f.name.startsWith("${dbFile.name}.before-v") }?.sortedBy { it.name }.orEmpty()

    /** Call before opening the database. Returns the copy it made, if it made one. */
    fun beforeOpen(appVersion: Int): File? {
        val onDisk = versionOnDisk() ?: return null
        if (onDisk >= appVersion) {
            // Up to date: the last upgrade worked and this is a later launch, so the copy can go.
            copies().forEach { if (it.delete()) Log.i(TAG, "Removed old pre-migration copy ${it.name}") }
            return null
        }
        val copy = File(dbFile.parentFile, "${dbFile.name}.before-v$onDisk")
        // A copy from an earlier attempt at this same upgrade is the true original: never overwrite it.
        if (copy.exists()) return null
        // Copy to a temporary name, then rename: a half-finished copy must never look complete.
        sidecars().forEach { (from, suffix) ->
            val tmp = File(dbFile.parentFile, "${copy.name}$suffix.tmp")
            from.copyTo(tmp, overwrite = true)
            check(tmp.renameTo(File(dbFile.parentFile, "${copy.name}$suffix"))) { "Couldn't finish copy of ${from.name}" }
        }
        Log.i(TAG, "Copied database v$onDisk aside before upgrading to v$appVersion")
        return copy
    }

    /** The main file, and the write-ahead log if it has unmerged changes in it. */
    private fun sidecars(): List<Pair<File, String>> =
        listOfNotNull(dbFile to "", File("${dbFile.path}-wal").takeIf { it.isFile && it.length() > 0 }?.let { it to "-wal" })
            // Main file last: its presence marks the copy as complete.
            .reversed()

    private companion object {
        const val TAG = "BrainDump/Migration"
    }
}
