package dev.vimivo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.vimivo.settings.ThemeMode

@Composable
fun VimivoTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val scheme = when {
        highContrast && isDark -> VimivoHighContrastDarkColorScheme
        highContrast -> VimivoHighContrastLightColorScheme
        mode == ThemeMode.AMOLED -> VimivoAmoledColorScheme
        isDark -> VimivoDarkColorScheme
        else -> VimivoLightColorScheme
    }

    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalReducedMotion provides reduceMotion,
        LocalReducedTransparency provides (reduceTransparency || highContrast),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VimivoTypography,
            content = content,
        )
    }
}
