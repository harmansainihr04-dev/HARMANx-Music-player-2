package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuraDarkColorScheme = darkColorScheme(
    primary = CyberNeonCyan,
    onPrimary = CyberDeepBackground,
    primaryContainer = CyberSurfaceVariant,
    onPrimaryContainer = CyberNeonCyan,
    secondary = CyberElectricPurple,
    onSecondary = CyberDeepBackground,
    secondaryContainer = CyberSurfaceVariant,
    onSecondaryContainer = CyberElectricPurple,
    tertiary = CyberAmberGold,
    onTertiary = CyberDeepBackground,
    background = CyberDeepBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurfaceDark,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary,
    outline = CyberCardBorder
)

@Composable
fun AuraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        typography = Typography,
        content = content
    )
}

