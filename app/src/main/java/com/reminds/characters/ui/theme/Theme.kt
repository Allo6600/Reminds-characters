package com.reminds.characters.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF8A50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF5D3A00),
    secondary = Color(0xFF7CB342),
    surface = Color(0xFFFFFBF5),
    background = Color(0xFFFFF6E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF6D4C41),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFFAED581),
)

@Composable
fun RemindsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
