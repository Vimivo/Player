package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test

/**
 * Live libmpv: closing a player while audio plays and the audio device list is in use, many times in a row.
 * Guards the shutdown order that keeps libmpv's CoreAudio device listeners from firing into a destroyed context
 * (docs/evidence/D-coreaudio-shutdown). A crash here takes the test JVM down. Skipped without libmpv or media.
 */
class MpvShutdownStressTest {
    private val track = File("../testmedia/track1_solaris.flac").absoluteFile

    @Test
    fun closingWhilePlayingWithTheDeviceListOpenNeverCrashes() {
        if (!track.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val iterations = System.getenv("VIMIVO_SHUTDOWN_STRESS")?.toIntOrNull() ?: DEFAULT_ITERATIONS
        val uri = track.toPath().toUri().toString()
        val media = MediaItem(MediaId(uri), uri, track.nameWithoutExtension, MediaKind.AUDIO, fileName = track.name)
        repeat(iterations) {
            val engine = MpvPlayerEngine()
            try {
                runBlocking {
                    engine.setVolume(0f)
                    engine.open(media, 0).getOrThrow()
                    engine.play().getOrThrow()
                    withTimeout(PLAY_TIMEOUT) { engine.state.first { (it.positionMillis) > 0 } }
                    engine.getAudioOutputDevices()
                }
            } finally {
                engine.close()
            }
        }
    }

    private companion object {
        const val DEFAULT_ITERATIONS = 5
        const val PLAY_TIMEOUT = 15_000L
    }
}
