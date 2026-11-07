package com.quickdrop.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object QuickDropColors {
    // Ultra-premium cosmic slate background & surfaces
    val CosmicBackground = Color(0xFF090D16)
    val CosmicSurface = Color(0xFF101726)
    val CosmicSurfaceElevated = Color(0xFF192237)
    val CosmicSurfaceHighlight = Color(0xFF222F4C)
    val CosmicBorder = Color(0x1FFFFFFF) // 12% white
    val CosmicBorderGlow = Color(0x3310B981)

    // Vibrant accent colors
    val Emerald = Color(0xFF10B981)
    val EmeraldLight = Color(0xFF34D399)
    val EmeraldDark = Color(0xFF059669)

    val Violet = Color(0xFF8B5CF6)
    val VioletLight = Color(0xFFA78BFA)
    val VioletDark = Color(0xFF6D28D9)

    val Cyan = Color(0xFF06B6D4)
    val CyanLight = Color(0xFF38BDF8)

    val Amber = Color(0xFFF59E0B)
    val AmberLight = Color(0xFFFBBF24)

    val Rose = Color(0xFFF43F5E)
    val RoseLight = Color(0xFFFB7185)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Gradients
    val EmeraldGradient = Brush.linearGradient(listOf(Emerald, Color(0xFF059669)))
    val VioletGradient = Brush.linearGradient(listOf(Violet, Color(0xFF6366F1)))
    val AmberGradient = Brush.linearGradient(listOf(Amber, Color(0xFFEF4444)))
    val CyanGradient = Brush.linearGradient(listOf(Cyan, Color(0xFF3B82F6)))
    val GlassGradient = Brush.linearGradient(
        listOf(
            Color(0x26FFFFFF),
            Color(0x0AFFFFFF)
        )
    )
    val HeroGradient = Brush.radialGradient(
        listOf(
            Color(0xFF1E2E4A),
            Color(0xFF101726),
            Color(0xFF090D16)
        )
    )
}

val CosmicDarkColorScheme: ColorScheme = darkColorScheme(
    primary = QuickDropColors.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = QuickDropColors.EmeraldLight,
    secondary = QuickDropColors.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B1D7A),
    onSecondaryContainer = QuickDropColors.VioletLight,
    tertiary = QuickDropColors.Cyan,
    background = QuickDropColors.CosmicBackground,
    onBackground = QuickDropColors.TextPrimary,
    surface = QuickDropColors.CosmicSurface,
    onSurface = QuickDropColors.TextPrimary,
    surfaceVariant = QuickDropColors.CosmicSurfaceElevated,
    onSurfaceVariant = QuickDropColors.TextSecondary,
    outline = QuickDropColors.CosmicBorder,
    error = QuickDropColors.Rose,
    onError = Color.White
)

val NordicLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    onError = Color.White
)

val OledBlackColorScheme: ColorScheme = darkColorScheme(
    primary = QuickDropColors.Emerald,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF064E3B),
    secondary = QuickDropColors.Cyan,
    onSecondary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0x33FFFFFF),
    error = QuickDropColors.Rose
)

fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    borderColor: Color = QuickDropColors.CosmicBorder,
    backgroundColor: Color = QuickDropColors.CosmicSurface
): Modifier = this
    .background(backgroundColor, RoundedCornerShape(cornerRadius))
    .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
