package dev.vimivo.desktop.diagnostics

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression (D1 black video): the smoke test must fail on a black surface and pass on a visible picture. */
class DesktopVideoProbeTest {
    @Test
    fun blackSurfaceShowsNoPicture() {
        val sample = DesktopVideoProbe.analyse(image(Color.BLACK))
        assertEquals(0.0, sample.litFraction)
        assertFalse(sample.showsPicture)
    }

    @Test
    fun singleFlatColourIsNotAPicture() {
        // A Compose background showing through (white, or black with tint) is one flat colour.
        assertFalse(DesktopVideoProbe.analyse(image(Color.WHITE)).showsPicture)
    }

    @Test
    fun colourBarsShowAPicture() {
        val bars = listOf(Color.WHITE, Color.YELLOW, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.BLUE)
        val image = BufferedImage(700, 400, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until image.width) for (y in 0 until image.height) image.setRGB(x, y, bars[x / 100].rgb)
        assertTrue(DesktopVideoProbe.analyse(image).showsPicture)
    }

    private fun image(colour: Color) = BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB).apply {
        val graphics = createGraphics()
        graphics.color = colour
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
    }
}
