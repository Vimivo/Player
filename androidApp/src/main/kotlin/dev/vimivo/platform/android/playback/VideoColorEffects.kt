package dev.vimivo.platform.android.playback

import androidx.media3.common.Player
import dev.vimivo.playback.VideoAdjustments

/**
 * Brightness, contrast, saturation and hue for ExoPlayer's picture (V1 #14). The real implementation uses Media3's GL
 * effect pipeline and exists only in debug builds until it has been proven on phones; release builds get
 * [DisabledVideoColorEffects] and do not even contain the effect library.
 */
internal interface VideoColorEffects {
    /** False when this build or this device cannot do it. */
    val supported: Boolean

    /** Listener the engine registers with the player. */
    val listener: Player.Listener

    /** Applies [adjustments]; sliders run from -100 to 100. */
    fun apply(adjustments: VideoAdjustments)
}

internal object DisabledVideoColorEffects : VideoColorEffects {
    override val supported = false
    override val listener = object : Player.Listener {}

    override fun apply(adjustments: VideoAdjustments) =
        throw UnsupportedOperationException("Video colour controls are not available in this build")
}
