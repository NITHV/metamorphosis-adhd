package io.github.nithv.braindump.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nithv.braindump.BrainDumpApp
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import kotlinx.coroutines.launch

/**
 * Four screens: Home (dump + Inbox), Paused, Piles (four tiles) and one pile. Kept as a tiny hand-rolled
 * navigator instead of a navigation library: fewer moving parts, and the whole map fits on a screen.
 * The current screen is saved state ("cheap to lose" screen state, see design doc §5 A2).
 */
@Composable
fun AppRoot() {
    val c = BrainDumpTheme.colors
    val app = LocalContext.current.applicationContext as BrainDumpApp
    var route by rememberSaveable { mutableStateOf("home") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Activity-wide, so Home's "Where you left off" card and the Paused screen share one, and the
    // Pause sheet can open from either.
    val paused: PausedViewModel = viewModel { PausedViewModel(app.repository, app.transcriptions.pending) }
    val pauseVm: PauseViewModel = viewModel(factory = PauseViewModel.Factory)

    LaunchedEffect(pauseVm) {
        pauseVm.events.collect {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("Paused ⏸ Your place is saved.")
            }
        }
    }
    LaunchedEffect(paused) {
        paused.events.collect { event ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                when (event) {
                    is PausedEvent.Resumed ->
                        if (snackbar.showSnackbar("Welcome back 👋", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                            paused.unresume(event.id)
                        }
                    is PausedEvent.LetGo ->
                        if (snackbar.showSnackbar("Let go 🍃", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                            paused.undoLetGo(event.id)
                        }
                    PausedEvent.Kept -> snackbar.showSnackbar("Kept. We'll ask again in a week.")
                    PausedEvent.Failed -> snackbar.showSnackbar("Something went wrong. Try again.")
                }
            }
        }
    }

    val pile = route.removePrefix("pile:").takeIf { route.startsWith("pile:") }?.let(Kind::valueOf)
    BackHandler(enabled = route != "home") { route = if (pile != null) "piles" else "home" }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = c.card) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = c.foreground,
                    unselectedTextColor = c.muted,
                    indicatorColor = c.brandSoft,
                )
                NavigationBarItem(
                    selected = route == "home",
                    onClick = { route = "home" },
                    icon = { Text("📥", fontSize = 20.sp) },
                    label = { Text("Dump") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = route == "paused",
                    onClick = { route = "paused" },
                    icon = { Text("⏸️", fontSize = 20.sp) },
                    label = { Text("Paused") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = route == "piles" || pile != null,
                    onClick = { route = "piles" },
                    icon = { Text("🗂️", fontSize = 20.sp) },
                    label = { Text("Piles") },
                    colors = colors,
                )
            }
        },
    ) { padding: PaddingValues ->
        Box {
            when {
                pile != null -> PileScreen(
                    vm = viewModel(key = "pile-${pile.name}") { PileViewModel(app.repository, pile) },
                    snackbar = snackbar,
                    padding = padding,
                    onBack = { route = "piles" },
                )
                route == "piles" -> PilesScreen(
                    vm = viewModel { PilesViewModel(app.repository) },
                    padding = padding,
                    onOpen = { route = "pile:${it.name}" },
                )
                route == "paused" -> PausedScreen(vm = paused, padding = padding, onPause = pauseVm::open)
                else -> HomeScreen(
                    snackbar = snackbar,
                    padding = padding,
                    paused = paused,
                    onPause = pauseVm::open,
                    onSeeAllPaused = { route = "paused" },
                )
            }
        }
    }
    PauseSheet(pauseVm)
}
