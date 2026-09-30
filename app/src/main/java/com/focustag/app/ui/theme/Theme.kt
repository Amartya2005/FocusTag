package com.focustag.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClassroomColors = lightColorScheme(
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

@Composable
fun FocusTagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClassroomColors,
        typography = Typography,
        content = content
    )
}
