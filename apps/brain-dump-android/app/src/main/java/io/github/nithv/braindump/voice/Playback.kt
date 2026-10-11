package io.github.nithv.braindump.voice

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays one voice note at a time: starting another stops the first. Main thread only. */
object Playback {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)

    /** Path of the note playing now, or null. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    fun toggle(file: File) {
        if (_playing.value == file.path) return stop()
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(file.path)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepare() // a local file of a few hundred KB: quick
            p.start()
        } catch (e: Exception) {
            p.release()
            return
        }
        player = p
        _playing.value = file.path
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
    }
}
