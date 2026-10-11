package io.github.nithv.braindump.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.data.hasDates
import io.github.nithv.braindump.sort.DEFAULT_TIME
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

// ---------------------------------------------------------------------------------------------
// Piles: four tiles with open counts
// ---------------------------------------------------------------------------------------------

@Composable
fun PilesScreen(vm: PilesViewModel, padding: PaddingValues, onOpen: (Kind) -> Unit) {
    val c = BrainDumpTheme.colors
    val counts by vm.counts.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
    ) {
        Text("Piles", color = c.foreground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Where your sorted dumps live.",
            color = c.muted,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 20.dp),
        )
        Kind.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                pair.forEach { kind ->
                    PileTile(kind, counts?.get(kind), onClick = { onOpen(kind) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PileTile(kind: Kind, count: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = BrainDumpTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .chunky(fill = kind.soft(c), ink = c.ink, radius = 18.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 128.dp)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(kind.color(c), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(kind.look.pile, color = kind.color(c), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(top = 24.dp)) {
            Text(count?.toString() ?: "–", color = c.foreground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(kind.look.hint, color = c.muted, fontSize = 13.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One pile
// ---------------------------------------------------------------------------------------------

@Composable
fun PileScreen(vm: PileViewModel, snackbar: SnackbarHostState, padding: PaddingValues, onBack: () -> Unit) {
    val c = BrainDumpTheme.colors
    val kind = vm.kind
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var datingId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = remember { LocalDateTime.now() }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    PileEvent.Failed -> snackbar.showSnackbar("Something went wrong. Try again.")
                    is PileEvent.Archived -> {
                        val message = when {
                            event.ids.size > 1 -> "Cleared ${event.ids.size} done"
                            kind == Kind.WORRY -> "Let go 🍃"
                            else -> "Archived"
                        }
                        if (snackbar.showSnackbar(message, actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                            vm.unarchive(event.ids)
                        }
                    }
                    is PileEvent.Moved -> {
                        val result = snackbar.showSnackbar("Moved to ${event.to.look.pile}", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.unmove(event.id, event.dueAtMs)
                    }
                }
            }
        }
    }

    val open = state.open
    val done = state.done
    val checkable = kind.hasDates

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 4.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    "‹ Piles",
                    color = c.muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onBack)
                        .heightIn(min = 44.dp)
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(kind.color(c), CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(kind.look.pile, color = c.foreground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        when {
            open == null -> Unit
            open.isEmpty() && done.isEmpty() -> item(key = "empty") { EmptyPile(kind.look.empty) }
            else -> items(open, key = { it.id }) { row ->
                PileRowCard(
                    row = row,
                    kind = kind,
                    now = now,
                    checkable = checkable,
                    expanded = openId == row.id,
                    onToggleMenu = { openId = if (openId == row.id) null else row.id },
                    onToggleDone = { vm.toggleDone(row) },
                    onMove = { to -> openId = null; vm.move(row, to) },
                    onPickDate = { datingId = row.id },
                    onClearDate = { vm.setDue(row, null) },
                    onArchive = { openId = null; vm.archive(listOf(row.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-heading") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
                    Text(
                        "DONE TODAY · ${done.size}",
                        color = c.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Clear done",
                        color = c.blue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { vm.archive(done.map { it.id }) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
            items(done, key = { it.id }) { row ->
                PileRowCard(
                    row = row,
                    kind = kind,
                    now = now,
                    checkable = true,
                    expanded = false,
                    onToggleDone = { vm.toggleDone(row) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    val dating = open?.firstOrNull { it.id == datingId }
    if (dating != null) {
        DueDialog(
            initial = dating.due,
            onDismiss = { datingId = null },
            onPicked = { due ->
                datingId = null
                vm.setDue(dating, due)
            },
        )
    }
}

@Composable
private fun PileRowCard(
    row: PileRow,
    kind: Kind,
    now: LocalDateTime,
    checkable: Boolean,
    expanded: Boolean,
    onToggleDone: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleMenu: (() -> Unit)? = null,
    onMove: ((Kind) -> Unit)? = null,
    onPickDate: (() -> Unit)? = null,
    onClearDate: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
) {
    val c = BrainDumpTheme.colors
    val color = kind.color(c)
    val overdue = row.due != null && !row.done && row.due.isBefore(now)
    Column(
        modifier = if (row.done) {
            modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(14.dp))
                .border(2.dp, c.hairline, RoundedCornerShape(14.dp))
        } else {
            modifier
                .fillMaxWidth()
                .chunky(fill = c.card, ink = c.ink, radius = 14.dp, shadow = 3.dp)
        },
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 4.dp)) {
            if (checkable) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(role = Role.Checkbox, onClick = onToggleDone)
                        .semantics {
                            contentDescription = (if (row.done) "Mark not done: " else "Mark done: ") + row.title.take(40)
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .background(if (row.done) color else c.card, CircleShape)
                            .border(2.dp, if (row.done) color else c.foreground, CircleShape),
                    ) {
                        if (row.done) Text("✓", color = c.card, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(color, CircleShape),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 13.dp, bottom = 12.dp),
            ) {
                Text(
                    row.title,
                    color = if (row.done) c.muted else c.foreground,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textDecoration = if (row.done) TextDecoration.LineThrough else null,
                )
                if (row.due != null) {
                    Text(
                        "📅 ${formatDue(row.due, now)}",
                        color = if (overdue) c.red else color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(if (overdue) c.redSoft else kind.soft(c), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            if (onToggleMenu != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onToggleMenu)
                        .semantics { contentDescription = "More actions" },
                ) {
                    Text("⋯", color = c.muted, fontSize = 22.sp)
                }
            }
        }

        if (expanded && onMove != null && onArchive != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            ) {
                Column {
                    Label("MOVE TO")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Kind.entries.filter { it != kind }.forEach { to ->
                            OutlinePill(to.look.label, to.color(c), Modifier.weight(1f)) { onMove(to) }
                        }
                    }
                }
                if (kind.hasDates && onPickDate != null && onClearDate != null) {
                    Column {
                        Label("DATE")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinePill(if (row.due == null) "📅 Set date" else "📅 Change", c.foreground, Modifier.weight(2f), onPickDate)
                            if (row.due != null) OutlinePill("Clear", c.muted, Modifier.weight(1f), onClearDate)
                        }
                    }
                }
                OutlinePill(if (kind == Kind.WORRY) "Let it go 🍃" else "Archive", c.muted, onClick = onArchive)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = BrainDumpTheme.colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun OutlinePill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .border(2.dp, color.copy(alpha = 0.7f), CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 42.dp)
            .padding(horizontal = 14.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EmptyPile(message: String) {
    val c = BrainDumpTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(2.dp, c.hairline, RoundedCornerShape(16.dp))
            .padding(vertical = 36.dp, horizontal = 16.dp),
    ) {
        Text(message, color = c.muted, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/**
 * Pick a day, then (optionally) a time. "No time" keeps the 9:00 default, which the app shows as
 * just the day. The date picker works in UTC midnights, so we convert at the edges only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDialog(initial: LocalDateTime?, onDismiss: () -> Unit, onPicked: (LocalDateTime) -> Unit) {
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val pickedDay = day
    if (pickedDay == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = (initial?.toLocalDate() ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val start = initial?.toLocalTime()?.takeIf { it != DEFAULT_TIME } ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("What time?") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = { onPicked(pickedDay.atTime(timeState.hour, timeState.minute)) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { onPicked(pickedDay.atTime(DEFAULT_TIME)) }) { Text("No time") }
            },
        )
    }
}
