@file:Suppress("ArgumentListWrapping", "CyclomaticComplexMethod", "ImportOrdering", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.vimivo.settings.AccentColor
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.ThemeMode
import dev.vimivo.ui.theme.VimivoAmoledColorScheme
import dev.vimivo.ui.theme.VimivoColors
import dev.vimivo.ui.theme.VimivoDarkColorScheme
import dev.vimivo.ui.theme.VimivoLightColorScheme
import dev.vimivo.ui.theme.VimivoTypography
import dev.vimivo.ui.theme.LocalReducedTransparency
import dev.vimivo.ui.theme.LocalReducedMotion as SharedLocalReducedMotion

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalLargeTouchTargets = staticCompositionLocalOf { false }

@Composable
fun VimivoAndroidTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = when {
        settings.themeMode == ThemeMode.AMOLED -> VimivoAmoledColorScheme
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> VimivoDarkColorScheme
        else -> VimivoLightColorScheme
    }
    val accent = when (settings.accentColor) {
        AccentColor.AURORA -> VimivoColors.AuroraTeal
        AccentColor.VIOLET -> Color(0xFF6C5CE7)
        AccentColor.BLUE -> Color(0xFF1769E0)
        AccentColor.GREEN -> Color(0xFF087F5B)
        AccentColor.ORANGE -> Color(0xFFC45100)
        AccentColor.PINK -> Color(0xFFB52B67)
    }
    val personalized = if (!settings.dynamicColor && settings.themeMode != ThemeMode.AMOLED) {
        scheme.copy(primary = accent)
    } else {
        scheme
    }
    val contrasted = if (settings.highContrast) {
        personalized.copy(
            onBackground = if (dark) Color.White else Color.Black,
            onSurface = if (dark) Color.White else Color.Black,
        )
    } else {
        personalized
    }
    val reducedMotion = settings.reduceAnimations || !ValueAnimator.areAnimatorsEnabled()
    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        // Shared components such as VimivoTabBar read the common-code motion preference.
        SharedLocalReducedMotion provides reducedMotion,
        LocalReducedTransparency provides (settings.reduceTransparency || settings.highContrast),
        LocalLargeTouchTargets provides settings.largerTouchTargets,
    ) {
        MaterialTheme(colorScheme = contrasted, typography = VimivoTypography, content = content)
    }
}
