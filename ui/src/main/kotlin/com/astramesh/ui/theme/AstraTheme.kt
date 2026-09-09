package com.astramesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AstraBackground = Color(0xFF0D0E12)
val AstraSurface = Color(0xFF171922)
val AstraSurfaceVariant = Color(0xFF222533)
val AstraOutline = Color(0xFF2E3346)

val AstraEmerald = Color(0xFF00E676)
val AstraCyan = Color(0xFF00E5FF)
val AstraCrimson = Color(0xFFFF1744)
val AstraAmber = Color(0xFFFFD600)

val AstraTextPrimary = Color(0xFFF0F2F8)
val AstraTextSecondary = Color(0xFF9BA3BF)

private val DarkColorScheme = darkColorScheme(
    primary = AstraCyan,
    onPrimary = Color.Black,
    secondary = AstraEmerald,
    onSecondary = Color.Black,
    tertiary = AstraAmber,
    background = AstraBackground,
    onBackground = AstraTextPrimary,
    surface = AstraSurface,
    onSurface = AstraTextPrimary,
    surfaceVariant = AstraSurfaceVariant,
    onSurfaceVariant = AstraTextSecondary,
    outline = AstraOutline,
    error = AstraCrimson,
    onError = Color.White
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
