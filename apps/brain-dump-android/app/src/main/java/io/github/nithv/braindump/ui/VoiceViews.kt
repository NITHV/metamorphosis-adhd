package io.github.nithv.braindump.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import io.github.nithv.braindump.voice.MAX_VOICE_MS
import io.github.nithv.braindump.voice.Playback
import io.github.nithv.braindump.voice.Transcriber
import java.io.File

/**
 * Record a voice note. Asks for the microphone the first time only; if refused, says how to allow
 * it later instead of nagging. The note is saved the moment you stop; its words come later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(vm: VoiceViewModel) {
    val context = LocalContext.current
    val c = BrainDumpTheme.colors
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.permissionDenied()
    }
    val canTranscribe = remember { Transcriber.isSupported(context) }

    // Android cuts the mic off for apps in the background, so leaving mid-note saves what we have.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopAndSave() }
    // Leaving the screen stops any note that's playing.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { Playback.stop() }

    val phase = vm.phase
    if (phase == VoicePhase.Closed) return

    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.start()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (phase is VoicePhase.Recording) vm.stopAndSave() else vm.cancel() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            when (phase) {
                VoicePhase.Ready -> {
                    Text("🎙️ Voice dump", color = c.muted, fontWeight = FontWeight.SemiBold)
                    BigRoundButton(label = "Start recording", fill = c.brand, onClick = ::record) {
                        Text("🎙️", fontSize = 34.sp)
                    }
                    Text("Tap to start. Up to 2 minutes.", color = c.muted, fontSize = 14.sp)
                    if (!canTranscribe) {
                        Text(
                            "This phone can't turn speech into text offline, so notes are kept as recordings you can play back.",
                            color = c.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is VoicePhase.Recording -> {
                    Text("Listening…", color = c.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(mmss(vm.elapsedMs), color = c.foreground, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    LevelMeter(vm.level)
                    BigRoundButton(label = "Stop and save", fill = c.red, onClick = vm::stopAndSave) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .background(c.card, RoundedCornerShape(6.dp)),
                        )
                    }
                    Text(
                        "Stops by itself at ${mmss(MAX_VOICE_MS.toLong())}",
                        color = c.muted,
                        fontSize = 13.sp,
                    )
                    Text(
                        "Cancel",
                        color = c.muted,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = vm::cancel)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
                VoicePhase.Saving -> Text("Saving…", color = c.muted, modifier = Modifier.padding(vertical = 40.dp))
                VoicePhase.PermissionDenied -> {
                    Text("🎙️ Microphone is off", color = c.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Brain Dump needs the microphone only while you record. You can allow it in the app's settings.",
                        color = c.muted,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Close", Modifier.weight(1f), onClick = vm::cancel)
                        PillButton("Open settings", Modifier.weight(1f)) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                            vm.cancel()
                        }
                    }
                }
                is VoicePhase.Failed -> {
                    Text("🎙️ Hmm.", color = c.foreground, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(phase.message, color = c.muted, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Close", Modifier.weight(1f), onClick = vm::cancel)
                        PillButton("Try again", Modifier.weight(1f)) {
                            vm.open()
                            record()
                        }
                    }
                }
                VoicePhase.Closed -> Unit
            }
        }
    }
}

private fun mmss(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun BigRoundButton(
    label: String,
    fill: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = BrainDumpTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(96.dp)
            .chunky(fill = fill, ink = c.ink, radius = 48.dp, shadow = 4.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}

@Composable
private fun LevelMeter(level: Float) {
    val c = BrainDumpTheme.colors
    Box(
        Modifier
            .fillMaxWidth(0.7f)
            .height(10.dp)
            .background(c.hairline, CircleShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth((level * 1.6f).coerceIn(0.03f, 1f))
                .height(10.dp)
                .background(c.brand, CircleShape),
        )
    }
}

@Composable
private fun PillButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = BrainDumpTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .border(2.dp, c.hairline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp),
    ) {
        Text(text, color = c.foreground, fontWeight = FontWeight.SemiBold)
    }
}

/** Play/stop for a voice note. */
@Composable
fun PlayButton(file: File, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    val playing by Playback.playing.collectAsStateWithLifecycle()
    val isPlaying = playing == file.path
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(2.dp, c.hairline, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button) { Playback.toggle(file) }
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = if (isPlaying) "Stop voice note" else "Play voice note" },
    ) {
        Text(if (isPlaying) "■  Stop" else "▶  Play", color = c.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
