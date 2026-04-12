package com.apofeoz.shiftmanager.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.apofeoz.shiftmanager.R

/**
 * Variable TTF из [Google Fonts](https://github.com/google/fonts) (OFL):
 * `res/font/inter_variable.ttf` — основной текст, `space_grotesk_variable.ttf` — заголовки (как в мокапах docs/ui-mockups).
 */
val ApofeozFontInter: FontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_variable, FontWeight.Bold, FontStyle.Normal),
)

val ApofeozFontSpaceGrotesk: FontFamily = FontFamily(
    Font(R.font.space_grotesk_variable, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.space_grotesk_variable, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.space_grotesk_variable, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.space_grotesk_variable, FontWeight.Bold, FontStyle.Normal),
)
