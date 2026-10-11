package io.github.nithv.braindump.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import java.io.File

/**
 * Take or choose a photo, then add an optional caption and save.
 *
 * Neither path needs a permission: the camera button hands off to the phone's own camera app, and
 * "Choose" uses Android's Photo Picker, which only gives us the one photo you pick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSheet(vm: PhotoViewModel) {
    val context = LocalContext.current
    val c = BrainDumpTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current

    // Where the camera app writes the full-size photo. Saved state: Android may close Brain Dump
    // while the camera is open, and the result must still find the file when it comes back.
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraPath?.let(::File)
        cameraPath = null
        if (ok && file != null && file.length() > 0) {
            vm.onPicked(Uri.fromFile(file), cleanup = { file.delete() })
        } else {
            file?.delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPicked(uri)
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        cameraPath = file.path
        try {
            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (e: ActivityNotFoundException) {
            cameraPath = null
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val phase = vm.phase
    if (phase == PhotoPhase.Closed) return

    ModalBottomSheet(
        onDismissRequest = vm::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            when (phase) {
                PhotoPhase.Picking -> {
                    Text("📷 Photo dump", color = c.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    SheetButton("📸  Take photo", primary = true, onClick = ::openCamera)
                    SheetButton("Choose from gallery") {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    SheetButton("Cancel", quiet = true, onClick = vm::close)
                }
                PhotoPhase.Shrinking -> Text(
                    "Getting your photo ready…",
                    color = c.muted,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                )
                is PhotoPhase.Review, is PhotoPhase.Saving -> {
                    val file = (phase as? PhotoPhase.Review)?.file ?: (phase as PhotoPhase.Saving).file
                    val saving = phase is PhotoPhase.Saving
                    PhotoPreview(
                        file,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(2.dp, c.hairline, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    ) {
                        if (vm.caption.isEmpty()) Text("Add a caption (optional)", color = c.muted, fontSize = 16.sp)
                        BasicTextField(
                            value = vm.caption,
                            onValueChange = vm::updateCaption,
                            enabled = !saving,
                            textStyle = TextStyle(color = c.foreground, fontSize = 16.sp),
                            cursorBrush = SolidColor(c.brand),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Caption" },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetButton("Retake", quiet = true, enabled = !saving, modifier = Modifier.weight(1f), onClick = vm::retake)
                        SheetButton(
                            if (saving) "Saving…" else "Dump it",
                            primary = true,
                            enabled = !saving,
                            modifier = Modifier.weight(2f),
                            onClick = {
                                keyboard?.hide()
                                vm.dump()
                            },
                        )
                    }
                }
                is PhotoPhase.Failed -> {
                    Text("📷 Hmm.", color = c.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(phase.message, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetButton("Close", quiet = true, modifier = Modifier.weight(1f), onClick = vm::close)
                        SheetButton("Try another", modifier = Modifier.weight(1f), onClick = vm::retake)
                    }
                }
                PhotoPhase.Closed -> Unit
            }
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    quiet: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = BrainDumpTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val base = when {
        primary -> modifier.chunky(fill = c.brand, ink = c.ink, radius = 14.dp, shadow = 3.dp)
        quiet -> modifier.border(2.dp, c.hairline, shape)
        else -> modifier.chunky(fill = c.card, ink = c.ink, radius = 14.dp, shadow = 3.dp)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = base
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp),
    ) {
        Text(
            text,
            color = if (primary) c.brandForeground else if (quiet) c.muted else c.foreground,
            fontSize = 16.sp,
            fontWeight = if (quiet) FontWeight.SemiBold else FontWeight.Bold,
        )
    }
}
