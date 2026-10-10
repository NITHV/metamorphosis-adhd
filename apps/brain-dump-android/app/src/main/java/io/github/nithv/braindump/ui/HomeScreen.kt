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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
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
import io.github.nithv.braindump.data.MAX_CAPTURE_LENGTH
import io.github.nithv.braindump.greeting
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val c = BrainDumpTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            // Each message gets its own coroutine, so a waiting "Undo" never delays the next one.
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    HomeEvent.Saved -> snackbar.showSnackbar("Saved ✓")
                    is HomeEvent.SaveFailed -> snackbar.showSnackbar(event.reason)
                    is HomeEvent.Cleared -> {
                        val result = snackbar.showSnackbar("Cleared from inbox", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.restore(event.id)
                    }
                }
            }
        }
    }

    // Re-render relative times ("5m") every half minute.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
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
                        now = now,
                        onClear = { vm.clear(row.id) },
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
    }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
        if (count > 0) Text("Sorting comes next update", color = c.muted, fontSize = 13.sp)
    }
}

@Composable
private fun InboxRowCard(row: InboxRow, now: Long, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .chunky(fill = c.card, ink = c.ink, radius = 14.dp, shadow = 3.dp)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
                .padding(top = 13.dp, bottom = 13.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            relativeTime(row.createdAt, now),
            color = c.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 15.dp),
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
