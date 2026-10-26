package dev.vimivo.platform.android.playback

import android.graphics.Bitmap
import dev.vimivo.playback.BlackBarDetector
import dev.vimivo.playback.CropPreset
import dev.vimivo.playback.CropRect
import dev.vimivo.playback.VideoCropMath

/** The crop chosen in the video panel, with the size of the picture it was worked out for. */
data class VideoCrop(val preset: CropPreset, val rect: CropRect?, val sourceWidth: Int, val sourceHeight: Int)

/** Works out a crop for the frame on screen: a fixed ratio from its size, or the picture inside its black bars. */
object VideoCropping {
    /** Black-bar detection looks at a copy this wide; the result is scaled back to the frame's own pixels. */
    private const val ANALYSIS_WIDTH = 480

    fun crop(preset: CropPreset, frame: Bitmap): VideoCrop {
        val rect = when {
            preset == CropPreset.NONE -> null
            preset == CropPreset.AUTO -> autoCrop(frame)
            else -> preset.ratio?.let { VideoCropMath.centered(frame.width, frame.height, it) }
        }
        return VideoCrop(preset, rect, frame.width, frame.height)
    }

    private fun autoCrop(frame: Bitmap): CropRect? {
        val width = minOf(frame.width, ANALYSIS_WIDTH)
        val height = maxOf(1, frame.height * width / frame.width)
        val small = if (width == frame.width) frame else Bitmap.createScaledBitmap(frame, width, height, true)
        try {
            val pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            val luma = ByteArray(pixels.size) { index ->
                val pixel = pixels[index]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                ((red * 299 + green * 587 + blue * 114) / 1000).toByte()
            }
            val bounds = BlackBarDetector.contentBounds(width, height, luma) ?: return null
            val scaled = CropRect(
                bounds.x * frame.width / width,
                bounds.y * frame.height / height,
                minOf(bounds.width * frame.width / width, frame.width),
                minOf(bounds.height * frame.height / height, frame.height),
            )
            return VideoCropMath.fromContent(frame.width, frame.height, scaled)
        } finally {
            if (small !== frame) small.recycle()
        }
    }
}
