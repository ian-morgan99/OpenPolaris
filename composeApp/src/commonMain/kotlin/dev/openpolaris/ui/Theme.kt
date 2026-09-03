package dev.openpolaris.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/**
 * Readable-on-small-screens typography. v0.1.7 only used Material3 defaults
 * (bodyMedium=14sp, bodySmall=12sp) which became illegible on a 304 dpi
 * Pixel 5 in landscape with 1.0 font scale. This bumps the base sizes
 * slightly while keeping the dark-mode visual weight. Labels in particular
 * are bumped to 13sp so the rail reads as text, not a glyph cluster.
 * Fixes the "text not readable" complaint from issues #45 and #46.
 */
private val NightTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

/** Always-night theme wrapper for the whole app. */
@Composable
fun OpenPolarisTheme(content: @Composable () -> Unit) {
    // Night mode is mandatory by design; isSystemInDarkTheme() kept for future
    // optional day theme. Suppress unused warning via explicit reference.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = NightColors, typography = NightTypography, content = content)
}
