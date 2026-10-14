package com.ramanan.fit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF0E1513)
    val Surface = Color(0xFF17221F)
    val SurfaceHi = Color(0xFF213029)
    val Text = Color(0xFFF2F7F4)
    val TextDim = Color(0xFFB4C4BD)
    val Exercise = Color(0xFF3DDC84)      // green = work
    val ExerciseDeep = Color(0xFF0F3B25)
    val Rest = Color(0xFF5AA9FF)          // blue = rest
    val RestDeep = Color(0xFF0E2A4A)
    val Warn = Color(0xFFFFC56B)
    val Danger = Color(0xFFFF7A6E)
}

@Composable
fun RamananFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Exercise,
            onPrimary = Color(0xFF00210F),
            secondary = Palette.Rest,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.TextDim,
            error = Palette.Danger,
        ),
        content = content,
    )
}
