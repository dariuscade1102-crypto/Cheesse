package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Matte Dark Slate Studio Palette
val SlateBackground = Color(0xFF0E1318)
val SlateSurface = Color(0xFF161E26)
val SlateSurfaceVariant = Color(0xFF1F2A35)
val SlateBorder = Color(0xFF2B3947)

val TournamentEmerald = Color(0xFF22C55E)
val TournamentEmeraldDark = Color(0xFF15803D)
val TacticalCyan = Color(0xFF38BDF8)
val AmberHighlight = Color(0xFFFACC15)

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Move Strength & Classification Colors
val MoveBestColor = Color(0xFF22C55E)       // Emerald
val MoveGoodColor = Color(0xFF84CC16)       // Lime
val MoveBookColor = Color(0xFF38BDF8)       // Cyan
val MoveInaccuracyColor = Color(0xFFFACC15) // Amber
val MoveMistakeColor = Color(0xFFFB923C)    // Orange
val MoveBlunderColor = Color(0xFFEF4444)    // Red

// Espresso / Mahogany Wood-Grain Studio Palette & Gauge Accents
val EspressoBackgroundTop = Color(0xFF1A0F0A)
val EspressoBackgroundBottom = Color(0xFF2B1810)
val EvalGaugeBarBackground = Color(0xFF4B4E58)
val ActionRoyalBlue = Color(0xFF3B82F6)
val BestMoveIndigoArrow = Color(0xFF3B5BDB)
val LastMoveSlateBlue = Color(0xFF7C8BA1)
val MovePillBrightGreen = Color(0xFF65A30D)
val MovePillOrange = Color(0xFFD97706)

// Customizable Board Theme Palettes
enum class BoardThemeOption(
    val label: String,
    val lightSquare: Color,
    val darkSquare: Color,
    val coordColor: Color
) {
    CLASSIC_WALNUT(
        label = "Classic Walnut",
        lightSquare = Color(0xFFE8D0AA),
        darkSquare = Color(0xFFB58863),
        coordColor = Color(0xFF5C3D24)
    ),
    TOURNAMENT_EMERALD(
        label = "Tournament Emerald",
        lightSquare = Color(0xFFE5ECE6),
        darkSquare = Color(0xFF4A7C59),
        coordColor = Color(0xFF1E3A28)
    ),
    OCEAN_SLATE(
        label = "Ocean Slate",
        lightSquare = Color(0xFFDEE3E6),
        darkSquare = Color(0xFF5D768B),
        coordColor = Color(0xFF263645)
    ),
    ICE_MONOCHROME(
        label = "Ice Monochrome",
        lightSquare = Color(0xFFE2E8F0),
        darkSquare = Color(0xFF475569),
        coordColor = Color(0xFF1E293B)
    );

    companion object {
        fun fromLabel(label: String): BoardThemeOption =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: CLASSIC_WALNUT
    }
}
