package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CheesseDarkColorScheme = darkColorScheme(
    primary = TournamentEmerald,
    onPrimary = Color(0xFF042F16),
    primaryContainer = TournamentEmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = TacticalCyan,
    onSecondary = Color(0xFF062536),
    tertiary = AmberHighlight,
    background = SlateBackground,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = MoveBlunderColor
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CheesseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
