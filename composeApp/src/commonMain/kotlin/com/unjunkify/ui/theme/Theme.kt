package com.unjunkify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { System, Light, Dark }

private val LightColorScheme = lightColorScheme(
    primary = UnjunkifyPrimary,
    onPrimary = UnjunkifyOnPrimary,
    primaryContainer = UnjunkifyPrimaryContainer,
    onPrimaryContainer = UnjunkifyOnPrimaryContainer,
    secondary = UnjunkifySecondary,
    onSecondary = UnjunkifyOnSecondary,
    secondaryContainer = UnjunkifySecondaryContainer,
    onSecondaryContainer = UnjunkifyOnSecondaryContainer,
    tertiary = UnjunkifyTertiary,
    onTertiary = UnjunkifyOnTertiary,
    tertiaryContainer = UnjunkifyTertiaryContainer,
    onTertiaryContainer = UnjunkifyOnTertiaryContainer,
    error = UnjunkifyError,
    onError = UnjunkifyOnError,
    errorContainer = UnjunkifyErrorContainer,
    onErrorContainer = UnjunkifyOnErrorContainer,
    background = UnjunkifyBackground,
    onBackground = UnjunkifyOnBackground,
    surface = UnjunkifySurface,
    onSurface = UnjunkifyOnSurface,
    surfaceVariant = UnjunkifySurfaceVariant,
    onSurfaceVariant = UnjunkifyOnSurfaceVariant,
    outline = UnjunkifyOutline,
    outlineVariant = UnjunkifyOutlineVariantLight,
    scrim = UnjunkifyScrim,
    inverseSurface = UnjunkifyInverseSurfaceLight,
    inverseOnSurface = UnjunkifyInverseOnSurfaceLight,
    inversePrimary = UnjunkifyInversePrimaryLight,
    surfaceDim = UnjunkifySurfaceDimLight,
    surfaceBright = UnjunkifySurfaceBrightLight,
    surfaceContainerLowest = UnjunkifySurfaceContainerLowestLight,
    surfaceContainerLow = UnjunkifySurfaceContainerLowLight,
    surfaceContainer = UnjunkifySurfaceContainerLight,
    surfaceContainerHigh = UnjunkifySurfaceContainerHighLight,
    surfaceContainerHighest = UnjunkifySurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = UnjunkifyPrimaryContainer,
    onPrimary = UnjunkifyOnPrimaryContainer,
    primaryContainer = UnjunkifyPrimary,
    onPrimaryContainer = UnjunkifyOnPrimary,
    secondary = UnjunkifySecondaryContainer,
    onSecondary = UnjunkifyOnSecondaryContainer,
    secondaryContainer = UnjunkifySecondary,
    onSecondaryContainer = UnjunkifyOnSecondary,
    tertiary = UnjunkifyTertiaryContainer,
    onTertiary = UnjunkifyOnTertiaryContainer,
    tertiaryContainer = UnjunkifyTertiary,
    onTertiaryContainer = UnjunkifyOnTertiary,
    error = UnjunkifyErrorContainer,
    onError = UnjunkifyOnErrorContainer,
    errorContainer = UnjunkifyError,
    onErrorContainer = UnjunkifyOnError,
    background = UnjunkifyOnBackground,
    onBackground = UnjunkifyBackground,
    surface = UnjunkifyOnSurface,
    onSurface = UnjunkifyBackground,
    surfaceVariant = UnjunkifyOnSurfaceVariant,
    onSurfaceVariant = UnjunkifySurfaceVariant,
    outline = UnjunkifyOutline,
    outlineVariant = UnjunkifyOutlineVariantDark,
    scrim = UnjunkifyScrim,
    inverseSurface = UnjunkifyInverseSurfaceDark,
    inverseOnSurface = UnjunkifyInverseOnSurfaceDark,
    inversePrimary = UnjunkifyInversePrimaryDark,
    surfaceDim = UnjunkifySurfaceDimDark,
    surfaceBright = UnjunkifySurfaceBrightDark,
    surfaceContainerLowest = UnjunkifySurfaceContainerLowestDark,
    surfaceContainerLow = UnjunkifySurfaceContainerLowDark,
    surfaceContainer = UnjunkifySurfaceContainerDark,
    surfaceContainerHigh = UnjunkifySurfaceContainerHighDark,
    surfaceContainerHighest = UnjunkifySurfaceContainerHighestDark,
)

@Composable
expect fun getDynamicColorScheme(darkTheme: Boolean): ColorScheme?

@Composable
expect fun PlatformSystemBarsEffect(darkTheme: Boolean)

val LocalThemeIsDark = staticCompositionLocalOf { false }

@Composable
fun UnjunkifyTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colorScheme = if (dynamicColor) {
        getDynamicColorScheme(darkTheme) ?: if (darkTheme) DarkColorScheme else LightColorScheme
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    PlatformSystemBarsEffect(darkTheme = darkTheme)

    CompositionLocalProvider(LocalThemeIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UnjunkifyTypography,
            content = content
        )
    }
}

fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.System -> ThemeMode.Light
    ThemeMode.Light -> ThemeMode.Dark
    ThemeMode.Dark -> ThemeMode.System
}
