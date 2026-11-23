package com.lifeticker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Up = Color(0xFF00C805)
    val Down = Color(0xFFFF5000)
    val Flat = Color(0xFF8A9199)
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF111417)
    val SurfaceHigh = Color(0xFF1B1F24)
    val Divider = Color(0xFF23272D)
    val TextDim = Color(0xFF8A9199)
}

fun trendColor(change: Float?): Color = when {
    change == null -> Palette.Flat
    change > 0f -> Palette.Up
    change < 0f -> Palette.Down
    else -> Palette.Flat
}

private val scheme = darkColorScheme(
    primary = Palette.Up,
    onPrimary = Color.Black,
    secondary = Palette.Up,
    onSecondary = Color.Black,
    background = Palette.Bg,
    onBackground = Color.White,
    surface = Palette.Bg,
    onSurface = Color.White,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
    error = Palette.Down,
)

@Composable
fun LifeTickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
