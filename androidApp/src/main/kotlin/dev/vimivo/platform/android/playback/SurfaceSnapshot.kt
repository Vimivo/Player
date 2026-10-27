package dev.vimivo.platform.android.playback

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Copies what a video [Surface] shows into a bitmap with `PixelCopy` (the only way to read a hardware surface). */
internal object SurfaceSnapshot {
    /** Captures [surface] at [width] × [height] pixels; fails when the surface is gone or the copy is refused. */
    suspend fun capture(surface: Surface, width: Int, height: Int): Result<Bitmap> {
        if (!surface.isValid || width <= 0 || height <= 0) {
            return Result.failure(IllegalStateException("There is no picture on screen to capture"))
        }
        return suspendCancellableCoroutine { continuation ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val thread = HandlerThread("vimivo-snapshot").apply { start() }
            continuation.invokeOnCancellation { thread.quitSafely() }
            PixelCopy.request(surface, bitmap, { code ->
                thread.quitSafely()
                continuation.resume(
                    if (code == PixelCopy.SUCCESS) {
                        Result.success(bitmap)
                    } else {
                        Result.failure(IllegalStateException("The picture could not be copied (PixelCopy error $code)"))
                    },
                )
            }, Handler(thread.looper))
        }
    }
}
