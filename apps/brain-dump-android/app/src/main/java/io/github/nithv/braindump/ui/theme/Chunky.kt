package io.github.nithv.braindump.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The website's "chunky" look: a thick ink outline with a hard offset shadow (no blur). */
fun Modifier.chunky(
    fill: Color,
    ink: Color,
    radius: Dp = 16.dp,
    shadow: Dp = 4.dp,
): Modifier = this
    .drawBehind {
        val r = radius.toPx()
        val o = shadow.toPx()
        drawRoundRect(color = ink, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(r, r))
    }
    .background(fill, RoundedCornerShape(radius))
    .border(2.dp, ink, RoundedCornerShape(radius))
