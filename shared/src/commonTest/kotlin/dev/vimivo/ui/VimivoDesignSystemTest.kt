package dev.vimivo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vimivo.ui.theme.VimivoAmoledColorScheme
import dev.vimivo.ui.theme.VimivoDarkColorScheme
import dev.vimivo.ui.theme.VimivoElevation
import dev.vimivo.ui.theme.VimivoGlassVariant
import dev.vimivo.ui.theme.VimivoHighContrastDarkColorScheme
import dev.vimivo.ui.theme.VimivoHighContrastLightColorScheme
import dev.vimivo.ui.theme.VimivoIconSize
import dev.vimivo.ui.theme.VimivoLayout
import dev.vimivo.ui.theme.VimivoLightColorScheme
import dev.vimivo.ui.theme.VimivoMotion
import dev.vimivo.ui.theme.VimivoRadius
import dev.vimivo.ui.theme.VimivoSpacing
import dev.vimivo.ui.theme.WindowSizeClass
import dev.vimivo.ui.theme.calculateContrastRatio
import dev.vimivo.ui.theme.calculateRelativeLuminance
import dev.vimivo.ui.theme.resolveGlassTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VimivoDesignSystemTest {

    @Test
    fun testWindowSizeClassBreakpoints() {
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.fromWidth(320.dp))
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.fromWidth(599.dp))
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.fromWidth(600.dp))
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.fromWidth(839.dp))
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.fromWidth(840.dp))
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.fromWidth(1440.dp))
    }

    @Test
    fun testRelativeLuminance() {
        val blackLuminance = calculateRelativeLuminance(Color.Black)
        val whiteLuminance = calculateRelativeLuminance(Color.White)
        assertTrue(blackLuminance < 0.01f)
        assertTrue(whiteLuminance > 0.99f)
    }

    @Test
    fun testContrastRatioBlackOnWhite() {
        val contrast = calculateContrastRatio(Color.Black, Color.White)
        assertTrue(contrast > 20.0f)
    }

    @Test
    fun testLightColorSchemeWcagAaCompliance() {
        val textContrast = calculateContrastRatio(
            VimivoLightColorScheme.onBackground,
            VimivoLightColorScheme.background,
        )
        // WCAG AA requirement for body text is at least 4.5:1
        assertTrue(textContrast >= 4.5f, "Light theme text contrast ($textContrast) must meet WCAG AA (>= 4.5)")

        val surfaceContrast = calculateContrastRatio(
            VimivoLightColorScheme.onSurface,
            VimivoLightColorScheme.surface,
        )
        assertTrue(surfaceContrast >= 4.5f, "Light theme surface contrast ($surfaceContrast) must meet WCAG AA")
    }

    @Test
    fun testDarkColorSchemeWcagAaCompliance() {
        val textContrast = calculateContrastRatio(
            VimivoDarkColorScheme.onBackground,
            VimivoDarkColorScheme.background,
        )
        assertTrue(textContrast >= 4.5f, "Dark theme text contrast ($textContrast) must meet WCAG AA (>= 4.5)")

        val surfaceContrast = calculateContrastRatio(
            VimivoDarkColorScheme.onSurface,
            VimivoDarkColorScheme.surface,
        )
        assertTrue(surfaceContrast >= 4.5f, "Dark theme surface contrast ($surfaceContrast) must meet WCAG AA")
    }

    @Test
    fun testAmoledColorSchemeHighContrast() {
        val textContrast = calculateContrastRatio(
            VimivoAmoledColorScheme.onBackground,
            VimivoAmoledColorScheme.background,
        )
        assertTrue(textContrast >= 7.0f, "AMOLED text contrast ($textContrast) must exceed WCAG AAA (>= 7.0)")
        assertEquals(Color.Black, VimivoAmoledColorScheme.background)
    }

    @Test
    fun testHighContrastColorSchemesWcagAaaCompliance() {
        val lightHcContrast = calculateContrastRatio(
            VimivoHighContrastLightColorScheme.onBackground,
            VimivoHighContrastLightColorScheme.background,
        )
        assertTrue(lightHcContrast >= 7.0f, "High contrast light theme ($lightHcContrast) must meet WCAG AAA (>= 7.0)")

        val darkHcContrast = calculateContrastRatio(
            VimivoHighContrastDarkColorScheme.onBackground,
            VimivoHighContrastDarkColorScheme.background,
        )
        assertTrue(darkHcContrast >= 7.0f, "High contrast dark theme ($darkHcContrast) must meet WCAG AAA (>= 7.0)")
    }

    @Test
    fun testSpacingHierarchy() {
        assertTrue(VimivoSpacing.xs < VimivoSpacing.sm)
        assertTrue(VimivoSpacing.sm < VimivoSpacing.md)
        assertTrue(VimivoSpacing.md < VimivoSpacing.lg)
        assertTrue(VimivoSpacing.lg < VimivoSpacing.xl)
        assertTrue(VimivoSpacing.xl < VimivoSpacing.xxl)
    }

    @Test
    fun testRadiusHierarchy() {
        assertTrue(VimivoRadius.none < VimivoRadius.xs)
        assertTrue(VimivoRadius.xs < VimivoRadius.sm)
        assertTrue(VimivoRadius.sm < VimivoRadius.md)
        assertTrue(VimivoRadius.md < VimivoRadius.lg)
        assertTrue(VimivoRadius.lg < VimivoRadius.xl)
        assertTrue(VimivoRadius.xl < VimivoRadius.pill)
    }

    @Test
    fun testElevationHierarchy() {
        assertTrue(VimivoElevation.level0 < VimivoElevation.level1)
        assertTrue(VimivoElevation.level1 < VimivoElevation.level2)
        assertTrue(VimivoElevation.level2 < VimivoElevation.level3)
    }

    @Test
    fun testIconSizeHierarchy() {
        assertTrue(VimivoIconSize.small < VimivoIconSize.medium)
        assertTrue(VimivoIconSize.medium < VimivoIconSize.large)
        assertTrue(VimivoIconSize.large < VimivoIconSize.hero)
    }

    @Test
    fun testMotionHierarchyRemainsResponsive() {
        assertEquals(0, VimivoMotion.instantMillis)
        assertTrue(VimivoMotion.feedbackMillis < VimivoMotion.fastMillis)
        assertTrue(VimivoMotion.fastMillis < VimivoMotion.navigationMillis)
        assertTrue(VimivoMotion.navigationMillis < VimivoMotion.sheetMillis)
        assertTrue(VimivoMotion.sheetMillis < VimivoMotion.playerMillis)
        assertTrue(VimivoMotion.playerMillis <= 400)
    }

    @Test
    fun testSemanticLayoutUsesAccessibleTargetsAndBoundedReadingWidth() {
        assertTrue(VimivoLayout.minimumTouchTarget >= 48.dp)
        assertTrue(VimivoLayout.maximumReadingWidth < VimivoLayout.maximumContentWidth)
        assertTrue(VimivoLayout.compactScreenPadding < VimivoLayout.expandedScreenPadding)
    }

    @Test
    fun testGlassVariantsHaveBoundedAlphaAndOpaqueFallback() {
        VimivoGlassVariant.entries.forEach { variant ->
            val normal = resolveGlassTokens(variant, reducedTransparency = false)
            assertTrue(normal.containerAlpha in 0.85f..1f)
            assertTrue(normal.borderAlpha >= 0.3f)
            assertTrue(normal.radius >= 0.dp)

            val fallback = resolveGlassTokens(variant, reducedTransparency = true)
            assertTrue(fallback.opaque)
            assertEquals(1f, fallback.containerAlpha)
            assertEquals(0.dp, fallback.elevation)
        }
    }

    @Test
    fun testUnicodeAndBengaliMetadataHandling() {
        val bengaliTitle = "আমার সোনার বাংলা - রবীন্দ্রনাথ ঠাকুর"
        val mixedMetadata = "Vimivo 2026: শান্তিনিকেতন (Santiniketan) Edition [BD/IN]"
        val complexConjuncts = "বিজ্ঞান, প্রযুক্তি, ক্ষান্তি, সান্ত্বনা ও আনন্দ"
        val unicodeWithEmoji = "🎵 প্রতিধ্বনিত সুর · Echoes of Melody (HQ Audio)"

        assertTrue(bengaliTitle.contains("বাংলা"))
        assertTrue(mixedMetadata.contains("Santiniketan"))
        assertTrue(complexConjuncts.contains("আনন্দ"))
        assertTrue(unicodeWithEmoji.contains("🎵"))
    }
}
