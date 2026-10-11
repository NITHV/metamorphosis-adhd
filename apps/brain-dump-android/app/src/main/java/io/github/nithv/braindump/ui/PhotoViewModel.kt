package io.github.nithv.braindump.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.nithv.braindump.BrainDumpApp
import io.github.nithv.braindump.data.BrainDumpRepository
import io.github.nithv.braindump.data.MAX_CAPTURE_LENGTH
import io.github.nithv.braindump.data.PhotoShrinker
import io.github.nithv.braindump.data.PhotoTooBigException
import io.github.nithv.braindump.data.SloTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

sealed interface PhotoPhase {
    data object Closed : PhotoPhase
    data object Picking : PhotoPhase
    data object Shrinking : PhotoPhase
    data class Review(val id: String, val file: File) : PhotoPhase
    data class Saving(val id: String, val file: File) : PhotoPhase
    data class Failed(val message: String) : PhotoPhase
}

sealed interface PhotoEvent {
    data object Saved : PhotoEvent
    data object SaveFailed : PhotoEvent
}

/**
 * The photo sheet: pick → shrink (in the background) → review with an optional caption → save.
 * [shrink] and [open] are injectable so tests can fake the image work.
 */
class PhotoViewModel(
    private val repository: BrainDumpRepository,
    private val open: (Uri) -> InputStream,
    private val shrink: (open: () -> InputStream, out: File) -> Unit = PhotoShrinker::shrink,
) : ViewModel() {

    var phase by mutableStateOf<PhotoPhase>(PhotoPhase.Closed)
        private set

    var caption by mutableStateOf("")
        private set

    private val _events = Channel<PhotoEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun start() {
        caption = ""
        phase = PhotoPhase.Picking
    }

    fun updateCaption(text: String) {
        caption = text.take(MAX_CAPTURE_LENGTH)
    }

    /** Back to the two big buttons, throwing away the photo being reviewed. */
    fun retake() {
        discardPending()
        phase = PhotoPhase.Picking
    }

    fun close() {
        if (phase is PhotoPhase.Saving) return // let the save finish
        discardPending()
        phase = PhotoPhase.Closed
    }

    /**
     * A photo was taken or chosen. [cleanup] runs afterwards either way (used to delete the
     * full-size camera file, which we never keep).
     */
    fun onPicked(uri: Uri, cleanup: () -> Unit = {}) {
        discardPending()
        phase = PhotoPhase.Shrinking
        viewModelScope.launch {
            phase = try {
                withContext(Dispatchers.IO) {
                    val id = repository.newPhotoId()
                    val out = checkNotNull(repository.photos).pendingFile(id)
                    try {
                        SloTimer.measure("shrink_photo", SloTimer.SHRINK_PHOTO_TARGET_MS) { shrink({ open(uri) }, out) }
                    } catch (e: Exception) {
                        out.delete()
                        throw e
                    }
                    PhotoPhase.Review(id, out)
                }
            } catch (e: PhotoTooBigException) {
                PhotoPhase.Failed("That photo is too big, even after shrinking.")
            } catch (e: Exception) {
                PhotoPhase.Failed("Couldn't read that photo. Try another one.")
            } finally {
                withContext(Dispatchers.IO) { runCatching(cleanup) }
            }
        }
    }

    fun dump() {
        val review = phase as? PhotoPhase.Review ?: return
        phase = PhotoPhase.Saving(review.id, review.file)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.dumpPhoto(review.id, caption) }
                caption = ""
                phase = PhotoPhase.Closed
                _events.send(PhotoEvent.Saved)
            } catch (e: Exception) {
                phase = review // the photo and caption are still here: tap Dump it again
                _events.send(PhotoEvent.SaveFailed)
            }
        }
    }

    private fun discardPending() {
        when (val p = phase) {
            is PhotoPhase.Review -> repository.photos?.discard(p.id)
            else -> Unit
        }
    }

    /** Leaving the screen for good: don't leave a half-finished photo behind (the clean-up job would anyway). */
    override fun onCleared() {
        discardPending()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BrainDumpApp
                PhotoViewModel(app.repository, open = { uri ->
                    checkNotNull(app.contentResolver.openInputStream(uri)) { "Can't open $uri" }
                })
            }
        }
    }
}
