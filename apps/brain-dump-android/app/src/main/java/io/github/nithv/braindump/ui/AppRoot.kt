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
import androidx.compose.runtime.Composable
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

/**
 * Three screens: Home (dump + Inbox), Piles (four tiles) and one pile. Kept as a tiny hand-rolled
 * navigator instead of a navigation library: fewer moving parts, and the whole map fits on a screen.
 * The current screen is saved state ("cheap to lose" screen state, see design doc §5 A2).
 */
@Composable
fun AppRoot() {
    val c = BrainDumpTheme.colors
    val app = LocalContext.current.applicationContext as BrainDumpApp
    var route by rememberSaveable { mutableStateOf("home") }
    val snackbar = remember { SnackbarHostState() }

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
                    selected = route != "home",
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
                else -> HomeScreen(snackbar = snackbar, padding = padding)
            }
        }
    }
}
