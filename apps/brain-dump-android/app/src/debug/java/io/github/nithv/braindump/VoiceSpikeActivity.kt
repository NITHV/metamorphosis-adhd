package io.github.nithv.braindump

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import io.github.nithv.braindump.voice.AudioDecoder
import io.github.nithv.braindump.voice.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * The N4 spike, kept as a debug-only diagnostic. Answers: can THIS phone turn a saved recording
 * into text, on the device? Copy an audio file into the app's files/ folder, then:
 *   adb shell am start -n io.github.nithv.braindump.debug/io.github.nithv.braindump.VoiceSpikeActivity --es file spike.wav
 * Results go to logcat under "BrainDump/Spike".
 */
class VoiceSpikeActivity : Activity() {
    private val tag = "BrainDump/Spike"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(tag, "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}, locale ${Locale.getDefault().toLanguageTag()}")
        Log.i(tag, "on-device recognition: ${Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)}")
        val name = intent.getStringExtra("file") ?: return
        MainScope().launch {
            // --ez encode true: first re-encode the file to AAC .m4a on the phone (what our recorder
            // makes), to test the real decode path end to end.
            val file = if (intent.getBooleanExtra("encode", false)) {
                val out = File(filesDir, name.substringBeforeLast('.') + ".m4a")
                withContext(Dispatchers.IO) { encodeAac(File(filesDir, name), out) }
                Log.i(tag, "encoded ${out.name}: ${out.length()} bytes")
                out
            } else {
                File(filesDir, name)
            }
            val t0 = System.currentTimeMillis()
            val pcm = runCatching { AudioDecoder.decodeMono16(file) }
            Log.i(tag, "decoded ${file.name}: ${pcm.getOrNull()?.let { "${it.samples.size * 1000L / it.sampleRate} ms @ ${it.sampleRate} Hz" } ?: pcm.exceptionOrNull()} in ${System.currentTimeMillis() - t0} ms")
            val t1 = System.currentTimeMillis()
            val result = Transcriber.transcribe(this@VoiceSpikeActivity, file, log = { Log.i(tag, "  recognizer: $it") })
            Log.i(tag, "RESULT after ${System.currentTimeMillis() - t1} ms: $result")
        }
    }
}

/** Encodes any decodable audio file to AAC-in-MP4 (mono, 32 kbit/s), like MediaRecorder would. */
private fun encodeAac(input: File, output: File) {
    val pcm = AudioDecoder.decodeMono16(input)
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, 1).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
    val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var track = -1
    val info = MediaCodec.BufferInfo()
    var offset = 0
    var inputDone = false
    while (true) {
        if (!inputDone) {
            val i = codec.dequeueInputBuffer(10_000)
            if (i >= 0) {
                val buf = codec.getInputBuffer(i)!!
                val n = minOf(buf.capacity() / 2, pcm.samples.size - offset)
                if (n <= 0) {
                    codec.queueInputBuffer(i, 0, 0, offset * 1_000_000L / pcm.sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm.samples, offset, n)
                    codec.queueInputBuffer(i, 0, n * 2, offset * 1_000_000L / pcm.sampleRate, 0)
                    offset += n
                }
            }
        }
        val o = codec.dequeueOutputBuffer(info, 10_000)
        if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track = muxer.addTrack(codec.outputFormat)
            muxer.start()
        } else if (o >= 0) {
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                muxer.writeSampleData(track, codec.getOutputBuffer(o)!!, info)
            }
            codec.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }
    codec.stop(); codec.release()
    muxer.stop(); muxer.release()
}
