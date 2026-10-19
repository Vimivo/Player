package dev.vimivo.desktop.diagnostics

import java.awt.Component
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/** What the screen shows inside the video surface: the share of visibly lit pixels and how many colours appear. */
internal data class VideoPixelSample(val litFraction: Double, val distinctColours: Int, val width: Int, val height: Int) {
    /** A playing colour-bars video lights most of the surface in several clearly different colours. */
    val showsPicture: Boolean get() = litFraction >= MIN_LIT_FRACTION && distinctColours >= MIN_COLOURS

    override fun toString() = "lit=%.2f colours=%d size=%dx%d".format(litFraction, distinctColours, width, height)

    companion object {
        const val MIN_LIT_FRACTION = 0.25
        const val MIN_COLOURS = 3
    }
}

/**
 * Reads back the composited screen pixels under the video surface (the app's own window, so macOS needs no Screen
 * Recording permission). This catches what decoder statistics cannot: a renderer that draws into a detached layer,
 * or something opaque stacked over the video.
 */
internal object DesktopVideoProbe {
    private const val GRID = 24
    private const val LIT_THRESHOLD = 40
    private const val COLOUR_BUCKET = 64

    fun sample(surface: Component): VideoPixelSample? {
        var bounds: Rectangle? = null
        SwingUtilities.invokeAndWait {
            if (surface.isShowing && surface.width > 1 && surface.height > 1) {
                bounds = Rectangle(surface.locationOnScreen, surface.size)
            }
        }
        val area = bounds ?: return null
        return analyse(Robot().createScreenCapture(area))
    }

    internal fun analyse(image: BufferedImage): VideoPixelSample {
        var lit = 0
        val colours = HashSet<Int>()
        for (gx in 0 until GRID) for (gy in 0 until GRID) {
            val rgb = image.getRGB((gx + 0.5).times(image.width / GRID.toDouble()).toInt(), (gy + 0.5).times(image.height / GRID.toDouble()).toInt())
            val r = rgb shr 16 and 0xFF
            val g = rgb shr 8 and 0xFF
            val b = rgb and 0xFF
            if (maxOf(r, g, b) > LIT_THRESHOLD) {
                lit++
                colours += (r / COLOUR_BUCKET shl 8) or (g / COLOUR_BUCKET shl 4) or (b / COLOUR_BUCKET)
            }
        }
        return VideoPixelSample(lit.toDouble() / (GRID * GRID), colours.size, image.width, image.height)
    }
}
