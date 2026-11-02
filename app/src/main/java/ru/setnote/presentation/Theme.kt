package ru.setnote.presentation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors =
    darkColorScheme(
        primary = Color(0xFF81B9FF),
        onPrimary = Color(0xFF082340),
        primaryContainer = Color(0xFF162C45),
        onPrimaryContainer = Color(0xFFD7E8FF),
        background = Color(0xFF080A0E),
        onBackground = Color(0xFFF1F3F7),
        surface = Color(0xFF10141B),
        onSurface = Color(0xFFF1F3F7),
        surfaceVariant = Color(0xFF1B2330),
        onSurfaceVariant = Color(0xFF9CAABD),
        outline = Color(0xFF354154),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun SetnoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
