package dev.openpolaris.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Night-mode palette: near-black background, red primary — preserves dark
 * adaptation at the eyepiece. Muted amber/green accents for secondary info.
 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF3B30),          // signal red — main actions/highlights
    onPrimary = Color(0xFF1A0000),
    secondary = Color(0xFFFFB74D),        // muted amber — secondary info
    onSecondary = Color(0xFF1A1200),
    tertiary = Color(0xFF81C784),         // soft green — "good" states
    background = Color(0xFF000000),       // true black
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF5C1410),
)

/** Always-night theme wrapper for the whole app. */
@Composable
fun OpenPolarisTheme(content: @Composable () -> Unit) {
    // Night mode is mandatory by design; isSystemInDarkTheme() kept for future
    // optional day theme. Suppress unused warning via explicit reference.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = NightColors, content = content)
}
