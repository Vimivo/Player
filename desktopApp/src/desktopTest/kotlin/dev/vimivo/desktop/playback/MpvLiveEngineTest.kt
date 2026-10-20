package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.TrackOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real libmpv with the repository's test media. Skipped where libmpv is not installed
 * (for example Windows unit-test runs, which only get libmpv inside the packaged app).
 */
class MpvLiveEngineTest {
    private val media = File("../testmedia/track2_lunar.mp3").absoluteFile

    @Test
    fun amplifiesToTwoHundredPercentAndRejectsMore() = withLiveEngine { engine ->
        engine.setVolume(2f).getOrThrow()
        val amplified = awaitState(engine) { kotlin.math.abs(it.volume - 2f) < 0.01f }
        assertEquals(2f, amplified.volume, 0.01f)

        assertTrue(engine.setVolume(2.01f).isFailure)
    }

    @Test
    fun loadsSidecarSubtitlesBesideTheFileAndInSubsFolder() {
        val video = File("../testmedia/video_sample.mkv").absoluteFile
        val folder = Files.createTempDirectory("vimivo-subs").toFile().apply { deleteOnExit() }
        val movie = video.copyTo(File(folder, "movie.mkv"))
        File(folder, "movie.en.srt").writeText(SRT)
        File(folder, "Subs").mkdirs()
        File(folder, "Subs/movie.fr.srt").writeText(SRT)
        File(folder, "unrelated.srt").writeText(SRT)

        withLiveEngine(movie, MediaKind.VIDEO) { engine ->
            awaitState(engine) { it.externalSubtitleCount() >= 2 }
            delay(SETTLE_MILLIS)
            val external = engine.state.value.subtitleTracks.filter { it.origin == TrackOrigin.EXTERNAL }
            assertEquals(listOf("en", "fr"), external.mapNotNull { it.language }.sorted(), "got $external")

            val dropped = assertNotNull(MpvPlayerEngine.externalSubtitleFor(File(folder, "unrelated.srt")))
            engine.addExternalSubtitle(dropped).getOrThrow()
            awaitState(engine) { it.externalSubtitleCount() == EXPECTED_AFTER_DROP }
        }
    }

    /** Regression: files opened from the OS had no artist/album in Now Playing, SMTC or MPRIS. */
    @Test
    fun publishesEmbeddedTagsForFilesOpenedOutsideTheLibrary() = withLiveEngine { engine ->
        val tagged = awaitState(engine) { it.metadata.artist != null }
        assertEquals("Lunar Horizon", tagged.metadata.title)
        assertEquals("Calyx", tagged.metadata.artist)
        assertEquals("Atmospheric Explorations", tagged.metadata.album)
    }

    @Test
    fun recognisesOnlySubtitleFilesAsDroppable() {
        assertEquals("en", MpvPlayerEngine.externalSubtitleFor(File("Movie.en.srt"))?.language)
        assertNull(MpvPlayerEngine.externalSubtitleFor(File("Movie.mkv")))
    }

    private fun PlaybackState.externalSubtitleCount() = subtitleTracks.count { it.origin == TrackOrigin.EXTERNAL }

    private fun withLiveEngine(
        file: File = media,
        kind: MediaKind = MediaKind.AUDIO,
        block: suspend (MpvPlayerEngine) -> Unit,
    ) {
        if (!file.isFile || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: libmpv or test media unavailable")
            return
        }
        val engine = MpvPlayerEngine()
        try {
            runBlocking {
                val item = MediaItem(MediaId("live"), file.toPath().toUri().toString(), "live", kind)
                engine.open(item).getOrThrow()
                engine.setVolume(0f).getOrThrow()
                engine.play().getOrThrow()
                awaitState(engine) { it.durationMillis != null }
                block(engine)
            }
        } finally {
            engine.close()
        }
    }

    private suspend fun awaitState(engine: MpvPlayerEngine, predicate: (PlaybackState) -> Boolean): PlaybackState =
        withTimeout(TIMEOUT_MILLIS) { engine.state.first(predicate) }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 600L
        const val EXPECTED_AFTER_DROP = 3
        const val SRT = "1\n00:00:00,000 --> 00:00:04,000\nHello\n"
    }
}
