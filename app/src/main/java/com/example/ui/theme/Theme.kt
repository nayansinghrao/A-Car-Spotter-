package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpotrDarkColorScheme = darkColorScheme(
    primary = SpotrGreen,
    onPrimary = NearBlack,
    primaryContainer = SpotrGreenContainer,
    onPrimaryContainer = SpotrOnGreenContainer,
    secondary = AccentCyan,
    onSecondary = NearBlack,
    secondaryContainer = CharcoalSurfaceVariant,
    onSecondaryContainer = TextWhite,
    tertiary = SpotrGreenDark,
    onTertiary = TextWhite,
    background = NearBlack,
    onBackground = TextWhite,
    surface = CharcoalCard,
    onSurface = TextWhite,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = CharcoalBorder,
    outlineVariant = CharcoalSurfaceVariant,
    error = DangerRed,
    onError = TextWhite,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFFFFB4AB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Always enforce premium SPOTR automotive dark theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SpotrDarkColorScheme,
        typography = Typography,
        content = content
    )
}
