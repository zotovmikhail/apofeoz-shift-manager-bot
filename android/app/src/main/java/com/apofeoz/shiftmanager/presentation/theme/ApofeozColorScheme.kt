package com.apofeoz.shiftmanager.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Палитра из HTML-мокапов `apofeoz_ui` (тёмная тема + золотой акцент). */
object ApofeozColors {
    val Background = Color(0xFF0D0D0D)
    val Surface = Color(0xFF171717)
    val SurfaceVariant = Color(0xFF27272A)
    val Primary = Color(0xFFD4AF37)
    val OnPrimary = Color(0xFF0D0D0D)
    val OnBackground = Color(0xFFF8FAFC)
    val OnSurface = Color(0xFFF8FAFC)
    val OnSurfaceVariant = Color(0xFFA1A1AA)
    val Outline = Color(0xFF27272A)
    val Error = Color(0xFFDC2626)
    val PrimaryMuted = Primary.copy(alpha = 0.12f)
    val PrimaryBorder = Primary.copy(alpha = 0.22f)
}

val ApofeozDarkColorScheme = darkColorScheme(
    primary = ApofeozColors.Primary,
    onPrimary = ApofeozColors.OnPrimary,
    primaryContainer = ApofeozColors.SurfaceVariant,
    onPrimaryContainer = ApofeozColors.Primary,
    secondary = ApofeozColors.SurfaceVariant,
    onSecondary = Color(0xFFE4E4E7),
    tertiary = ApofeozColors.SurfaceVariant,
    onTertiary = ApofeozColors.OnSurface,
    background = ApofeozColors.Background,
    onBackground = ApofeozColors.OnBackground,
    surface = ApofeozColors.Surface,
    onSurface = ApofeozColors.OnSurface,
    surfaceVariant = ApofeozColors.SurfaceVariant,
    onSurfaceVariant = ApofeozColors.OnSurfaceVariant,
    outline = ApofeozColors.Outline,
    outlineVariant = ApofeozColors.Outline,
    error = ApofeozColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
)
