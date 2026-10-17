@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MagicNumber", "LongMethod",
)

package dev.vimivo.desktop.reliability

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LongSessionStabilityTest {

    @Test
    fun testLongSessionEngineLifecycleAndStateTransitions() = runBlocking {
        val engine = MpvPlayerEngine()
        val tempFiles = mutableListOf<File>()

        try {
            // Create dummy test audio fixtures
            for (i in 1..5) {
                val file = File.createTempFile("vimivo_session_$i", ".mp3")
                file.writeBytes(ByteArray(1024) { (it % 256).toByte() })
                tempFiles.add(file)
            }

            // Simulate multi-track playback session
            val iterations = 25
            for (step in 1..iterations) {
                val targetFile = tempFiles[(step - 1) % tempFiles.size]
                val media = MediaItem(
                    id = MediaId("session-track-$step"),
                    uri = targetFile.toURI().toString(),
                    title = "Session Track $step",
                    kind = MediaKind.AUDIO,
                )

                val openResult = engine.open(media, startPositionMillis = (step * 500L))
                assertTrue(openResult.isSuccess, "Engine open must succeed for iteration $step: ${openResult.exceptionOrNull()?.message}")

                // Query state
                val currentState = engine.state.value
                assertEquals(media.id, currentState.media?.id)

                // Seek and volume adjustments
                engine.seekTo(1000L + step * 100L)
                engine.setVolume(0.5f + (step % 5) * 0.1f)

                if (step % 2 == 0) {
                    engine.pause()
                } else {
                    engine.play()
                }

                // Short sleep to allow polling loop execution
                delay(20)
            }

            // Check that the engine state remains coherent
            val finalState = engine.state.value
            assertNotNull(finalState.media)
            assertTrue(finalState.volume in 0f..1f)

            // Test clean engine shutdown
            engine.close()
            val closedState = engine.state.value
            assertEquals(PlaybackStatus.Idle, closedState.status)
        } finally {
            engine.close()
            tempFiles.forEach { it.delete() }
        }
    }

    @Test
    fun testLongSessionRapidSourceSwitchingSafety() = runBlocking {
        val engine = MpvPlayerEngine()
        try {
            // Rapid switching across 20 media targets without awaiting completion
            for (i in 1..20) {
                val item = MediaItem(
                    id = MediaId("rapid-switch-$i"),
                    uri = "file:///tmp/dummy_switch_$i.mp3",
                    title = "Rapid Switch $i",
                    kind = MediaKind.AUDIO,
                )
                val result = engine.open(item, 0L)
                assertTrue(result.isSuccess)
            }

            // Engine should reflect the latest opened media
            val state = engine.state.value
            assertEquals(MediaId("rapid-switch-20"), state.media?.id)
        } finally {
            engine.close()
        }
    }
}
