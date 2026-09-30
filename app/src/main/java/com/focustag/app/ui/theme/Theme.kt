package com.focustag.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F4F1),
    onPrimaryContainer = Ink,
    secondary = Ember,
    onSecondary = Color.White,
    secondaryContainer = EmberSoft,
    onSecondaryContainer = Ink,
    tertiary = Ready,
    background = Sand,
    onBackground = Ink,
    surface = SandCard,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Slate,
    outline = Color(0xFFB7C0C6),
    error = Danger,
    errorContainer = Color(0xFFF8D7D3),
    onErrorContainer = Color(0xFF4A1010)
)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Ink,
    primaryContainer = InkRaised,
    onPrimaryContainer = TealBright,
    secondary = Ember,
    onSecondary = Ink,
    background = Ink,
    onBackground = Sand,
    surface = InkRaised,
    onSurface = Sand,
    surfaceVariant = Color(0xFF1C3644),
    onSurfaceVariant = Color(0xFFC5D0D6),
    outline = Color(0xFF6D808A),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5A1C1C),
    onErrorContainer = Color(0xFFFFE8E6)
)

@Composable
fun FocusTagTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
