package io.github.nithv.braindump.voice

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.nithv.braindump.data.BrainDumpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Turns saved voice notes into words in the background, through a small DURABLE queue.
 *
 * The note is saved first; its id is written to disk here before any transcribing starts. If the
 * app is closed halfway, the id is still on disk and [resume] (at the next start) finishes the job:
 * at-least-once processing, the same idea as a server's job queue, in a SharedPreferences file.
 * One note at a time ([Mutex]): two recognizer sessions at once break each other (N4 spike).
 */
class Transcriptions(
    private val repository: BrainDumpRepository,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val transcribe: suspend (File) -> Transcript,
) {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow(prefs.getStringSet(KEY_PENDING, emptySet())!!.toSet())

    /** Voice notes still waiting for their words (the Inbox shows "Transcribing…"). */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    /** Why the last note couldn't be transcribed on this phone, if it couldn't (shown once as a hint). */
    private val _unavailable = MutableStateFlow<String?>(null)
    val unavailable: StateFlow<String?> = _unavailable.asStateFlow()

    /** Call right after a voice note is saved. Returns the background job (tests wait on it). */
    fun enqueue(captureId: String): Job {
        update(_pending.value + captureId)
        return scope.launch { process(captureId) }
    }

    /** At app start: finish anything an earlier run didn't. */
    fun resume() {
        _pending.value.forEach { id -> scope.launch { process(id) } }
    }

    /** Runs one note. Internal so tests can await it. */
    internal suspend fun process(captureId: String) = mutex.withLock {
        if (captureId !in _pending.value) return@withLock // done meanwhile
        val file = repository.getCapture(captureId)?.audioFile?.let(repository::voiceFile)
        if (file == null || !file.isFile) return@withLock done(captureId) // deleted, or nothing to read
        when (val result = runCatching { transcribe(file) }.getOrElse { Transcript.Failed(it.toString()) }) {
            is Transcript.Text -> {
                repository.setTranscript(captureId, result.text)
                done(captureId)
            }
            Transcript.NoSpeech -> done(captureId)
            is Transcript.Unavailable -> {
                _unavailable.value = result.reason
                done(captureId)
            }
            is Transcript.Failed -> {
                // Maybe a one-off (recognizer busy, app closed). Try again next start, but not forever.
                val attempts = prefs.getInt(attemptsKey(captureId), 0) + 1
                Log.w(TAG, "Transcription attempt $attempts failed: ${result.reason}")
                if (attempts >= MAX_ATTEMPTS) done(captureId)
                else prefs.edit().putInt(attemptsKey(captureId), attempts).apply()
            }
        }
    }

    private fun done(captureId: String) {
        prefs.edit().remove(attemptsKey(captureId)).apply()
        update(_pending.value - captureId)
    }

    private fun update(ids: Set<String>) {
        _pending.value = ids
        // commit(), not apply(): the queue must be on disk before we rely on it.
        prefs.edit().putStringSet(KEY_PENDING, ids).commit()
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val TAG = "BrainDump/Voice"
        private const val KEY_PENDING = "pending"
        private fun attemptsKey(id: String) = "attempts:$id"

        /** The queue's file. Tests pass their own [name]: the real app's startup job uses the default one. */
        fun prefs(context: Context, name: String = "transcriptions"): SharedPreferences =
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
