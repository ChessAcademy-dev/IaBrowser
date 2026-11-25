package com.aegis.browser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Utilitarian, high-contrast obsidian & phosphor-emerald dark palette.
// Zero purple/blue gradients, zero glassmorphism, crisp 1dp structural hairlines.
val ObsidianBg = Color(0xFF0B0D0E)
val SurfaceDark = Color(0xFF131618)
val SurfaceElevated = Color(0xFF1B1F23)
val BorderHairline = Color(0xFF2A3036)
val TextPrimary = Color(0xFFF2F4F5)
val TextSecondary = Color(0xFF949DA6)
val ShieldActiveEmerald = Color(0xFF22C55E)
val ShieldWarningAmber = Color(0xFFF59E0B)
val DangerCrimson = Color(0xFFEF4444)

private val AegisDarkColors = darkColorScheme(
    primary = ShieldActiveEmerald,
    onPrimary = Color(0xFF051B0D),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    error = DangerCrimson
)

@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AegisDarkColors,
        content = content
    )
}
