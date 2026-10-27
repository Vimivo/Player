@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("DEPRECATION")

package dev.vimivo.platform.android.playback

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.CropPreset
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.SnapshotFormat
import dev.vimivo.playback.VideoAdjustments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * V1 #5 and #16 on Android with a real video in a real surface: stepping moves the picture by exactly one decoded frame
 * each time, forward and back (counted on the decoder's own rendered-frame counter and the position), and a snapshot
 * is the picture that is on screen (a solid red clip comes out red), saved in Pictures/Vimivo.
 */
@RunWith(AndroidJUnit4::class)
class AndroidVideoFramesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun aSnapshotIsThePictureOnScreenAndIsSavedToThePicturesFolder() = withVideo("multitrack_tiny.mkv") { engine ->
        runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 500 } } }
        onMain { engine.pause().getOrThrow() }
        Thread.sleep(SETTLE_MILLIS)
        val bitmap = runBlocking { engine.video.captureFrame() }.getOrThrow()
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val (red, green, blue) = Triple(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF)
        assertTrue("the first track is solid red, the snapshot centre is ($red, $green, $blue)", red > 200 && green < 60 && blue < 60)
        assertEquals("the snapshot has the video's size", 320, bitmap.width)

        val name = "vimivo-test-${System.currentTimeMillis()}.png"
        val uri = runBlocking { AndroidSnapshotStore(context).save(bitmap, name, SnapshotFormat.PNG) }.getOrThrow()
        try {
            val saved = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
            assertEquals(bitmap.width, saved.width)
            assertEquals(bitmap.height, saved.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val path = context.contentResolver.query(
                    uri, arrayOf(MediaStore.Images.Media.RELATIVE_PATH), null, null, null,
                )?.use { if (it.moveToFirst()) it.getString(0) else null }
                assertTrue("saved under Pictures/Vimivo, was $path", path?.startsWith("Pictures/Vimivo") == true)
            }
        } finally {
            if (uri.scheme == "content") context.contentResolver.delete(uri, null, null) else File(checkNotNull(uri.path)).delete()
        }
    }

    @Test
    fun autoCropFindsThePictureInsideTheBlackBarsOfTheFrameOnScreen() = withVideo("letterbox_tiny.mp4") { engine ->
        runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 500 } } }
        onMain { engine.pause().getOrThrow() }
        Thread.sleep(SETTLE_MILLIS)
        val frame = runBlocking { engine.video.captureFrame() }.getOrThrow()
        assertEquals("the clip is 320x240 with 30 black rows above and below", 240, frame.height)

        val auto = VideoCropping.crop(CropPreset.AUTO, frame)
        val rect = checkNotNull(auto.rect) { "black bars were not found" }
        assertEquals(0, rect.x)
        assertEquals(320, rect.width)
        assertEquals("the top bar is 30 rows", 30.0, rect.y.toDouble(), 2.0)
        assertEquals("the picture is 180 rows", 180.0, rect.height.toDouble(), 4.0)

        assertEquals("a 16:9 preset removes the same bars", 30, checkNotNull(VideoCropping.crop(CropPreset.RATIO_16_9, frame).rect).y)
        assertEquals(null, VideoCropping.crop(CropPreset.NONE, frame).rect)
    }

    /**
     * Colour controls use Media3's GL effects (debug builds). Where the GL stack renders them the picture must change;
     * where it renders nothing (the emulator's does not) the feature must notice and report itself as unsupported
     * instead of pretending. ExoPlayer cannot return to the plain path once effects were set, so the picture is not
     * judged in that case.
     */
    @Test
    fun colourControlsEitherChangeThePictureOrReportThatTheyCannotRender() = withVideo("multitrack_tiny.mkv") { engine ->
        assumeTrue("colour controls exist only in debug builds", engine.capabilities.videoAdjustments)
        runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 500 } } }
        onMain { engine.applyVideoAdjustments(VideoAdjustments(brightness = -60)).getOrThrow() }
        // The watchdog gives the pipeline a few seconds to show a first frame; wait past that.
        val deadline = System.currentTimeMillis() + SETTLE_COLOUR_MILLIS
        while (onMain { engine.capabilities.videoAdjustments } && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        if (onMain { engine.capabilities.videoAdjustments }) {
            onMain { engine.seekTo(1_000).getOrThrow(); engine.play().getOrThrow() }
            Thread.sleep(PLAY_MILLIS)
            onMain { engine.pause().getOrThrow() }
            Thread.sleep(SETTLE_MILLIS)
            val bitmap = runBlocking { engine.video.captureFrame() }.getOrThrow()
            val red = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) shr 16 and 0xFF
            assertTrue("the effect is on, so red must be darker than 200, was $red", red < 200)
        } else {
            assertTrue("the failure is reported, and further changes are refused", onMain { engine.applyVideoAdjustments(VideoAdjustments(contrast = 10)) }.isFailure)
        }
    }

    @Test
    fun eachStepMovesOneFrameForwardAndBack() = withVideo("video_landscape.mp4") { engine ->
        runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 1_000 } } }
        onMain { engine.pause().getOrThrow() }
        Thread.sleep(SETTLE_MILLIS)
        val frameMillis = 1_000.0 / onMain { engine.player.videoFormat!!.frameRate }
        val startPosition = onMain { engine.player.currentPosition }
        val startCount = rendered(engine)
        repeat(STEPS) { index ->
            onMain { engine.frameStep(forward = true).getOrThrow() }
            awaitRendered(engine, startCount + index + 1)
        }
        assertEquals("five steps forward render five frames", startCount + STEPS, rendered(engine))
        assertEquals("and move five frame durations", STEPS * frameMillis, (onMain { engine.player.currentPosition } - startPosition).toDouble(), 3.0)
        val afterForward = rendered(engine)
        repeat(STEPS) { index ->
            onMain { engine.frameStep(forward = false).getOrThrow() }
            awaitRendered(engine, afterForward + index + 1)
        }
        assertEquals("five steps back render five frames", afterForward + STEPS, rendered(engine))
        assertEquals("and return to the start", startPosition.toDouble(), onMain { engine.player.currentPosition }.toDouble(), 3.0)
        assertTrue("stepping leaves the video paused", !onMain { engine.player.isPlaying })
    }

    @Test
    fun steppingWithoutVideoIsRefused() {
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            onMain { runBlocking { engine.open(item("track1_solaris.flac", MediaKind.AUDIO)).getOrThrow() } }
            assertTrue(onMain { engine.frameStep(true) }.isFailure)
        } finally {
            onMain { engine.close() }
        }
    }

    private fun rendered(engine: Media3PlayerEngine): Int = onMain {
        engine.player.videoDecoderCounters?.also { it.ensureUpdated() }?.renderedOutputBufferCount ?: 0
    }

    private fun awaitRendered(engine: Media3PlayerEngine, expected: Int) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (rendered(engine) < expected && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    }

    private fun withVideo(asset: String, block: (Media3PlayerEngine) -> Unit) {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val created = CountDownLatch(1)
        lateinit var view: SurfaceView
        scenario.onActivity { activity ->
            view = SurfaceView(activity)
            view.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = created.countDown()
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
            activity.setContentView(view)
        }
        assumeTrue("surface not created", created.await(TIMEOUT, TimeUnit.MILLISECONDS))
        val engine = onMain { Media3PlayerEngine(context) }
        try {
            onMain { engine.video.attach(view.holder) }
            onMain { runBlocking { engine.open(item(asset, MediaKind.VIDEO)).getOrThrow() } }
            onMain { engine.setVolume(0f).getOrThrow() }
            onMain { engine.play().getOrThrow() }
            runBlocking { withTimeout(TIMEOUT) { engine.state.first { it.status == PlaybackStatus.Playing } } }
            block(engine)
        } finally {
            onMain { engine.close() }
            scenario.close()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun item(asset: String, kind: MediaKind): MediaItem {
        val file = File(context.cacheDir, asset)
        instrumentation.context.assets.open("media/$asset").use { input -> file.outputStream().use(input::copyTo) }
        return MediaItem(MediaId(asset), file.toURI().toString(), asset, kind, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val POLL_MILLIS = 30L
        const val SETTLE_MILLIS = 600L
        const val STEPS = 5
        const val PLAY_MILLIS = 700L
        const val SETTLE_COLOUR_MILLIS = 7_000L
    }
}
