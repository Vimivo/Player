@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ListeningInsights
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.WatchingInsights

internal class DesktopInsightsCalculator {

    fun calculateListeningInsights(
        allMedia: List<MediaItem>,
        history: List<PlaybackHistory>,
    ): ListeningInsights {
        val audioMedia = allMedia.filter { it.kind == MediaKind.AUDIO }
        val audioById = audioMedia.associateBy { it.id.value }

        val audioHistory = history.filter { it.mediaId.value in audioById }
        val totalPlays = audioHistory.sumOf { it.playCount.toLong() }

        // Listening duration estimate based on playCount * durationMillis or actual playback
        val totalDurationMillis = audioHistory.sumOf { entry ->
            val duration = audioById[entry.mediaId.value]?.durationMillis ?: 0L
            duration * entry.playCount
        }

        val topArtists = audioHistory.mapNotNull { entry -> audioById[entry.mediaId.value]?.artist }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)

        val topAlbums = audioHistory.mapNotNull { entry -> audioById[entry.mediaId.value]?.album }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)

        val topTracks = audioHistory.mapNotNull { entry ->
            audioById[entry.mediaId.value]?.let { it to entry.playCount }
        }.sortedByDescending { it.second }.take(5)

        val playedIds = history.map { it.mediaId.value }.toSet()
        val unplayedCount = audioMedia.count { it.id.value !in playedIds }

        return ListeningInsights(
            totalListeningDurationMillis = totalDurationMillis,
            totalTracksPlayed = totalPlays,
            topArtists = topArtists,
            topAlbums = topAlbums,
            topTracks = topTracks,
            unplayedTracksCount = unplayedCount,
        )
    }

    fun calculateWatchingInsights(
        allMedia: List<MediaItem>,
        history: List<PlaybackHistory>,
        positions: Map<String, ResumePosition>,
    ): WatchingInsights {
        val videoMedia = allMedia.filter { it.kind == MediaKind.VIDEO }
        val videoById = videoMedia.associateBy { it.id.value }

        val videoHistory = history.filter { it.mediaId.value in videoById }
        val totalVideosPlayed = videoHistory.sumOf { it.playCount.toLong() }
        val completedCount = videoHistory.count { it.completed }

        val totalDurationMillis = videoHistory.sumOf { entry ->
            val duration = videoById[entry.mediaId.value]?.durationMillis ?: 0L
            duration * entry.playCount
        }

        val inProgressCount = videoMedia.count { video ->
            val pos = positions[video.id.value]
            pos != null && !pos.completed && pos.positionMillis > 10_000L
        }

        return WatchingInsights(
            totalWatchingDurationMillis = totalDurationMillis,
            totalVideosPlayed = totalVideosPlayed,
            continueWatchingCount = inProgressCount,
            completedVideosCount = completedCount,
        )
    }
}
