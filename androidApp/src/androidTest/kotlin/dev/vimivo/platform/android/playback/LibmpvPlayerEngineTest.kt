package dev.vimivo.platform.android.playback

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.settings.SettingsRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent

/** The LGPL libmpv fallback engine (ADR-005) on a real device or emulator: audio, video into a Surface, errors. */
@RunWith(AndroidJUnit4::class)
class LibmpvPlayerEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings: SettingsRepository = KoinJavaComponent.get(SettingsRepository::class.java)

    @Before
    fun libmpvIsPackaged() = assumeTrue("libmpv is not in this APK", LibmpvNative.available)

    @Test
    fun audioPlaysAndItsPositionAdvances() = runBlocking {
        LibmpvPlayerEngine(context, settings).use { engine ->
            engine.open(item(asset("track2_lunar.mp3"), MediaKind.AUDIO)).getOrThrow()
            engine.setVolume(0f).getOrThrow()
            engine.play().getOrThrow()
            val playing = withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 500 } }
            assertEquals(PlaybackStatus.Playing, playing.status)
            assertTrue(playing.durationMillis!! > 5_000)
            assertEquals("mp3", playing.audioTracks.single().codec)
        }
    }

    @Test
    fun videoReportsTracksAndShowsAPictureInASurfaceView() = runBlocking {
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
        assertTrue("surface not created", created.await(TIMEOUT, TimeUnit.MILLISECONDS))
        try {
            LibmpvPlayerEngine(context, settings, File(context.cacheDir, "mpv-video.log")).use { engine ->
                engine.setVideoSurfaceHolder(view.holder)
                engine.open(item(asset("video_sample.mkv"), MediaKind.VIDEO)).getOrThrow()
                engine.setVolume(0f).getOrThrow()
                engine.play().getOrThrow()
                val state = withTimeout(TIMEOUT) { engine.state.first { it.positionMillis > 1_000 } }
                assertEquals("h264", state.videoTracks.single { it.isSelected }.codec)
                assertEquals(1280, state.videoTracks.single { it.isSelected }.width)
                assertEquals("eng", state.subtitleTracks.single().language)
                assertTrue("the SurfaceView shows no picture", litFraction(view) > MIN_LIT_FRACTION)
            }
        } finally {
            scenario.close()
        }
    }

    /** Share of non-black pixels in what the SurfaceView shows, read back with PixelCopy. */
    private fun litFraction(view: SurfaceView): Double {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        val thread = HandlerThread("pixel-copy").apply { start() }
        try {
            PixelCopy.request(view, bitmap, { code -> result = code; done.countDown() }, Handler(thread.looper))
            assertTrue(done.await(TIMEOUT, TimeUnit.MILLISECONDS))
        } finally {
            thread.quitSafely()
        }
        assertEquals(PixelCopy.SUCCESS, result)
        // The 16:9 video is letterboxed in the middle of a portrait view; measure that band only.
        val bandHeight = (bitmap.width * 9 / 16).coerceAtMost(bitmap.height)
        val top = (bitmap.height - bandHeight) / 2
        val pixels = IntArray(bitmap.width * bandHeight)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, top, bitmap.width, bandHeight)
        return pixels.count { (it and 0xFFFFFF) > 0x101010 }.toDouble() / pixels.size
    }

    @Test
    fun aDamagedFileEndsInAnError() = runBlocking {
        val broken = File(context.cacheDir, "broken.mkv").apply { writeBytes(ByteArray(4_096) { (it * 31).toByte() }) }
        LibmpvPlayerEngine(context, settings).use { engine ->
            engine.open(item(broken, MediaKind.VIDEO)).getOrThrow()
            engine.play().getOrThrow()
            val failed = withTimeout(TIMEOUT) { engine.state.first { it.status is PlaybackStatus.Error } }
            assertTrue((failed.status as PlaybackStatus.Error).error.message.startsWith("libmpv could not play"))
        }
    }

    private fun asset(name: String): File {
        val target = File(context.cacheDir, name)
        val testAssets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        testAssets.open("media/$name").use { input -> target.outputStream().use(input::copyTo) }
        return target
    }

    private fun item(file: File, kind: MediaKind): MediaItem {
        val uri = file.toURI().toString()
        return MediaItem(MediaId(uri), uri, file.nameWithoutExtension, kind, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT = 15_000L
        const val MIN_LIT_FRACTION = 0.3
    }
}
