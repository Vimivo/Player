package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression for V0 audit B2 with real libmpv: choosing "Fill" (libmpv rejected the old value) or any other failing
 * control must not turn into a playback error, and the queue must not skip the file. Skipped without libmpv.
 */
class MpvControlFailureTest {
    private val videos = listOf("video_sample.mkv", "video_landscape.mp4").map { File("../testmedia/$it").absoluteFile }

    // Ten-second audio: headless video without a render surface reaches end of file early on macOS.
    private val songs = listOf("track2_lunar.mp3", "track1_solaris.flac").map { File("../testmedia/$it").absoluteFile }

    @Test
    fun everyAspectPresetIsAcceptedByLibmpv() = withQueue(videos, MediaKind.VIDEO) { engine, _ ->
        VideoAspectRatio.entries.forEach { ratio ->
            val result = engine.applyVideoAdjustments(VideoAdjustments(aspectRatio = ratio))
            assertTrue(result.isSuccess, "$ratio failed: ${result.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun failedControlKeepsPlayingTheSameFile() = withQueue(songs, MediaKind.AUDIO) { engine, queue ->
        assertTrue(engine.setPlaybackSpeed(UNSUPPORTED_SPEED).isFailure)
        delay(SETTLE_MILLIS)
        val state = engine.state.value
        assertNotEquals(PlaybackStatus.Error::class, state.status::class, "status became ${state.status}")
        assertEquals("track2_lunar.mp3", state.media?.fileName)
        assertEquals(0, queue.state.value.currentIndex)
    }

    private fun withQueue(
        files: List<File>,
        kind: MediaKind,
        block: suspend (MpvPlayerEngine, PlaybackQueueController) -> Unit,
    ) {
        if (files.any { !it.isFile } || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val engine = MpvPlayerEngine()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = PlaybackQueueController(engine, scope)
        try {
            runBlocking {
                queue.play(files.map { item(it, kind) }, startIndex = 0).getOrThrow()
                withTimeout(TIMEOUT_MILLIS) { engine.state.first { it.durationMillis != null } }
                engine.setVolume(0f).getOrThrow()
                block(engine, queue)
            }
        } finally {
            queue.close()
            engine.close()
            scope.cancel()
        }
    }

    private fun item(file: File, kind: MediaKind): MediaItem {
        val uri = file.toPath().toUri().toString()
        return MediaItem(MediaId(uri), uri, file.nameWithoutExtension, kind, fileName = file.name)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 1_500L
        const val UNSUPPORTED_SPEED = 10f
    }
}
