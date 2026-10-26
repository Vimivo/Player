package dev.vimivo.playback

/** A rectangle of the decoded picture, in pixels. */
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "A crop needs a positive size" }
        require(x >= 0 && y >= 0) { "A crop cannot start outside the picture" }
    }

    /** libmpv's `video-crop` value: `WxH+X+Y`. */
    fun toMpvValue(): String = "${width}x$height+$x+$y"
}

/** Crop choices in the video panel (V1 #12). [ratio] is width / height; [AUTO] detects black bars instead. */
enum class CropPreset(val label: String, val ratio: Double?) {
    NONE("None", null),
    AUTO("Auto (remove black bars)", null),
    RATIO_16_9("16:9", 16.0 / 9.0),
    RATIO_21_9("21:9", 21.0 / 9.0),
    RATIO_4_3("4:3", 4.0 / 3.0),
    RATIO_1_1("1:1", 1.0),
}

/** How a view that shows the whole picture fitted in its box must be scaled and moved so [CropRect] fills the box. */
data class CropTransform(val scale: Float, val translateX: Float, val translateY: Float)

object VideoCropMath {
    /** Bars thinner than this many pixels are noise (rounding, a soft edge), not bars. */
    const val MIN_BAR_PIXELS: Int = 4

    /**
     * The largest centred rectangle with [ratio] inside a [sourceWidth]×[sourceHeight] picture, or null if it is the
     * whole picture. Offsets and sizes are even (chroma-subsampled video cannot be cropped on odd pixels).
     */
    fun centered(sourceWidth: Int, sourceHeight: Int, ratio: Double): CropRect? {
        require(sourceWidth > 0 && sourceHeight > 0 && ratio > 0) { "Invalid picture size or ratio" }
        val sourceRatio = sourceWidth.toDouble() / sourceHeight
        var width = even(sourceWidth)
        var height = even(sourceHeight)
        if (ratio < sourceRatio) {
            width = even((sourceHeight * ratio).toInt())
        } else {
            height = even((sourceWidth / ratio).toInt())
        }
        val rect = CropRect(even((sourceWidth - width) / 2), even((sourceHeight - height) / 2), width, height)
        val noBars = sourceWidth - rect.width < MIN_BAR_PIXELS && sourceHeight - rect.height < MIN_BAR_PIXELS
        return rect.takeUnless { noBars }
    }

    /** Turns detected content bounds into a crop, or null when the bars are too thin to matter (no bars). */
    fun fromContent(sourceWidth: Int, sourceHeight: Int, content: CropRect): CropRect? {
        val x = even(content.x)
        val y = even(content.y)
        val width = even(minOf(content.width, sourceWidth - x))
        val height = even(minOf(content.height, sourceHeight - y))
        if (width <= 0 || height <= 0) return null
        val cropped = CropRect(x, y, width, height)
        val barsAcross = sourceWidth - cropped.width
        val barsDown = sourceHeight - cropped.height
        return cropped.takeUnless { barsAcross < MIN_BAR_PIXELS && barsDown < MIN_BAR_PIXELS }
    }

    private fun even(value: Int) = value - (value and 1)

    /**
     * The scale and translation (in view pixels, applied about the view's centre) that make [crop] fill a
     * [containerWidth]×[containerHeight] box in which the [sourceWidth]×[sourceHeight] picture is shown fitted.
     */
    fun transform(
        containerWidth: Float,
        containerHeight: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        crop: CropRect,
    ): CropTransform {
        require(containerWidth > 0 && containerHeight > 0 && sourceWidth > 0 && sourceHeight > 0) { "Invalid sizes" }
        val fitted = minOf(containerWidth / sourceWidth, containerHeight / sourceHeight)
        val wanted = minOf(containerWidth / crop.width, containerHeight / crop.height)
        val offsetX = crop.x + crop.width / 2f - sourceWidth / 2f
        val offsetY = crop.y + crop.height / 2f - sourceHeight / 2f
        return CropTransform(wanted / fitted, -offsetX * wanted, -offsetY * wanted)
    }
}
