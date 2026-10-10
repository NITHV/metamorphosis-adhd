package io.github.nithv.braindump.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nithv.braindump.BuildConfig
import io.github.nithv.braindump.R
import io.github.nithv.braindump.greeting
import io.github.nithv.braindump.ui.theme.BrainDumpTheme
import io.github.nithv.braindump.ui.theme.chunky
import java.time.LocalTime

/** Milestone N0 ("walking skeleton"): the Home screen shell. The Dump box and Inbox arrive in N1. */
@Composable
fun HomeScreen() {
    val c = BrainDumpTheme.colors
    val hello = remember { greeting(LocalTime.now().hour) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Green welcome card, like the website's hero.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .chunky(fill = c.brand, ink = c.ink, radius = 20.dp)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$hello 👋", color = c.brandForeground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Catch every thought. Sort it later.", color = c.brandForeground, fontSize = 15.sp)
            }
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "Brain Dump mascot",
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, c.ink, RoundedCornerShape(18.dp)),
            )
        }

        // Placeholder for the Dump box (milestone N1).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .chunky(fill = c.card, ink = c.ink)
                .padding(16.dp),
        ) {
            Text("What's on your mind?", color = c.muted, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text("The Dump box arrives in the next update.", color = c.muted, fontSize = 13.sp)
        }

        Text(
            "Brain Dump ${BuildConfig.VERSION_NAME} · works offline · no internet permission",
            color = c.muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun HomePreview() {
    BrainDumpTheme { HomeScreen() }
}
