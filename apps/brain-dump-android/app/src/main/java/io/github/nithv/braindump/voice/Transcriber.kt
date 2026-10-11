package io.github.nithv.braindump.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/** What came of trying to turn a voice note into words. */
sealed interface Transcript {
    data class Text(val text: String) : Transcript
    /** The recognizer listened and heard no words. */
    data object NoSpeech : Transcript
    /** This phone can't transcribe ON THE DEVICE (too old, or no offline speech pack). */
    data class Unavailable(val reason: String) : Transcript
    data class Failed(val reason: String) : Transcript
}

/**
 * Turns a saved voice note into text using Android's ON-DEVICE speech recognizer only.
 *
 * Why on-device only: Android's default recognizer may send audio to a server. Brain Dump's promise
 * is that dumps never leave the phone, so we ask for the on-device one explicitly
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer]) and refuse to fall back to the other.
 *
 * Why from a file: the recognizer normally grabs the microphone itself, which clashes with our
 * own recording. Since Android 13 it can read audio we hand it instead, so we record first (the
 * voice note is saved no matter what), then decode the file to raw sound and feed it through a pipe.
 */
object Transcriber {
    // Found in the N4 spike (design doc §8): the recognizer finalises a sentence only after it
    // "hears" a pause, and it silently drops audio pushed much faster than real time (sentence two
    // of a test recording vanished). 4x worked on the emulator; 2x leaves headroom for slow phones.
    private const val TRAILING_SILENCE_MS = 1500
    private const val FEED_SPEED = 2.0

    /** Whether this phone could transcribe at all. Cheap; safe to call on the main thread. */
    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** [log] receives the recognizer's raw events, for diagnosing a phone that won't transcribe. */
    suspend fun transcribe(
        context: Context,
        audio: File,
        locale: Locale = Locale.getDefault(),
        log: (String) -> Unit = {},
    ): Transcript {
        if (Build.VERSION.SDK_INT < 33) return Transcript.Unavailable("Needs Android 13 or newer")
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return Transcript.Unavailable("No on-device speech recognizer")
        val pcm = try {
            withContext(Dispatchers.IO) { AudioDecoder.decodeMono16(audio) }
        } catch (e: Exception) {
            return Transcript.Failed("Couldn't read the recording: ${e.message}")
        }
        if (pcm.samples.isEmpty()) return Transcript.NoSpeech
        val language = withContext(Dispatchers.Main) { pickLanguage(context, locale) }
            ?: return Transcript.Unavailable("No offline speech pack for ${locale.toLanguageTag()}")
        log("language: $language")
        // Feeding takes (length / FEED_SPEED); allow generous slack on top for the recognizer.
        val timeoutMs = (pcm.samples.size * 1000L / pcm.sampleRate / FEED_SPEED).toLong() + 30_000
        return withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) { recognize(context, pcm, language, log) }
        } ?: Transcript.Failed("Timed out")
    }

    /**
     * The best INSTALLED offline language for [locale]: exactly it ("en-IN"), else the same language
     * in another region ("en-US"), else null. Asks in its own recognizer session; the spike showed
     * two sessions at once break each other.
     */
    @RequiresApi(33)
    private suspend fun pickLanguage(context: Context, locale: Locale): String? = suspendCancellableCoroutine { cont ->
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        fun done(tag: String?) {
            runCatching { recognizer.destroy() }
            if (cont.isActive) cont.resume(tag)
        }
        val wanted = locale.toLanguageTag()
        recognizer.checkRecognitionSupport(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, wanted),
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    val installed = support.installedOnDeviceLanguages
                    done(
                        installed.firstOrNull { it.equals(wanted, ignoreCase = true) }
                            ?: installed.firstOrNull { Locale.forLanguageTag(it).language == locale.language },
                    )
                }

                // Some recognizers can't answer the question; try the phone's own language anyway.
                override fun onError(error: Int) = done(wanted)
            },
        )
        cont.invokeOnCancellation { runCatching { recognizer.destroy() } }

    }

    @RequiresApi(33)
    private suspend fun recognize(context: Context, pcm: Pcm, language: String, log: (String) -> Unit): Transcript =
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
            var done = false
            fun finish(result: Transcript) {
                if (done) return
                done = true
                runCatching { recognizer.destroy() }
                runCatching { readEnd.close() }
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { runCatching { recognizer.destroy() }; runCatching { readEnd.close() } }

            // The recognizer splits a ramble into segments (roughly one per sentence) and reports each
            // one's final words, then says the whole session ended. We stitch the segments together.
            val segments = mutableListOf<String>()
            var lastPartial = ""
            fun words(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            fun finishWithWhatWeHeard() {
                // A last segment that never got its final result still counts (its latest partial words).
                if (lastPartial.isNotEmpty()) segments += lastPartial
                val text = segments.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                finish(if (text.isEmpty()) Transcript.NoSpeech else Transcript.Text(text))
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onSegmentResults(segmentResults: Bundle) {
                    val text = words(segmentResults)
                    log("segment: $text")
                    if (text.isNotEmpty()) segments += text
                    lastPartial = ""
                }

                override fun onEndOfSegmentedSession() {
                    log("session ended")
                    finishWithWhatWeHeard()
                }

                override fun onResults(results: Bundle?) {
                    log("results: ${words(results)}")
                    words(results).takeIf { it.isNotEmpty() }?.let { segments += it; lastPartial = "" }
                    finishWithWhatWeHeard()
                }

                override fun onError(error: Int) {
                    log("error $error")
                    // Words already heard are kept even if the end goes wrong.
                    if (segments.isNotEmpty() || lastPartial.isNotEmpty()) return finishWithWhatWeHeard()
                    finish(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Transcript.NoSpeech
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                Transcript.Unavailable("No offline speech pack for $language")
                            else -> Transcript.Failed("Recognizer error $error")
                        },
                    )
                }

                override fun onReadyForSpeech(params: Bundle?) = log("ready")
                override fun onBeginningOfSpeech() = log("speech began")
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = log("speech ended")
                override fun onPartialResults(partialResults: Bundle?) {
                    words(partialResults).takeIf { it.isNotEmpty() }?.let { lastPartial = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
                // One session for the whole recording, ending when the audio runs out.
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
            }
            recognizer.startListening(intent)

            // Feed the sound through the pipe from a background thread, then close it ("that's all").
            Thread {
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                    // Plus a moment of silence: the recognizer only finalises the last sentence once it
                    // "hears" a pause, and a recording can stop mid-breath.
                    val padded = pcm.samples.copyOf(pcm.samples.size + pcm.sampleRate * TRAILING_SILENCE_MS / 1000)
                    val bytes = ByteBuffer.allocate(padded.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    bytes.asShortBuffer().put(padded)
                    // Fed in chunks at [FEED_SPEED] times real time, like a microphone would.
                    val chunk = pcm.sampleRate / 10 * 2 // 100 ms of 16-bit mono
                    runCatching {
                        var at = 0
                        val raw = bytes.array()
                        while (at < raw.size) {
                            val n = minOf(chunk, raw.size - at)
                            out.write(raw, at, n)
                            at += n
                            Thread.sleep((100 / FEED_SPEED).toLong())
                        }
                    }.onFailure { log("feed failed: $it") }
                    log("fed ${bytes.capacity()} bytes")
                }
            }.apply { name = "voice-feed"; isDaemon = true }.start()
        }
}

class Pcm(val samples: ShortArray, val sampleRate: Int)

/** Decodes any audio file Android can read (M4A/AAC from our recorder, WAV, …) to 16-bit mono PCM. */
object AudioDecoder {
    fun decodeMono16(file: File): Pcm {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio in ${file.name}")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            val out = ShortArrayBuilder()
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                while (true) {
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        o >= 0 -> {
                            val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frame = ShortArray(buf.remaining()).also { buf.get(it) }
                            out.addDownmixed(frame, channels)
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return Pcm(out.toArray(), sampleRate)
        } finally {
            extractor.release()
        }
    }
}

private class ShortArrayBuilder {
    private var data = ShortArray(16_000)
    private var size = 0

    fun addDownmixed(frame: ShortArray, channels: Int) {
        val c = channels.coerceAtLeast(1)
        val n = frame.size / c
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        for (i in 0 until n) {
            var sum = 0
            for (ch in 0 until c) sum += frame[i * c + ch]
            data[size++] = (sum / c).toShort()
        }
    }

    fun toArray(): ShortArray = data.copyOf(size)
}
