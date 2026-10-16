package dev.vimivo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object VimivoSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 48.dp
}

object VimivoRadius {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val pill: Dp = 999.dp
}

object VimivoElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
}

object VimivoIconSize {
    val small: Dp = 16.dp
    val medium: Dp = 24.dp
    val large: Dp = 32.dp
    val hero: Dp = 48.dp
}

enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun fromWidth(widthDp: Float): WindowSizeClass = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }

        fun fromWidth(width: Dp): WindowSizeClass = fromWidth(width.value)
    }
}

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalHighContrast = staticCompositionLocalOf { false }
val LocalWindowSizeClass = staticCompositionLocalOf { WindowSizeClass.MEDIUM }
val LocalTouchTargetScale = staticCompositionLocalOf { 1.0f }

object VimivoColors {
    // Brand Accent
    val BrandViolet = Color(0xFF6C5CE7)
    val BrandVioletLight = Color(0xFFC9C2FF)

    // Semantic Status
    val Success = Color(0xFF087F5B)
    val SuccessContainer = Color(0xFFD3F9D8)
    val Warning = Color(0xFFC45100)
    val WarningContainer = Color(0xFFFFE8CC)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val Info = Color(0xFF1769E0)
    val InfoContainer = Color(0xFFD0E2FF)

    // Focus Outline Token (High-visibility for keyboard navigation)
    val FocusOutlineLight = Color(0xFF4D56C7)
    val FocusOutlineDark = Color(0xFFBEC2FF)
}

val VimivoLightColorScheme = lightColorScheme(
    primary = Color(0xFF4D56C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF030865),
    secondary = Color(0xFF536471),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3EC),
    onSecondaryContainer = Color(0xFF101C27),
    tertiary = Color(0xFF78536B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3F7),
    surfaceContainer = Color(0xFFEEEEF2),
    surfaceContainerHigh = Color(0xFFE8E7EC),
    surfaceContainerHighest = Color(0xFFE2E2E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF777680),
)

val VimivoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1A2188),
    primaryContainer = Color(0xFF353DAB),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFBBC7D1),
    onSecondary = Color(0xFF25313A),
    secondaryContainer = Color(0xFF3B4852),
    onSecondaryContainer = Color(0xFFD7E3EC),
    tertiary = Color(0xFFE8B9D4),
    onTertiary = Color(0xFF45263B),
    background = Color(0xFF131316),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0E0E11),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF2A292D),
    surfaceContainerHighest = Color(0xFF353438),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF91909A),
)

val VimivoAmoledColorScheme = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1A2188),
    primaryContainer = Color(0xFF353DAB),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFBBC7D1),
    onSecondary = Color(0xFF25313A),
    background = Color.Black,
    onBackground = Color(0xFFE4E2E6),
    surface = Color.Black,
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0E),
    surfaceContainer = Color(0xFF121216),
    surfaceContainerHigh = Color(0xFF1C1C22),
    surfaceContainerHighest = Color(0xFF26262E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF91909A),
)

val VimivoHighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF000080),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0000B3),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF000000),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF000000),
    error = Color(0xFF990000),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF000000),
)

val VimivoHighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFF00),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFE6E600),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFFFFFFF),
    error = Color(0xFFFF6666),
    onError = Color(0xFF000000),
    outline = Color(0xFFFFFFFF),
)

val VimivoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

fun calculateRelativeLuminance(color: Color): Double {
    fun channelLuminance(value: Float): Double {
        val d = value.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }
    val r = channelLuminance(color.red)
    val g = channelLuminance(color.green)
    val b = channelLuminance(color.blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun calculateContrastRatio(foreground: Color, background: Color): Double {
    val lum1 = calculateRelativeLuminance(foreground)
    val lum2 = calculateRelativeLuminance(background)
    val brighter = max(lum1, lum2)
    val darker = min(lum1, lum2)
    return (brighter + 0.05) / (darker + 0.05)
}
