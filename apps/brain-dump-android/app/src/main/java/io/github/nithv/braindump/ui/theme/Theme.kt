package io.github.nithv.braindump.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brain Dump's palette, mirrored from the website's CSS tokens (apps/brain-dump/src/app/globals.css). */
@Immutable
data class BrainDumpColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val foreground: Color,
    val muted: Color,
    val ink: Color,
    val hairline: Color,
    val brand: Color,
    val brandForeground: Color,
    val brandSoft: Color,
    val blue: Color,
    val purple: Color,
    val red: Color,
    val orange: Color,
)

val LightColors = BrainDumpColors(
    background = Color(0xFFECE5D6),
    surface = Color(0xFFF7F2E8),
    card = Color(0xFFFFFDF7),
    foreground = Color(0xFF1C1B18),
    muted = Color(0xFF6E695E),
    ink = Color(0xFF1C1B18),
    hairline = Color(0xFFE3DCCD),
    brand = Color(0xFF2F9E66),
    brandForeground = Color(0xFF10140F),
    brandSoft = Color(0xFFD9F0E2),
    blue = Color(0xFF3D7FF0),
    purple = Color(0xFF7B61E8),
    red = Color(0xFFE0443E),
    orange = Color(0xFFF29A1F),
)

val DarkColors = BrainDumpColors(
    background = Color(0xFF0B0B0A),
    surface = Color(0xFF131312),
    card = Color(0xFF1A1A18),
    foreground = Color(0xFFF1EEE6),
    muted = Color(0xFFA39E92),
    ink = Color(0xFF3B3934),
    hairline = Color(0xFF2A2926),
    brand = Color(0xFF5AA877),
    brandForeground = Color(0xFF0D120E),
    brandSoft = Color(0xFF1D3326),
    blue = Color(0xFF5D95F5),
    purple = Color(0xFF9B86F2),
    red = Color(0xFFEF6560),
    orange = Color(0xFFF4AB45),
)

private val LocalColors = staticCompositionLocalOf { LightColors }

object BrainDumpTheme {
    val colors: BrainDumpColors
        @Composable get() = LocalColors.current
}

@Composable
fun BrainDumpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val material = if (dark) {
        darkColorScheme(
            primary = c.brand, onPrimary = c.brandForeground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.foreground,
        )
    } else {
        lightColorScheme(
            primary = c.brand, onPrimary = c.brandForeground,
            background = c.background, onBackground = c.foreground,
            surface = c.card, onSurface = c.foreground,
        )
    }
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
