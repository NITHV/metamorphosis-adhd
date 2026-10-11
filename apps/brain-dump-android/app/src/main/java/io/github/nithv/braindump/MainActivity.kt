package io.github.nithv.braindump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.nithv.braindump.ui.AppRoot
import io.github.nithv.braindump.ui.theme.BrainDumpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrainDumpTheme {
                AppRoot()
            }
        }
    }
}
