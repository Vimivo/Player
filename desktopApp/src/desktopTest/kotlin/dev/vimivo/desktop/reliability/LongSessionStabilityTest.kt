@file:Suppress(
    "ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "MagicNumber", "LongMethod",
    "StringLiteralDuplication",
)

package dev.vimivo.desktop.reliability

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.streaming.LiveLatencyMode
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
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

    @Test
    fun testRepeatedRapidSeekAndAudioSessionStress() = runBlocking {
        val engine = MpvPlayerEngine()
        val tempFile = File.createTempFile("vimivo_seek_stress", ".mp3")
        try {
            tempFile.writeBytes(ByteArray(2048) { (it % 256).toByte() })
            val media = MediaItem(
                id = MediaId("seek-stress-track"),
                uri = tempFile.toURI().toString(),
                title = "Seek Stress Track",
                kind = MediaKind.AUDIO,
            )
            val openRes = engine.open(media, 0L)
            assertTrue(openRes.isSuccess)

            // Perform 50 rapid seeks across varied positions
            for (i in 1..50) {
                val seekPos = (i * 350L) % 180_000L
                val seekRes = engine.seekTo(seekPos, exact = (i % 2 == 0))
                assertTrue(seekRes.isSuccess, "Seek $i to $seekPos ms must succeed")
            }

            // Assert engine is intact and responsive
            val state = engine.state.value
            assertEquals(media.id, state.media?.id)
        } finally {
            engine.close()
            tempFile.delete()
        }
    }

    @Test
    fun testStreamingLiveLatencyConfigurationLifecycle() = runBlocking {
        val engine = MpvPlayerEngine()
        try {
            val streamMedia = MediaItem(
                id = MediaId("stream-radio-1"),
                uri = "http://stream.example.com/live.aac",
                title = "Internet Radio Test",
                kind = MediaKind.STREAM,
            )

            // Test latency modes
            LiveLatencyMode.entries.forEach { mode ->
                val latencyRes = engine.setLiveLatencyMode(mode)
                assertTrue(latencyRes.isSuccess, "Setting latency mode $mode must succeed")
                assertEquals(mode, engine.liveLatencyMode)
            }

            // Open stream media with low-latency mode configured
            engine.setLiveLatencyMode(LiveLatencyMode.LOW_LATENCY)
            val openRes = engine.open(streamMedia, 0L)
            assertTrue(openRes.isSuccess)
            assertEquals(LiveLatencyMode.LOW_LATENCY, engine.liveLatencyMode)

            // Stop during streaming
            val stopRes = engine.stop()
            assertTrue(stopRes.isSuccess)
            assertEquals(PlaybackStatus.Idle, engine.state.value.status)
        } finally {
            engine.close()
        }
    }

    @Test
    fun testConcurrentLibraryDatabaseWritesDuringPlaybackSafety() = runBlocking {
        val engine = MpvPlayerEngine()
        val tempDir = Files.createTempDirectory("vimivo-concurrent-bench")
        val dbPath = tempDir.resolve("concurrent.sqlite")
        val tempAudio = File.createTempFile("vimivo_concurrent_audio", ".mp3")

        try {
            tempAudio.writeBytes(ByteArray(1024) { 0 })
            val media = MediaItem(
                id = MediaId("playing-track"),
                uri = tempAudio.toURI().toString(),
                title = "Playing Track",
                kind = MediaKind.AUDIO,
            )
            assertTrue(engine.open(media, 0L).isSuccess)
            engine.play()

            // Concurrently perform 500 database writes while engine polling is active
            DesktopDatabase.open(dbPath).use { database ->
                val writeJob = async(Dispatchers.IO) {
                    database.transaction { conn ->
                        conn.createStatement().execute(
                            "INSERT INTO library_roots VALUES ('root-c', 'file:///c', 'Root C', 1000, 1000, 1)",
                        )
                        val stmt = conn.prepareStatement(
                            "INSERT INTO media_items(id,root_id,uri,file_name,title,kind,mime_type,size_bytes,modified_ms,indexed_ms,available) " +
                                "VALUES(?,?,?,?,?,?,?,?,?,?,1)",
                        )
                        for (i in 1..500) {
                            stmt.setString(1, "c-track-$i")
                            stmt.setString(2, "root-c")
                            stmt.setString(3, "file:///c/track_$i.mp3")
                            stmt.setString(4, "track_$i.mp3")
                            stmt.setString(5, "Track $i")
                            stmt.setString(6, "AUDIO")
                            stmt.setString(7, "audio/mpeg")
                            stmt.setLong(8, 2048)
                            stmt.setLong(9, 1000)
                            stmt.setLong(10, 1000)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }

                writeJob.await()
            }

            // Verify playback engine state is completely unaffected
            val finalState = engine.state.value
            assertEquals(media.id, finalState.media?.id)
        } finally {
            engine.close()
            tempAudio.delete()
        }
    }
}
