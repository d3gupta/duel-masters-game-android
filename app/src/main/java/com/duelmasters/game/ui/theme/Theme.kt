package com.duelmasters.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7CB342),
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF33691E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF33691E),
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF7CB342)
)

@Composable
fun DuelMastersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
