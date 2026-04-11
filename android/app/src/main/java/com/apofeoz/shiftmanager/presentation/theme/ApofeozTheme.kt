package com.apofeoz.shiftmanager.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ApofeozTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ApofeozDarkColorScheme,
        typography = ApofeozTypography,
        shapes = ApofeozShapes,
        content = content,
    )
}
