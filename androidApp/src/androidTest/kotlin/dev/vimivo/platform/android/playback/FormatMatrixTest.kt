package dev.vimivo.platform.android.playback

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File

/**
 * V1 #1 and #9 on Android: every checked-in container/codec sample plays through the same engine manager the app uses
 * (Media3 first, the libmpv fallback when Media3 refuses or cannot decode it) and its position advances. The engine
 * that played each file is logged (`adb logcat -s VimivoFormats`) so the matrix can say which engine covers what.
 */
@RunWith(AndroidJUnit4::class)
class FormatMatrixTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val settings: SettingsRepository = KoinJavaComponent.get(SettingsRepository::class.java)

    private data class Sample(val asset: String, val kind: MediaKind, val what: String)

    private val samples = listOf(
        Sample("track1_solaris.flac", MediaKind.AUDIO, "FLAC audio"),
        Sample("track2_lunar.mp3", MediaKind.AUDIO, "MP3 audio"),
        Sample("video_landscape.mp4", MediaKind.VIDEO, "MP4 H.264 + AAC"),
        Sample("video_portrait.mp4", MediaKind.VIDEO, "MP4 portrait"),
        Sample("video_sample.mkv", MediaKind.VIDEO, "MKV H.264 + subtitles"),
        Sample("letterbox_tiny.mp4", MediaKind.VIDEO, "MP4 letterboxed"),
        Sample("interlaced_tiny.ts", MediaKind.VIDEO, "MPEG-TS MPEG-2 interlaced"),
        Sample("hdr10_tiny.mkv", MediaKind.VIDEO, "MKV HEVC 10-bit HDR10 (PQ)"),
        Sample("hlg_tiny.mkv", MediaKind.VIDEO, "MKV HEVC 10-bit HLG"),
        Sample("multitrack_tiny.mkv", MediaKind.VIDEO, "MKV 2 video + 2 audio tracks"),
    )

    @Test
    fun everySamplePlaysAndAdvances() {
        val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
        val manager = PlaybackEngineManager(
            mapOf(
                EngineId.MEDIA3 to Media3PlayerEngineFactory(context),
                EngineId.MPV to LibmpvPlayerEngineFactory(context, settings),
            ),
            scope,
        )
        val failures = mutableListOf<String>()
        try {
            for (sample in samples) {
                val result = play(manager, scope, sample)
                Log.i(TAG, "${sample.asset} (${sample.what}) -> $result")
                if (!result.startsWith("OK")) failures += "${sample.asset}: $result"
            }
        } finally {
            instrumentation.runOnMainSync { manager.close() }
            scope.cancel()
        }
        assertTrue("these samples did not play: $failures", failures.isEmpty())
    }

    private fun play(manager: PlaybackEngineManager, scope: CoroutineScope, sample: Sample): String {
        val media = item(sample)
        val opened = scope.launch { manager.open(media) }
        runBlocking { opened.join() }
        instrumentation.runOnMainSync { manager.setVolume(0f) }
        instrumentation.runOnMainSync { manager.play() }
        return runBlocking {
            // An error from the first engine is not the end: the manager may be switching to the fallback engine.
            val progressed = runCatching {
                withTimeout(TIMEOUT) {
                    manager.state.first {
                        it.status == PlaybackStatus.Completed ||
                            (it.status == PlaybackStatus.Playing && it.positionMillis >= MIN_POSITION_MILLIS)
                    }
                }
            }.getOrNull()
            val engine = manager.activeEngine.value?.id
            val last = manager.state.value.status
            when {
                progressed != null -> "OK on $engine"
                last is PlaybackStatus.Error -> "FAILED: ${last.error.message}"
                else -> "FAILED: no progress ($engine, $last)"
            }
        }
    }

    private fun item(sample: Sample): MediaItem {
        val file = File(context.cacheDir, sample.asset)
        instrumentation.context.assets.open("media/${sample.asset}").use { input ->
            file.outputStream().use(input::copyTo)
        }
        val uri = file.toURI().toString()
        return MediaItem(MediaId(uri), uri, sample.asset, sample.kind, fileName = file.name)
    }

    private companion object {
        const val TAG = "VimivoFormats"
        const val TIMEOUT = 25_000L
        const val MIN_POSITION_MILLIS = 800L
    }
}
