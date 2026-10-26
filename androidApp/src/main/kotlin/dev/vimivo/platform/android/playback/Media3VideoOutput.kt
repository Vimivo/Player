@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package dev.vimivo.platform.android.playback

import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a Media3 player draws its video, kept so a snapshot can be copied from the same surface. */
class Media3VideoOutput internal constructor(private val player: ExoPlayer) {
    private var surface: Surface? = null

    /** Attaches the video output (a [Surface] or [SurfaceHolder]) the session hands over. */
    fun attach(output: Any) {
        when (output) {
            is Surface -> {
                surface = output
                player.setVideoSurface(output)
            }
            is SurfaceHolder -> {
                surface = output.surface
                player.setVideoSurfaceHolder(output)
            }
        }
    }

    fun clear() {
        surface = null
        player.clearVideoSurface()
    }

    /** The frame on screen as a bitmap at the video's own size; fails when nothing is being shown. */
    suspend fun captureFrame(): Result<Bitmap> {
        val current = surface ?: return Result.failure(IllegalStateException("There is no video on screen"))
        // ExoPlayer is read on its own thread; the copy itself runs on the snapshot thread.
        val size = withContext(Dispatchers.Main.immediate) { player.videoSize }
        return SurfaceSnapshot.capture(current, size.width, size.height)
    }
}
