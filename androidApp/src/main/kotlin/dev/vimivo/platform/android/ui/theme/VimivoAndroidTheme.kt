package dev.vimivo.platform.android.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.vimivo.settings.ThemeMode

val LocalReducedMotion = staticCompositionLocalOf { false }

private val Accent = Color(0xFF6C5CE7)
private val LightScheme = lightColorScheme(
    primary = Accent,
    secondary = Color(0xFF536471),
    tertiary = Color(0xFF9A456A),
    surface = Color(0xFFFFFBFF),
    surfaceContainer = Color(0xFFF4F0F5),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9C2FF),
    secondary = Color(0xFFBBC7D1),
    tertiary = Color(0xFFFFB1C8),
    background = Color(0xFF111014),
    surface = Color(0xFF111014),
    surfaceContainer = Color(0xFF1E1D22),
)
private val AmoledScheme = darkColorScheme(
    primary = Color(0xFFC9C2FF),
    secondary = Color(0xFFC5C2CA),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF101014),
    surfaceContainerHigh = Color(0xFF18181E),
)

private val VimivoTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun VimivoAndroidTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (mode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = when {
        mode == ThemeMode.AMOLED -> AmoledScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalReducedMotion provides !ValueAnimator.areAnimatorsEnabled()) {
        MaterialTheme(colorScheme = scheme, typography = VimivoTypography, content = content)
    }
}
