@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.Player
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.exoplayer.ExoPlayer
import dev.vimivo.playback.VideoAdjustments

/**
 * Media3 GL effects for the picture. Facts that shaped this class:
 * - ExoPlayer builds its effect pipeline only when the effects are set before `prepare()`, so the first change stops,
 *   sets the effects, re-prepares and seeks back; playback that never touches the colour controls stays on the plain
 *   decoder-to-surface path.
 * - The pipeline needs the surface size, which only a `SurfaceHolder` reports, so the output is re-attached as one.
 * - Some GL stacks (the Android emulator's, so far) produce no frames at all with the pipeline on. A watchdog waits
 *   for the first frame and, if none comes, marks the feature unsupported so the UI can say so. ExoPlayer keeps a
 *   renderer on the effect path for good once effects were set (even an empty list), so the plain picture comes back
 *   only after the player is rebuilt, which in practice means restarting the app.
 * - Nothing starts the pipeline until a colour control is changed, so playback that never uses them is unaffected.
 */
internal class Media3VideoColorEffects(
    private val player: ExoPlayer,
    private val video: Media3VideoOutput,
    private val onChanged: () -> Unit,
) : VideoColorEffects {
    private val handler = Handler(Looper.getMainLooper())
    private var pipelineReady = false
    private var failed = false
    private var awaitingFrame = false
    /** A working pipeline has shown a frame and knows the picture's size by now; a dead one reports neither. */
    private val watchdog = Runnable { if (awaitingFrame || player.videoSize.width == 0) giveUp() }

    override val supported: Boolean get() = !failed

    override val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            awaitingFrame = false
        }
    }

    override fun apply(adjustments: VideoAdjustments) {
        check(!failed) { "Colour effects rendered no video on this device; restart the app to restore the picture" }
        val effects = effects(adjustments)
        if (pipelineReady) {
            player.setVideoEffects(effects)
        } else if (effects.isNotEmpty()) {
            reprepare(effects)
            pipelineReady = true
            awaitingFrame = true
            handler.postDelayed(watchdog, FIRST_FRAME_TIMEOUT_MILLIS)
        }
    }

    private fun reprepare(effects: List<Effect>) {
        val position = player.currentPosition
        player.stop()
        player.setVideoEffects(effects)
        video.reattach()
        player.prepare()
        player.seekTo(position)
    }

    private fun giveUp() {
        Log.w(TAG, "No video frame after enabling colour effects; reporting the feature as unsupported")
        failed = true
        awaitingFrame = false
        onChanged()
    }

    companion object {
        private const val TAG = "VimivoColorEffects"
        private const val FIRST_FRAME_TIMEOUT_MILLIS = 4_000L
        private const val SLIDER_MAX = 100
        private const val HUE_DEGREES_PER_STEP = 1.8f

        /** The effect chain for [adjustments]; empty when nothing is changed. Hue's full slider is a half turn. */
        fun effects(adjustments: VideoAdjustments): List<Effect> = buildList {
            if (adjustments.brightness != 0) add(Brightness(adjustments.brightness.limited() / SLIDER_MAX.toFloat()))
            if (adjustments.contrast != 0) add(Contrast(adjustments.contrast.limited() / SLIDER_MAX.toFloat()))
            if (adjustments.saturation != 0 || adjustments.hue != 0) {
                add(
                    HslAdjustment.Builder()
                        .adjustHue(adjustments.hue.limited() * HUE_DEGREES_PER_STEP)
                        .adjustSaturation(adjustments.saturation.limited().toFloat())
                        .build(),
                )
            }
        }

        private fun Int.limited() = coerceIn(-SLIDER_MAX, SLIDER_MAX)
    }
}
