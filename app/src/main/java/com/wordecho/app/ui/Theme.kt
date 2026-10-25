package com.wordecho.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentBlue = Color(0xFF2F6BFF)
val SuccessGreen = Color(0xFF12A150)
val ErrorRed = Color(0xFFE5484D)
val WarnYellow = Color(0xFFFFC53D)
val WarnYellowSoft = Color(0xFFFFF6DE)

private val Light = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF00A389),
    onSecondary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF16181D),
    surface = Color.White,
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF5A6270),
    outline = Color(0xFFD6DAE2)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FA6FF),
    onPrimary = Color(0xFF06101F),
    secondary = Color(0xFF4FD1B5),
    onSecondary = Color(0xFF062018),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE9ECF1),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE9ECF1),
    surfaceVariant = Color(0xFF242A32),
    onSurfaceVariant = Color(0xFFA6AEBC),
    outline = Color(0xFF333A45)
)

@Composable
fun WordEchoTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
