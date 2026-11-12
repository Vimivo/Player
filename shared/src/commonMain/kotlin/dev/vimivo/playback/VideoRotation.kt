package dev.vimivo.playback

import kotlin.math.min

/**
 * Turning the picture itself in quarter steps, independent of how the phone or window is held.
 * The player rotates the video surface (or asks the engine to); this holds the maths both share.
 */
enum class VideoRotation(val degrees: Int) {
    NONE(0),
    CLOCKWISE_90(90),
    CLOCKWISE_180(180),
    CLOCKWISE_270(270),
    ;

    /** The next quarter turn clockwise; after 270° it is back to the original picture. */
    fun next(): VideoRotation = entries[(ordinal + 1) % entries.size]

    val isSideways: Boolean get() = this == CLOCKWISE_90 || this == CLOCKWISE_270

    val label: String get() = if (this == NONE) "Original" else "$degrees°"

    /**
     * The scale that keeps the whole rotated picture inside a [containerWidth] x [containerHeight] area.
     * The unrotated picture is assumed to be fitted (letterboxed) in the area already, at [videoAspect]
     * (width / height). Upright and upside-down pictures need no scaling; a sideways one is scaled so its
     * turned bounding box still fits.
     */
    fun fitScale(containerWidth: Float, containerHeight: Float, videoAspect: Float): Float {
        if (!isSideways) return 1f
        if (containerWidth <= 0f || containerHeight <= 0f || videoAspect <= 0f || !videoAspect.isFinite()) return 1f
        val fittedWidth: Float
        val fittedHeight: Float
        if (containerWidth / containerHeight > videoAspect) {
            fittedHeight = containerHeight
            fittedWidth = containerHeight * videoAspect
        } else {
            fittedWidth = containerWidth
            fittedHeight = containerWidth / videoAspect
        }
        // After a quarter turn the picture is fittedHeight wide and fittedWidth tall.
        return min(containerWidth / fittedHeight, containerHeight / fittedWidth)
    }
}
