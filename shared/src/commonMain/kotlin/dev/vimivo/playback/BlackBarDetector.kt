package dev.vimivo.playback

/**
 * Finds the picture inside black bars (letterbox or pillarbox) from a frame's luma plane. The frame is handed over as
 * 8-bit brightness values, row by row, so every platform can feed it from its own image type (a PixelCopy bitmap on
 * Android, a CGImage on iOS); the detection is the same everywhere.
 */
object BlackBarDetector {
    /** A row or column whose brightest value is at or under this is part of a bar (video "black" is about 16). */
    const val BLACK_LEVEL: Int = 40

    /** At most this fraction of a line may be brighter than [BLACK_LEVEL] and still count as black (noise, logos). */
    private const val NOISE_FRACTION = 0.01

    /**
     * The bounds of the picture area in a [width]×[height] luma plane, or null when the whole frame is black. The
     * result is not aligned or filtered; pass it to [VideoCropMath.fromContent] to get a usable crop.
     */
    fun contentBounds(width: Int, height: Int, luma: ByteArray): CropRect? {
        require(width > 0 && height > 0 && luma.size >= width * height) { "The luma plane does not match its size" }
        fun bright(x: Int, y: Int) = (luma[y * width + x].toInt() and 0xFF) > BLACK_LEVEL
        fun rowHasContent(y: Int) = (0 until width).count { bright(it, y) } > width * NOISE_FRACTION
        val top = (0 until height).firstOrNull(::rowHasContent) ?: return null
        val bottom = (height - 1 downTo top).first(::rowHasContent)
        fun columnHasContent(x: Int) = (top..bottom).count { bright(x, it) } > (bottom - top + 1) * NOISE_FRACTION
        val left = (0 until width).firstOrNull(::columnHasContent) ?: return null
        val right = (width - 1 downTo left).first(::columnHasContent)
        return CropRect(left, top, right - left + 1, bottom - top + 1)
    }

    /** The smallest rectangle holding all of [bounds]: bars that vary between scenes are cut only where all agree. */
    fun union(bounds: List<CropRect>): CropRect? {
        if (bounds.isEmpty()) return null
        val left = bounds.minOf { it.x }
        val top = bounds.minOf { it.y }
        val right = bounds.maxOf { it.x + it.width }
        val bottom = bounds.maxOf { it.y + it.height }
        return CropRect(left, top, right - left, bottom - top)
    }
}
