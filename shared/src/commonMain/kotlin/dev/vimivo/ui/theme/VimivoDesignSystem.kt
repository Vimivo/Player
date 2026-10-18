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

/** Semantic layout aliases built from the primitive spacing scale. */
object VimivoLayout {
    val compactScreenPadding: Dp = VimivoSpacing.lg
    val expandedScreenPadding: Dp = VimivoSpacing.xl
    val sectionSpacing: Dp = VimivoSpacing.xxl
    val compactContentGap: Dp = VimivoSpacing.sm
    val comfortableContentGap: Dp = VimivoSpacing.lg
    val minimumTouchTarget: Dp = 48.dp
    val maximumReadingWidth: Dp = 720.dp
    val maximumContentWidth: Dp = 1_280.dp
}

/** Component tokens and breakpoint policy for the responsive application shell. */
object VimivoShell {
    val compactNavigationHeight: Dp = 80.dp
    val navigationRailWidth: Dp = 88.dp
    val expandedSidebarWidth: Dp = 220.dp
    val collapsedSidebarWidth: Dp = 88.dp
    val desktopToolbarHeight: Dp = 64.dp
    val desktopQueueWidth: Dp = 360.dp
    val floatingDockMaxWidth: Dp = 960.dp
    val tabBarHeight: Dp = 64.dp

    /** Side margin, corner radius, and gap shared by the floating tab bar and mini player stack. */
    val floatingChromeMargin: Dp = VimivoSpacing.lg
    val floatingChromeRadius: Dp = 32.dp
    val floatingChromeGap: Dp = VimivoSpacing.sm

    fun navigationMode(width: Dp): ShellNavigationMode = when {
        width < 720.dp -> ShellNavigationMode.BOTTOM_BAR
        width < 1_000.dp -> ShellNavigationMode.RAIL
        else -> ShellNavigationMode.RAIL_WITH_SPLIT_VIEW
    }
}

enum class ShellNavigationMode {
    BOTTOM_BAR,
    RAIL,
    RAIL_WITH_SPLIT_VIEW,
}

/** Component-layer measurements for the immersive audio player. */
object VimivoPlayer {
    val compactArtwork: Dp = 48.dp
    val queueArtwork: Dp = 52.dp
    val transportButton: Dp = 48.dp
    val primaryTransportButton: Dp = 72.dp
    val lyricsViewport: Dp = 280.dp
    val artworkMaximum: Dp = 520.dp
}

/** Shared motion timings. Callers must bypass decorative transitions when [LocalReducedMotion] is true. */
object VimivoMotion {
    const val instantMillis: Int = 0
    const val feedbackMillis: Int = 120
    const val fastMillis: Int = 160
    const val navigationMillis: Int = 240
    const val sheetMillis: Int = 300
    const val playerMillis: Int = 360
}

enum class VimivoGlassVariant {
    NAVIGATION,
    FLOATING_PLAYER,
    CONTROL,
    SHEET,
    POPOVER,
    SOLID_FALLBACK,
}

/** Component-layer tokens for a glass surface variant. */
data class VimivoGlassTokens(
    val containerAlpha: Float,
    val borderAlpha: Float,
    val radius: Dp,
    val elevation: Dp,
    val opaque: Boolean,
)

fun resolveGlassTokens(
    variant: VimivoGlassVariant,
    reducedTransparency: Boolean,
): VimivoGlassTokens {
    val opaque = reducedTransparency || variant == VimivoGlassVariant.SOLID_FALLBACK
    return VimivoGlassTokens(
        containerAlpha = if (opaque) 1f else glassContainerAlpha(variant),
        borderAlpha = if (opaque) 0.72f else 0.32f,
        radius = glassRadius(variant),
        elevation = if (opaque) VimivoElevation.level0 else glassElevation(variant),
        opaque = opaque,
    )
}

private fun glassContainerAlpha(variant: VimivoGlassVariant): Float = when (variant) {
    VimivoGlassVariant.FLOATING_PLAYER -> 0.94f
    VimivoGlassVariant.SHEET -> 0.96f
    VimivoGlassVariant.CONTROL -> 0.88f
    else -> 0.90f
}

private fun glassRadius(variant: VimivoGlassVariant): Dp = when (variant) {
    VimivoGlassVariant.FLOATING_PLAYER, VimivoGlassVariant.SHEET -> VimivoRadius.xl
    VimivoGlassVariant.CONTROL -> VimivoRadius.pill
    VimivoGlassVariant.POPOVER, VimivoGlassVariant.NAVIGATION -> VimivoRadius.lg
    VimivoGlassVariant.SOLID_FALLBACK -> VimivoRadius.md
}

private fun glassElevation(variant: VimivoGlassVariant): Dp = when (variant) {
    VimivoGlassVariant.FLOATING_PLAYER, VimivoGlassVariant.SHEET -> VimivoElevation.level4
    VimivoGlassVariant.POPOVER -> VimivoElevation.level3
    VimivoGlassVariant.NAVIGATION -> VimivoElevation.level2
    else -> VimivoElevation.level1
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
val LocalReducedTransparency = staticCompositionLocalOf { false }
val LocalHighContrast = staticCompositionLocalOf { false }
val LocalWindowSizeClass = staticCompositionLocalOf { WindowSizeClass.MEDIUM }
val LocalTouchTargetScale = staticCompositionLocalOf { 1.0f }

object VimivoColors {
    // Primary Brand Accents: Neon Cyan and Electric Violet
    val NeonCyan = Color(0xFF00E5FF)
    val NeonCyanDark = Color(0xFF00B4D8)
    val ElectricViolet = Color(0xFF8B5CF6)
    val ElectricVioletLight = Color(0xFFA78BFA)

    // Neutral Surfaces & Backgrounds
    val DarkBackground = Color(0xFF0A0A0C)
    val DarkSurface = Color(0xFF141418)
    val DarkSurfaceElevated = Color(0xFF1B1B21)
    val DarkBorder = Color(0xFF24242C)
    val DarkBorderLight = Color(0xFF32323D)

    // Typography Tokens
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextTertiary = Color(0xFF71717A)

    // Legacy Aurora brand accents for backward compatibility
    val AuroraTeal = Color(0xFF007F78)
    val AuroraTealLight = Color(0xFF71DBD2)
    val AuroraWarm = Color(0xFFC97852)

    // Semantic Status
    val Success = Color(0xFF10B981)
    val SuccessContainer = Color(0xFF064E3B)
    val Warning = Color(0xFFF59E0B)
    val WarningContainer = Color(0xFF78350F)
    val Error = Color(0xFFEF4444)
    val ErrorContainer = Color(0xFF7F1D1D)
    val Info = Color(0xFF38BDF8)
    val InfoContainer = Color(0xFF0C4A6E)

    // Focus Outline Token (High-visibility for keyboard navigation)
    val FocusOutlineLight = Color(0xFF4D56C7)
    val FocusOutlineDark = Color(0xFF00E5FF)
}

val VimivoLightColorScheme = lightColorScheme(
    primary = Color(0xFF006B66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA0F2E9),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF536471),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3EC),
    onSecondaryContainer = Color(0xFF101C27),
    tertiary = Color(0xFF8B4F35),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF171D1C),
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
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004E57),
    onPrimaryContainer = Color(0xFF9CF4FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF281157),
    secondaryContainer = Color(0xFF3C2278),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF003549),
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF141418),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF0A0A0C),
    surfaceContainerLow = Color(0xFF101014),
    surfaceContainer = Color(0xFF141418),
    surfaceContainerHigh = Color(0xFF1B1B21),
    surfaceContainerHighest = Color(0xFF24242C),
    error = Color(0xFFEF4444),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF24242C),
    outlineVariant = Color(0xFF1E1E26),
)

val VimivoAmoledColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004E57),
    onPrimaryContainer = Color(0xFF9CF4FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF281157),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F7),
    surface = Color.Black,
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF101014),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070709),
    surfaceContainer = Color(0xFF0E0E12),
    surfaceContainerHigh = Color(0xFF16161C),
    surfaceContainerHighest = Color(0xFF202028),
    error = Color(0xFFEF4444),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    outline = Color(0xFF202028),
    outlineVariant = Color(0xFF16161E),
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
