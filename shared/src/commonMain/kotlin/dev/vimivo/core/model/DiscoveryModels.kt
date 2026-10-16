package dev.vimivo.core.model

import kotlinx.serialization.Serializable

enum class DuplicateConfidence {
    EXACT_CONTENT_MATCH,
    SAME_FILE_ALIAS,
    POSSIBLE_DUPLICATE,
}

data class DuplicateGroup(
    val id: String,
    val confidence: DuplicateConfidence,
    val items: List<MediaItem>,
    val reason: String,
)

enum class RecommendationCategory {
    CONTINUE_LISTENING,
    CONTINUE_WATCHING,
    BECAUSE_YOU_LIKE_ARTIST,
    MORE_FROM_ALBUM,
    REDISCOVER_FAVORITES,
    RECENTLY_ADDED,
    UNPLAYED_GEMS,
    GENRE_HIGHLIGHT,
}

data class RecommendationItem(
    val category: RecommendationCategory,
    val media: MediaItem,
    val score: Double,
    val explanation: String,
)

data class ListeningInsights(
    val totalListeningDurationMillis: Long,
    val totalTracksPlayed: Long,
    val topArtists: List<Pair<String, Int>>,
    val topAlbums: List<Pair<String, Int>>,
    val topTracks: List<Pair<MediaItem, Int>>,
    val unplayedTracksCount: Int,
)

data class WatchingInsights(
    val totalWatchingDurationMillis: Long,
    val totalVideosPlayed: Long,
    val continueWatchingCount: Int,
    val completedVideosCount: Int,
)

@Serializable
data class PersonalizationSettings(
    val enabled: Boolean = true,
    val showInsights: Boolean = true,
    val showHomeRecommendations: Boolean = true,
)
