package com.flowpilot.automation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF1A237E)
private val IndigoLight = Color(0xFF534BAE)
private val Teal = Color(0xFF80CBC4)
private val Bg = Color(0xFF0D1333)
private val Surface = Color(0xFF1A2148)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color.Black,
    secondary = IndigoLight,
    background = Bg,
    surface = Surface,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFEF9A9A)
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Teal,
    background = Color(0xFFF5F5FA),
    surface = Color.White
)

@Composable
fun FlowPilotTheme(
    dark: Boolean = isSystemInDarkTheme() || true, // prefer dark; still Material 3
    content: @Composable () -> Unit
) {
    // Always offer a polished dark Material 3 look; light if caller forces it
    val useDark = dark
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
