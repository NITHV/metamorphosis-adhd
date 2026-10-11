package io.github.nithv.braindump.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import io.github.nithv.braindump.voice.Playback
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// The Pause sheet: leave a note for future you before switching tasks
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(vm: PauseViewModel) {
    val context = LocalContext.current
    val c = BrainDumpTheme.colors
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else vm.permissionDenied()
    }
    // Android cuts the mic off in the background, so leaving mid-note keeps what we have.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.stopRecording() }

    if (!vm.isOpen) return

    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var showExtras by rememberSaveable { mutableStateOf(vm.tag.isNotEmpty() || vm.link.isNotEmpty()) }

    ModalBottomSheet(
        // Swiping it away keeps everything (like closing the web sheet would not); only Cancel discards.
        onDismissRequest = { vm.stopRecording(); vm.hide() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column {
                Text("⏸ Pause", color = c.foreground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Leave a note for future you, so getting back in is easy.", color = c.muted, fontSize = 14.sp)
            }

            when (val r = vm.recording) {
                PauseRecording.None -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .chunky(fill = c.card, ink = c.ink, radius = 16.dp, shadow = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = ::record)
                        .heightIn(min = 56.dp),
                ) {
                    Text("🎙️ Say where you're up to", color = c.red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                is PauseRecording.Recording -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.redSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(mmss(vm.elapsedMs), color = c.red, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.weight(1f)) { LevelMeter(vm.level) }
                    PillButton("■ Stop", onClick = vm::stopRecording)
                }
                is PauseRecording.Done -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.redSoft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("🎙️ Voice note added ✓", color = c.red, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PlayButton(r.file)
                    Text(
                        "Redo",
                        color = c.red,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { Playback.stop(); record() }
                            .padding(8.dp),
                    )
                }
            }
            if (vm.micDenied) {
                Text(
                    "The microphone is off for Brain Dump. You can still type, or allow it in the app's settings.",
                    color = c.muted,
                    fontSize = 13.sp,
                )
            }

            Field(
                label = "Where are you up to?",
                value = vm.whereText,
                onChange = vm::updateWhere,
                placeholder = "Halfway through the budget sheet…",
                minLines = 2,
            )
            Field(
                label = "Next, I need to…",
                value = vm.nextStep,
                onChange = vm::updateNext,
                placeholder = "fill in March",
                imeAction = ImeAction.Done,
            )

            if (!showExtras) {
                Text(
                    "+ Add a tag or link",
                    color = c.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { showExtras = true }
                        .padding(vertical = 8.dp),
                )
            } else {
                Field(label = "Project or tag", value = vm.tag, onChange = vm::updateTag, placeholder = "Taxes")
                Field(
                    label = "Link to what you had open",
                    value = vm.link,
                    onChange = vm::updateLink,
                    placeholder = "docs.google.com/…",
                    keyboardType = KeyboardType.Uri,
                )
            }

            vm.error?.let { Text(it, color = c.red, fontSize = 14.sp, fontWeight = FontWeight.Medium) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Cancel", Modifier.weight(1f)) { Playback.stop(); vm.cancel() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(2f)
                        .alpha(if (vm.canSave) 1f else 0.5f)
                        .chunky(fill = c.brand, ink = c.ink, radius = 14.dp, shadow = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = vm.canSave, role = Role.Button) { Playback.stop(); vm.save() }
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (vm.saving) "Saving…" else "Save my place",
                        color = c.brandForeground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Default,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = BrainDumpTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = c.foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        // The border and padding are drawn by the field itself, so a tap anywhere in the box focuses it.
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = c.foreground, fontSize = 16.sp),
            cursorBrush = SolidColor(c.brand),
            minLines = minLines,
            singleLine = minLines == 1,
            keyboardOptions = KeyboardOptions(
                capitalization = if (keyboardType == KeyboardType.Uri) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                autoCorrectEnabled = keyboardType != KeyboardType.Uri,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(2.dp, c.hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 16.sp)
                    inner()
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The Paused screen
// ---------------------------------------------------------------------------------------------

@Composable
fun PausedScreen(vm: PausedViewModel, padding: PaddingValues, onPause: () -> Unit) {
    val c = BrainDumpTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val nowMs = rememberNow()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { Playback.stop() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Paused", color = c.foreground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Where did I leave off? Your bookmarks for getting back in.", color = c.muted, fontSize = 15.sp)
                }
                Spacer(Modifier.width(10.dp))
                PillButton("⏸ Pause", onClick = onPause)
            }
        }
        val active = state.active
        when {
            active == null -> Unit
            active.isEmpty() -> item(key = "empty") { NothingPaused(onPause) }
            else -> items(active, key = { it.id }) { row ->
                CheckpointCard(
                    row = row,
                    nowMs = nowMs,
                    onResume = { vm.resume(row.id) },
                    onLetGo = { vm.letGo(row.id) },
                    onKeep = { vm.keep(row.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.resumed.isNotEmpty()) {
            item(key = "history") {
                Column(Modifier.padding(top = 18.dp)) {
                    Text("RECENTLY RESUMED", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(2.dp, c.hairline, RoundedCornerShape(16.dp)),
                    ) {
                        state.resumed.forEachIndexed { i, row ->
                            if (i > 0) Box(Modifier.fillMaxWidth().size(2.dp).background(c.hairline))
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    row.nextStep ?: row.body.ifEmpty { "Voice note" },
                                    color = c.muted,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(relativeTime(row.resumedAt ?: row.createdAt, nowMs), color = c.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NothingPaused(onPause: () -> Unit) {
    val c = BrainDumpTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = c.hairline,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(vertical = 32.dp, horizontal = 16.dp),
    ) {
        Text(
            "Nothing paused. When you get pulled away from something, pause it here first.",
            color = c.muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "⏸ Pause something now",
            color = c.blue,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onPause)
                .padding(10.dp),
        )
    }
}

/** One bookmark. [compact] (Home) trims long text and skips the "still relevant?" question. */
@Composable
fun CheckpointCard(
    row: CheckpointRow,
    nowMs: Long,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    onLetGo: (() -> Unit)? = null,
    onKeep: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val c = BrainDumpTheme.colors
    val context = LocalContext.current
    val askStillRelevant = row.stale && !compact && onKeep != null && onLetGo != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chunky(fill = c.card, ink = c.ink, radius = 18.dp)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            row.tag?.let {
                Text(
                    it,
                    color = c.purple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(c.purpleSoft, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Paused ${relativeTime(row.createdAt, nowMs).let { if (it == "now") "just now" else "$it ago" }}", color = c.muted, fontSize = 12.sp)
        }
        if (row.body.isNotEmpty()) {
            Text(
                row.body,
                color = c.foreground,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = if (compact) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        row.nextStep?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .background(c.brandSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text("NEXT →", color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(it, color = c.foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (row.audio != null || row.link != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                row.audio?.let { PlayButton(it) }
                if (row.transcribing) Text("✍️ Words coming…", color = c.muted, fontSize = 12.sp)
                row.link?.let { link ->
                    Text(
                        "↗ ${row.linkHost ?: "Open link"}",
                        color = c.blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(c.blueSoft, CircleShape)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                // Opens in the browser: Brain Dump itself still has no internet permission.
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                } catch (e: ActivityNotFoundException) {
                                    // No browser on the phone: nothing sensible to do, and nothing lost.
                                }
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (askStillRelevant) {
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(
                            color = c.hairline,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                            cornerRadius = CornerRadius(12.dp.toPx()),
                        )
                    }
                    .padding(12.dp),
            ) {
                Text("Paused over a week ago. Still relevant?", color = c.foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PillButton("Keep it", Modifier.weight(1f), onClick = onKeep!!)
                    PillButton("Let it go 🍃", Modifier.weight(1f), onClick = onLetGo!!)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .chunky(fill = c.brand, ink = c.ink, radius = 14.dp, shadow = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onResume)
                    .heightIn(min = 48.dp),
            ) {
                Text("I'm back on it", color = c.brandForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            if (!askStillRelevant && onLetGo != null) {
                Text(
                    "Let it go",
                    color = c.muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onLetGo)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Home's "Where you left off": the newest pause, so coming back starts with your bookmark. */
@Composable
fun ResumeCard(vm: PausedViewModel, onSeeAll: () -> Unit) {
    val c = BrainDumpTheme.colors
    val latest by vm.latest.collectAsStateWithLifecycle()
    val (row, count) = latest ?: return
    val nowMs = rememberNow()
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Text("⏸", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text("Where you left off", color = c.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (count > 1) {
                Text(
                    "All paused ($count)",
                    color = c.blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onSeeAll)
                        .padding(8.dp),
                )
            }
        }
        CheckpointCard(row = row, nowMs = nowMs, onResume = { vm.resume(row.id) }, compact = true)
    }
}

/** The current time, refreshed every half minute for "Paused 5m ago". */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}
