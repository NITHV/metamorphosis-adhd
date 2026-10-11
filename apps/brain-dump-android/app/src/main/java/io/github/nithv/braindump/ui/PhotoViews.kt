package io.github.nithv.braindump.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nithv.braindump.data.PhotoShrinker
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Small, decoded thumbnails kept in memory so scrolling doesn't decode the same file twice.
 * Bounded by bytes (about 16 MB), so a long Inbox can't run the phone out of memory.
 */
private object Thumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun load(file: File, maxSide: Int): ImageBitmap? {
        val key = "${file.path}@$maxSide"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoShrinker.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            }
            BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()?.also { cache.put(key, it) }
        }
    }
}

@Composable
private fun rememberPhoto(file: File, maxSide: Int): PhotoLoad {
    var state by remember(file, maxSide) { mutableStateOf<PhotoLoad>(PhotoLoad.Loading) }
    LaunchedEffect(file, maxSide) {
        val image = Thumbnails.load(file, maxSide)
        state = if (image != null) PhotoLoad.Ready(image) else PhotoLoad.Broken
    }
    return state
}

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Broken : PhotoLoad
    data class Ready(val image: ImageBitmap) : PhotoLoad
}

/** A photo dump's thumbnail. Tap to see it full-screen. */
@Composable
fun PhotoThumb(file: File, description: String, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val box = modifier
        .size(width = 128.dp, height = 96.dp)
        .clip(shape)
        .background(c.surface)
        .border(2.dp, c.ink, shape)
    when (val s = rememberPhoto(file, 400)) {
        PhotoLoad.Loading -> Box(box)
        PhotoLoad.Broken -> Box(box, contentAlignment = Alignment.Center) {
            Text("📷 Can't load photo", color = c.muted, fontSize = 11.sp)
        }
        is PhotoLoad.Ready -> Image(
            bitmap = s.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = box
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "View photo: ${description.take(40)}" },
        )
    }
    if (open) PhotoViewer(file, description, onClose = { open = false })
}

/** Full-screen view. Tap anywhere (or Back) to close. */
@Composable
fun PhotoViewer(file: File, description: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onClose)
                .systemBarsPadding()
                .padding(12.dp),
        ) {
            when (val s = rememberPhoto(file, 2048)) {
                is PhotoLoad.Ready -> Image(
                    bitmap = s.image,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                PhotoLoad.Broken -> Text("📷 Can't load photo", color = Color.White)
                PhotoLoad.Loading -> Unit
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .semantics { contentDescription = "Close photo" },
            ) {
                Text("✕", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

/** The photo being reviewed before saving: fit inside the sheet, not cropped. */
@Composable
fun PhotoPreview(file: File, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val box = modifier
        .clip(shape)
        .background(c.surface)
        .border(2.dp, c.ink, shape)
    when (val s = rememberPhoto(file, 1600)) {
        is PhotoLoad.Ready -> Image(s.image, contentDescription = "Your photo", contentScale = ContentScale.Fit, modifier = box)
        else -> Box(box)
    }
}
