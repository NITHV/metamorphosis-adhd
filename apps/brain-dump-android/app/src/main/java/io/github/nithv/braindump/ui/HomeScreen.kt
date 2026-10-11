package io.github.nithv.braindump.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nithv.braindump.BuildConfig
import io.github.nithv.braindump.R
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.data.MAX_CAPTURE_LENGTH
import io.github.nithv.braindump.greeting
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    padding: PaddingValues,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    photoVm: PhotoViewModel = viewModel(factory = PhotoViewModel.Factory),
    voiceVm: VoiceViewModel = viewModel(factory = VoiceViewModel.Factory),
) {
    val c = BrainDumpTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            // Each message gets its own coroutine, so a waiting "Undo" never delays the next one.
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    HomeEvent.Saved -> snackbar.showSnackbar("Saved ✓")
                    is HomeEvent.SaveFailed -> snackbar.showSnackbar(event.reason)
                    is HomeEvent.Failed -> snackbar.showSnackbar(event.message)
                    is HomeEvent.Split -> snackbar.showSnackbar("Split into ${event.parts}")
                    is HomeEvent.Cleared -> {
                        val result = snackbar.showSnackbar("Cleared from inbox", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.restore(event.id)
                    }
                    is HomeEvent.Filed -> {
                        val result = snackbar.showSnackbar("Filed to ${event.kind.look.pile}", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.unsort(event.captureId)
                    }
                }
            }
        }
    }

    LaunchedEffect(photoVm) {
        photoVm.events.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    PhotoEvent.Saved -> snackbar.showSnackbar("Saved ✓")
                    PhotoEvent.SaveFailed -> snackbar.showSnackbar("Couldn't save the photo. Try again.")
                }
            }
        }
    }

    LaunchedEffect(voiceVm) {
        voiceVm.events.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    is VoiceEvent.Saved -> snackbar.showSnackbar(if (event.transcribing) "Saved ✓ Turning it into words…" else "Saved ✓")
                    VoiceEvent.SaveFailed -> snackbar.showSnackbar("Couldn't save the recording. Try again.")
                }
            }
        }
    }

    // Re-render relative times ("5m") and dates ("Tomorrow") every half minute.
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val now = remember(nowMs) { LocalDateTime.now() }

    val inbox = state.inbox
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") { Hero(toSort = inbox?.size) }
        item(key = "dump") {
            DumpBox(
                text = vm.draft,
                onTextChange = { vm.updateDraft(it.take(MAX_CAPTURE_LENGTH)) },
                onDump = vm::dump,
                onPhoto = photoVm::start,
                onVoice = voiceVm::open,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item(key = "inbox-heading") { InboxHeading(count = inbox?.size ?: 0) }
        when {
            inbox == null -> Unit // loading: show nothing rather than a misleading "empty"
            inbox.isEmpty() -> item(key = "empty") { EmptyInbox() }
            else -> items(inbox, key = { it.id }) { row ->
                InboxRowCard(
                    row = row,
                    nowMs = nowMs,
                    now = now,
                    onClear = { vm.clear(row.id) },
                    onSort = { kind -> vm.sort(row.id, kind) },
                    onSplit = { vm.split(row.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "footer") {
            Text(
                "Brain Dump ${BuildConfig.VERSION_NAME} · works offline · no internet permission",
                color = c.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }
    }
    PhotoSheet(photoVm)
    VoiceSheet(voiceVm)
}

@Composable
private fun Hero(toSort: Int?) {
    val c = BrainDumpTheme.colors
    val hello = remember { greeting(LocalTime.now().hour) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .chunky(fill = c.brand, ink = c.ink, radius = 24.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.mascot),
            contentDescription = "Brain Dump mascot",
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hello, color = c.brandForeground.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Get it out of your head.",
                color = c.brandForeground,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (toSort != null && toSort > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$toSort to sort",
                    color = c.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(c.card, CircleShape)
                        .border(2.dp, c.ink, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun DumpBox(
    text: String,
    onTextChange: (String) -> Unit,
    onDump: () -> Unit,
    onPhoto: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BrainDumpTheme.colors
    val canDump = text.isNotBlank()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chunky(fill = c.card, ink = c.ink)
            .padding(12.dp),
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            if (text.isEmpty()) {
                Text("What's on your mind? A task, an idea, a worry…", color = c.muted, fontSize = 18.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = c.foreground, fontSize = 18.sp),
                cursorBrush = SolidColor(c.brand),
                // Enter adds a new line on a phone keyboard; the button saves (same as the website).
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "What's on your mind?" },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolButton("🎙️", "Record a voice dump", onVoice)
                ToolButton("📷", "Take or choose a photo to dump", onPhoto)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alpha(if (canDump) 1f else 0.5f)
                    .chunky(fill = c.brand, ink = c.ink, radius = 14.dp, shadow = 3.dp)
                    .clickable(enabled = canDump, onClick = onDump)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 28.dp),
            ) {
                Text("Dump it", color = c.brandForeground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InboxHeading(count: Int) {
    val c = BrainDumpTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    ) {
        Text("📥", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text("Inbox", color = c.foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                color = c.foreground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(c.hairline, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (count > 0) Text("Tap a pile to file it", color = c.muted, fontSize = 13.sp)
    }
}

@Composable
private fun InboxRowCard(
    row: InboxRow,
    nowMs: Long,
    now: LocalDateTime,
    onClear: () -> Unit,
    onSort: (Kind) -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = BrainDumpTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .chunky(fill = c.card, ink = c.ink, radius = 14.dp, shadow = 3.dp)
            .padding(end = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 48dp touch target around a 24dp circle (Android's minimum comfortable tap size).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "Clear \"${row.text.take(40)}\" from inbox" },
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .border(2.dp, c.foreground, CircleShape),
                )
            }
            Text(
                row.text,
                color = c.foreground,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 13.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                relativeTime(row.createdAt, nowMs),
                color = c.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 15.dp),
            )
        }
        if (row.photo != null) {
            PhotoThumb(row.photo, row.text, Modifier.padding(start = 48.dp, top = 8.dp))
        }
        if (row.audio != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp, top = 8.dp)) {
                PlayButton(row.audio)
                if (row.transcribing) {
                    Spacer(Modifier.width(10.dp))
                    Text("✍️ Turning speech into words…", color = c.muted, fontSize = 12.sp)
                }
            }
        }
        Column(Modifier.padding(start = 48.dp, top = 8.dp)) {
            if (row.due != null) {
                Text(
                    "📅 ${formatDue(row.due, now)}",
                    color = c.purple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Kind.entries.forEach { kind ->
                    KindChip(
                        kind = kind,
                        suggested = row.suggested == kind,
                        onClick = { onSort(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (row.canSplit) {
                Text(
                    "✂ Split",
                    color = c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = c.hairline,
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onSplit)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** A pile button. The suggested one is filled in its pile's colour; the rest are outlines. */
@Composable
fun KindChip(kind: Kind, suggested: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    val base = if (suggested) {
        modifier.chunky(fill = kind.soft(c), ink = kind.color(c), radius = 20.dp, shadow = 2.dp)
    } else {
        modifier.border(2.dp, c.hairline, CircleShape)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = base
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .semantics { contentDescription = "File as ${kind.look.label}" + if (suggested) " (suggested)" else "" },
    ) {
        Text(
            kind.look.label,
            color = if (suggested) kind.color(c) else c.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyInbox() {
    val c = BrainDumpTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = c.hairline,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                )
            }
            .padding(vertical = 36.dp, horizontal = 16.dp),
    ) {
        Text("Inbox empty. Your head is clear 🌤️", color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** A square tool button beside "Dump it" (voice, photo). */
@Composable
private fun ToolButton(icon: String, label: String, onClick: () -> Unit) {
    val c = BrainDumpTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .chunky(fill = c.card, ink = c.ink, radius = 14.dp, shadow = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(icon, fontSize = 20.sp)
    }
}
