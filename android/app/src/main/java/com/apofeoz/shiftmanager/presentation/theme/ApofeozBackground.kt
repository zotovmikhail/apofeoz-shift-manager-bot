package com.apofeoz.shiftmanager.presentation.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Лёгкая сетка на фоне, как в `login.html` мокапа. */
fun Modifier.apofeozGridBackground(
    lineColor: Color = Color.White.copy(alpha = 0.03f),
    step: Dp = 32.dp,
): Modifier = drawBehind {
    val px = step.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += px
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += px
    }
}
