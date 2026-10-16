@file:Suppress(
    "ArgumentListWrapping", "FunctionNaming", "LongMethod", "MaximumLineLength",
    "MaxLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.RecommendationCategory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopRecommendationInsightsTest {

    @Test
    fun generatesExplainableLocalRecommendationsAndSupportsDismissal() {
        val tempDir = Files.createTempDirectory("vimivo-recs-test")
        val dbPath = tempDir.resolve("vimivo.sqlite")

        DesktopDatabase.open(dbPath).use { database ->
            val engine = DesktopRecommendationEngine(database)

            val track1 = MediaItem(
                id = MediaId("song-1"),
                uri = "file:///song1.mp3",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                kind = MediaKind.AUDIO,
            )
            val track2 = MediaItem(
                id = MediaId("song-2"),
                uri = "file:///song2.mp3",
                title = "Don't Stop Me Now",
                artist = "Queen",
                kind = MediaKind.AUDIO,
            )
            val video1 = MediaItem(
                id = MediaId("video-1"),
                uri = "file:///movie.mp4",
                title = "Documentary",
                durationMillis = 3600_000L,
                kind = MediaKind.VIDEO,
            )

            val allMedia = listOf(track1, track2, video1)

            // Cold start test
            val coldRecs = engine.generateRecommendations(allMedia, emptyList(), emptyMap())
            assertTrue(coldRecs.isNotEmpty())
            assertTrue(coldRecs.any { it.category == RecommendationCategory.UNPLAYED_GEMS })

            // With playback history and resume position
            val history = listOf(
                PlaybackHistory(
                    mediaId = MediaId("song-1"),
                    positionMillis = 0L,
                    lastPlayedAtEpochMillis = System.currentTimeMillis(),
                    playCount = 15,
                    completed = true,
                ),
            )
            val positions = mapOf(
                "video-1" to ResumePosition(
                    mediaId = MediaId("video-1"),
                    positionMillis = 1800_000L,
                    durationMillis = 3600_000L,
                    completed = false,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )

            val recommendations = engine.generateRecommendations(allMedia, history, positions)

            // Continue Watching for video-1
            val continueWatching = recommendations.find { it.category == RecommendationCategory.CONTINUE_WATCHING }
            assertTrue(continueWatching != null)
            assertEquals(MediaId("video-1"), continueWatching.media.id)

            // Top Artist recommendation for Queen
            val artistRec = recommendations.find { it.category == RecommendationCategory.BECAUSE_YOU_LIKE_ARTIST }
            assertTrue(artistRec != null)
            assertEquals("Queen", artistRec.media.artist)

            // Dismiss video-1
            engine.dismissRecommendation("MEDIA", "video-1")
            val recsAfterDismiss = engine.generateRecommendations(allMedia, history, positions)
            assertFalse(recsAfterDismiss.any { it.media.id == MediaId("video-1") })

            // Disable personalization entirely
            engine.setPersonalizationEnabled(false)
            val emptyRecs = engine.generateRecommendations(allMedia, history, positions)
            assertTrue(emptyRecs.isEmpty())
        }
    }

    @Test
    fun calculatesAccurateListeningAndWatchingInsights() {
        val calculator = DesktopInsightsCalculator()

        val audio1 = MediaItem(
            id = MediaId("a-1"),
            uri = "file:///a1.mp3",
            title = "Track One",
            artist = "Pink Floyd",
            album = "The Wall",
            durationMillis = 200_000L,
            kind = MediaKind.AUDIO,
        )
        val audio2 = MediaItem(
            id = MediaId("a-2"),
            uri = "file:///a2.mp3",
            title = "Track Two",
            artist = "Pink Floyd",
            album = "The Wall",
            durationMillis = 300_000L,
            kind = MediaKind.AUDIO,
        )
        val audioUnplayed = MediaItem(
            id = MediaId("a-3"),
            uri = "file:///a3.mp3",
            title = "Track Three",
            artist = "Led Zeppelin",
            kind = MediaKind.AUDIO,
        )
        val video = MediaItem(
            id = MediaId("v-1"),
            uri = "file:///v1.mp4",
            title = "Concert",
            durationMillis = 5000_000L,
            kind = MediaKind.VIDEO,
        )

        val media = listOf(audio1, audio2, audioUnplayed, video)

        val history = listOf(
            PlaybackHistory(
                mediaId = MediaId("a-1"),
                positionMillis = 0L,
                lastPlayedAtEpochMillis = 1000L,
                playCount = 4,
                completed = true,
            ),
            PlaybackHistory(
                mediaId = MediaId("a-2"),
                positionMillis = 0L,
                lastPlayedAtEpochMillis = 2000L,
                playCount = 2,
                completed = true,
            ),
            PlaybackHistory(
                mediaId = MediaId("v-1"),
                positionMillis = 0L,
                lastPlayedAtEpochMillis = 3000L,
                playCount = 1,
                completed = false,
            ),
        )

        val positions = mapOf(
            "v-1" to ResumePosition(
                mediaId = MediaId("v-1"),
                positionMillis = 2500_000L,
                durationMillis = 5000_000L,
                completed = false,
                updatedAtMillis = 3000L,
            ),
        )

        val listeningInsights = calculator.calculateListeningInsights(media, history)
        assertEquals(6L, listeningInsights.totalTracksPlayed) // 4 + 2
        // Duration: 4 * 200_000 + 2 * 300_000 = 800_000 + 600_000 = 1_400_000L
        assertEquals(1_400_000L, listeningInsights.totalListeningDurationMillis)
        assertEquals("Pink Floyd", listeningInsights.topArtists[0].first)
        assertEquals("The Wall", listeningInsights.topAlbums[0].first)
        assertEquals(1, listeningInsights.unplayedTracksCount) // audioUnplayed

        val watchingInsights = calculator.calculateWatchingInsights(media, history, positions)
        assertEquals(1L, watchingInsights.totalVideosPlayed)
        assertEquals(1, watchingInsights.continueWatchingCount) // v-1 is in progress
        assertEquals(0, watchingInsights.completedVideosCount)
    }
}
