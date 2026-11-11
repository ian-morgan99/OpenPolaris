package dev.openpolaris.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
private val DimNightColors = darkColorScheme(
    primary = Color(0xFF9E1B17),
    onPrimary = Color(0xFF0D0000),
    secondary = Color(0xFF8A642D),
    onSecondary = Color(0xFF0D0800),
    tertiary = Color(0xFF5D805F),
    background = Color(0xFF000000),
    onBackground = Color(0xFF9A9A9A),
    surface = Color(0xFF080808),
    onSurface = Color(0xFF9A9A9A),
    surfaceVariant = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF7D7D7D),
    error = Color(0xFFB76560),
    outline = Color(0xFF3D100E),
)

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

/**
 * Phone typography keeps the same contrast and hierarchy as the desktop
 * theme, but removes the extra label/title growth that makes a narrow phone
 * feel like it is rendering a tablet UI. Symbols used by the jog pad and
 * compact navigation inherit these smaller label styles too.
 */
private val CompactNightTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** True while the shared UI is hosted in the compact phone shell. */
val LocalOpenPolarisCompact = staticCompositionLocalOf { false }

/** Shared spacing tokens used by primary panes on phones and larger screens. */
@Composable
fun openPolarisCardOuterPadding() = if (LocalOpenPolarisCompact.current) 2.dp else 8.dp

@Composable
fun openPolarisCardInnerPadding() = if (LocalOpenPolarisCompact.current) 6.dp else 16.dp

@Composable
fun openPolarisSectionSpacing() = if (LocalOpenPolarisCompact.current) 4.dp else 8.dp

/** Night theme wrapper; dim mode reduces red/amber luminance for field use. */
@Composable
fun OpenPolarisTheme(
    dim: Boolean = false,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Night mode is mandatory by design; isSystemInDarkTheme() kept for future
    // optional day theme. Suppress unused warning via explicit reference.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalOpenPolarisCompact provides compact,
        // The compact shell deliberately uses a 28 dp visual interaction footprint.
        // It is still larger than the visible 10 dp line graphics, but avoids
        // Material3's default 48 dp expansion making every phone row feel
        // like a tablet control. Desktop keeps the normal 48 dp target.
        LocalMinimumInteractiveComponentSize provides if (compact) 28.dp else 48.dp,
    ) {
        MaterialTheme(
            colorScheme = if (dim) DimNightColors else NightColors,
            typography = if (compact) CompactNightTypography else NightTypography,
            content = content,
        )
    }
}
