package com.quickdrop.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.quickdrop.core.model.ThemePreference

object QuickDropThemeColors {
    val CosmicBackground = Color(0xFF090D16)
    val CosmicSurface = Color(0xFF101726)
    val CosmicSurfaceElevated = Color(0xFF192237)
    val CosmicBorder = Color(0x1FFFFFFF)

    val Emerald = Color(0xFF10B981)
    val EmeraldLight = Color(0xFF34D399)
    val EmeraldDark = Color(0xFF059669)

    val Violet = Color(0xFF8B5CF6)
    val VioletLight = Color(0xFFA78BFA)

    val Cyan = Color(0xFF06B6D4)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
}

private val CosmicDarkColorScheme = darkColorScheme(
    primary = QuickDropThemeColors.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = QuickDropThemeColors.EmeraldLight,
    secondary = QuickDropThemeColors.Violet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B1D7A),
    onSecondaryContainer = QuickDropThemeColors.VioletLight,
    tertiary = QuickDropThemeColors.Cyan,
    background = QuickDropThemeColors.CosmicBackground,
    onBackground = QuickDropThemeColors.TextPrimary,
    surface = QuickDropThemeColors.CosmicSurface,
    onSurface = QuickDropThemeColors.TextPrimary,
    surfaceVariant = QuickDropThemeColors.CosmicSurfaceElevated,
    onSurfaceVariant = QuickDropThemeColors.TextSecondary,
    outline = QuickDropThemeColors.CosmicBorder,
    error = QuickDropThemeColors.Rose,
    onError = Color.White
)

private val NordicLightColorScheme = lightColorScheme(
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

@Composable
fun QuickDropTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (useDarkTheme) CosmicDarkColorScheme else NordicLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
