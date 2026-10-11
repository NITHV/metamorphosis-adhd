package io.github.nithv.braindump.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Same limit as the website (apps/brain-dump/src/components/voice/voice-recorder.tsx). */
const val MAX_VOICE_MS = 120_000

/** What the voice sheet needs from a recorder; faked in tests. */
interface Recorder {
    val elapsedMs: Long
    fun start(file: File, onLimit: () -> Unit)
    fun level(): Float
    fun stop(): Boolean
}

/**
 * Records one voice note to an .m4a file (AAC, mono, 16 kHz, 32 kbit/s: plenty for speech, about
 * 4 KB a second, so a 2-minute note is ~480 KB). Not thread-safe; use from the main thread.
 */
class VoiceRecorder(private val context: Context) : Recorder {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    /** Milliseconds recorded so far. */
    override val elapsedMs get() = if (recorder == null) 0L else SystemClock.elapsedRealtime() - startedAt

    /** Starts recording into [file]. [onLimit] runs if the 2-minute limit stops it. Throws if the mic can't open. */
    override fun start(file: File, onLimit: () -> Unit) {
        check(recorder == null) { "Already recording" }
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_VOICE_MS)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
            }
            r.setOutputFile(file.path)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release()
            throw e
        }
        recorder = r
        startedAt = SystemClock.elapsedRealtime()
    }

    /** How loud right now, 0..1, for the level meter. */
    override fun level(): Float = (recorder?.maxAmplitude ?: 0) / 32_767f

    /**
     * Stops and finishes the file. Returns false if nothing usable was recorded (Android throws
     * when stop() comes a split second after start(), before any audio arrived).
     */
    override fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            r.release()
        }
    }

    /** Throws the recording away (the caller deletes the file). */
    fun cancel() {
        stop()
    }
}
