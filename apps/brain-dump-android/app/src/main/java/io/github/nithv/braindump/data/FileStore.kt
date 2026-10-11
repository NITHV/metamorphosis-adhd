package io.github.nithv.braindump.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Photo and voice files in the app's private folders (design doc §5 B). Each file goes through
 * two names (shown for photos; voice notes use .m4a):
 *
 *   <id>.webp.tmp   being written, or waiting for you to tap "Dump it"
 *   <id>.webp       committed: complete, and safe for a database row to point at
 *
 * Renaming is atomic (the filesystem does it in one step), so a `.webp` file is never half-written.
 * The row is written only AFTER the rename, so a row can never point at a missing or partial file.
 * The worst a crash can leave is an orphan file, which [reconcile] cleans up.
 */
class FileStore(
    val dir: File,
    /** ".webp" for photos, ".m4a" for voice notes. */
    private val extension: String = ".webp",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pendingExtension = "$extension.tmp"

    /** Where to write a new file before it's committed. */
    fun pendingFile(id: String): File {
        dir.mkdirs()
        return File(dir, "$id$pendingExtension")
    }

    fun fileName(id: String) = "$id$extension"

    fun file(name: String) = File(dir, name)

    /** Makes a pending file permanent, in one atomic step. Returns its file name. */
    fun commit(id: String): String {
        val pending = pendingFile(id)
        if (!pending.isFile || pending.length() == 0L) throw IOException("Photo $id is missing or empty")
        val name = fileName(id)
        Files.move(pending.toPath(), file(name).toPath(), StandardCopyOption.ATOMIC_MOVE)
        return name
    }

    /** Undo for [commit], used when the database row couldn't be written, so a retry still works. */
    fun uncommit(id: String) {
        val committed = file(fileName(id))
        if (committed.isFile) Files.move(committed.toPath(), pendingFile(id).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    fun discard(id: String) {
        pendingFile(id).delete()
    }

    /**
     * The reconciliation loop: compare "files the database points at" with "files on disk" and
     * delete the difference (orphans from a crash, abandoned pending photos).
     *
     * Only files older than [graceMs] are touched. Without that grace period this job could race
     * a save in progress: the file is renamed, the row is a millisecond away, and the cleaner sees
     * an "orphan". Returns how many files were deleted.
     */
    fun reconcile(referenced: Set<String>, graceMs: Long = GRACE_MS): Int {
        val cutoff = clock() - graceMs
        val files = dir.listFiles() ?: return 0
        return files.count { f ->
            f.isFile && f.lastModified() < cutoff && f.name !in referenced && f.delete()
        }
    }

    companion object {
        const val GRACE_MS = 60 * 60 * 1000L // 1 hour
    }
}
