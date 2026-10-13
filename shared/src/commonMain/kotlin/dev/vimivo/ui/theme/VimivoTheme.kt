package dev.vimivo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.vimivo.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D56C7),
    secondary = Color(0xFF5C5D72),
    tertiary = Color(0xFF78536B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    secondary = Color(0xFFC5C4DD),
    tertiary = Color(0xFFE8B9D4),
)
private val AmoledColors = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF141414),
    primary = Color(0xFFBEC2FF),
)

@Composable
fun VimivoTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
