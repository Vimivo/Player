package dev.vimivo.desktop.player

import dev.vimivo.core.model.MediaKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchPlannerTest {
    private val folder = Files.createTempDirectory("vimivo-plan").toFile().apply { deleteOnExit() }

    private fun touch(name: String) = File(folder, name).apply {
        parentFile.mkdirs()
        writeText("x")
    }

    @Test
    fun singleFileBringsNaturallySortedSiblingsOfTheSameKind() {
        val names = listOf("Show ep10.mkv", "Show ep2.mkv", "Show ep1.mp4", "cover.jpg", "theme.mp3", "Show ep2.srt")
        names.forEach(::touch)

        val plan = LaunchPlanner.plan(listOf(File(folder, "Show ep2.mkv")), includeFolderSiblings = true)!!

        assertEquals(listOf("Show ep1.mp4", "Show ep2.mkv", "Show ep10.mkv"), plan.items.map { it.fileName })
        assertEquals(1, plan.startIndex)
        assertEquals(PlayerPresentation.VIDEO, plan.presentation)
    }

    @Test
    fun siblingsCanBeTurnedOffAndAudioOpensNowPlaying() {
        listOf("a.flac", "b.flac").forEach(::touch)

        val plan = LaunchPlanner.plan(listOf(File(folder, "b.flac")), includeFolderSiblings = false)!!

        assertEquals(listOf("b.flac"), plan.items.map { it.fileName })
        assertEquals(PlayerPresentation.AUDIO, plan.presentation)
    }

    @Test
    fun multipleFilesKeepTheirOrderAndSubtitlesAreNotQueued() {
        val files = listOf(touch("z.mp3"), touch("a.mp3"), touch("a.srt"))

        val plan = LaunchPlanner.plan(files, includeFolderSiblings = true)!!

        assertEquals(listOf("z.mp3", "a.mp3"), plan.items.map { it.fileName })
    }

    @Test
    fun playlistsResolveRelativeAndAbsoluteEntriesSkipMissingAndKeepStreams() {
        val absolute = touch("music/one.mp3")
        touch("music/two.flac")
        val playlist = File(folder, "mix.m3u8").apply {
            val lines = listOf("#EXTM3U", "#EXTINF:10,First", absolute.absolutePath, "music/two.flac", "missing.mp3")
            writeText((lines + "https://example.test/live.m3u8").joinToString("\n"))
        }

        val plan = LaunchPlanner.plan(listOf(playlist), includeFolderSiblings = true)!!

        assertEquals(listOf("First", "two", "https://example.test/live.m3u8"), plan.items.map { it.title })
        assertEquals(MediaKind.STREAM, plan.items.last().kind)
    }

    @Test
    fun cueSheetsPlayAsOneItemAndNothingPlayableYieldsNoPlan() {
        val cue = touch("album.cue")
        val cuePlan = LaunchPlanner.plan(listOf(cue), includeFolderSiblings = true)!!
        assertEquals(listOf(cue.toPath().toUri().toString()), cuePlan.items.map { it.uri })
        assertNull(LaunchPlanner.plan(listOf(touch("only.srt")), true))
    }

    @Test
    fun initialPresentationComesFromTheFirstMediaFileWithoutReadingIt() {
        val subtitleThenVideo = listOf(File("/nope/movie.srt"), File("/nope/movie.mkv"))
        assertEquals(PlayerPresentation.VIDEO, LaunchPlanner.initialPresentation(subtitleThenVideo))
        assertEquals(PlayerPresentation.AUDIO, LaunchPlanner.initialPresentation(listOf(File("/nope/song.flac"))))
        assertEquals(PlayerPresentation.AUDIO, LaunchPlanner.initialPresentation(listOf(File("/nope/mix.m3u"))))
        assertNull(LaunchPlanner.initialPresentation(listOf(File("/nope/notes.txt"))))
        assertNull(LaunchPlanner.initialPresentation(emptyList()))
    }
}
