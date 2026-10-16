@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.RecommendationCategory
import dev.vimivo.core.model.RecommendationItem

internal class DesktopRecommendationEngine(private val database: DesktopDatabase) {

    fun isPersonalizationEnabled(): Boolean = database.transaction { connection ->
        connection.prepareStatement("SELECT value FROM personalization_preferences WHERE key='enabled'").use { statement ->
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1).toBoolean() else true }
        }
    }

    fun setPersonalizationEnabled(enabled: Boolean) = database.transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO personalization_preferences(key, value) VALUES('enabled', ?) " +
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        ).use { statement ->
            statement.setString(1, enabled.toString())
            statement.executeUpdate()
        }
    }

    fun dismissRecommendation(targetType: String, targetId: String, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT OR REPLACE INTO dismissed_recommendations(target_type, target_id, dismissed_at_ms) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setString(1, targetType)
                statement.setString(2, targetId)
                statement.setLong(3, now)
                statement.executeUpdate()
            }
        }

    fun dismissedIds(): Set<String> = database.transaction { connection ->
        connection.createStatement().executeQuery("SELECT target_id FROM dismissed_recommendations").use { rows ->
            buildSet { while (rows.next()) add(rows.getString(1)) }
        }
    }

    fun clearDismissed() = database.transaction { connection ->
        connection.createStatement().executeUpdate("DELETE FROM dismissed_recommendations")
    }

    fun generateRecommendations(
        allMedia: List<MediaItem>,
        history: List<PlaybackHistory>,
        positions: Map<String, ResumePosition>,
    ): List<RecommendationItem> {
        if (!isPersonalizationEnabled()) return emptyList()

        val dismissed = dismissedIds()
        val mediaById = allMedia.associateBy { it.id.value }
        val results = mutableListOf<RecommendationItem>()

        // 1. Continue Watching
        allMedia.filter { it.kind == MediaKind.VIDEO && it.id.value !in dismissed }.forEach { video ->
            val pos = positions[video.id.value]
            if (isEligibleForResume(pos, video.durationMillis, 10_000L)) {
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.CONTINUE_WATCHING,
                        media = video,
                        score = 100.0,
                        explanation = "Resume video from ${formatDuration(pos!!.positionMillis)}",
                    ),
                )
            }
        }

        // 2. Continue Listening (long-form audio or unfinished tracks)
        allMedia.filter { it.kind == MediaKind.AUDIO && it.id.value !in dismissed }.forEach { audio ->
            val pos = positions[audio.id.value]
            if (isEligibleForResume(pos, audio.durationMillis, 15_000L)) {
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.CONTINUE_LISTENING,
                        media = audio,
                        score = 90.0,
                        explanation = "Continue listening from ${formatDuration(pos!!.positionMillis)}",
                    ),
                )
            }
        }

        // 3. Because You Like [Top Artist]
        val topArtist = history.mapNotNull { mediaById[it.mediaId.value]?.artist }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        if (topArtist != null) {
            allMedia.filter {
                it.kind == MediaKind.AUDIO && it.artist.equals(topArtist, ignoreCase = true) && it.id.value !in dismissed
            }.take(5).forEach { item ->
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.BECAUSE_YOU_LIKE_ARTIST,
                        media = item,
                        score = 80.0,
                        explanation = "More by $topArtist, one of your most played artists",
                    ),
                )
            }
        }

        // 4. Rediscover Forgotten Favorites
        val playedRecentlyIds = history.filter {
            System.currentTimeMillis() - it.lastPlayedAtEpochMillis < 14L * 24 * 3600 * 1000
        }.map { it.mediaId.value }.toSet()

        allMedia.filter { it.isFavorite && it.id.value !in playedRecentlyIds && it.id.value !in dismissed }
            .take(5).forEach { item ->
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.REDISCOVER_FAVORITES,
                        media = item,
                        score = 70.0,
                        explanation = "A favorite track you haven't listened to in a while",
                    ),
                )
            }

        // 5. Recently Added
        allMedia.filter { it.dateAddedEpochSeconds != null && it.id.value !in dismissed }
            .sortedByDescending { it.dateAddedEpochSeconds }
            .take(5).forEach { item ->
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.RECENTLY_ADDED,
                        media = item,
                        score = 60.0,
                        explanation = "Recently added to your library",
                    ),
                )
            }

        // 6. Cold Start / Unplayed Gems
        if (history.isEmpty()) {
            allMedia.filter { !it.artist.isNullOrBlank() && it.id.value !in dismissed }.take(8).forEach { item ->
                results.add(
                    RecommendationItem(
                        category = RecommendationCategory.UNPLAYED_GEMS,
                        media = item,
                        score = 50.0,
                        explanation = "Explore tracks from your offline collection",
                    ),
                )
            }
        }

        return results.distinctBy { it.media.id }
    }

    private fun isEligibleForResume(pos: ResumePosition?, duration: Long?, minPositionMillis: Long): Boolean {
        if (pos == null || pos.completed || pos.positionMillis <= minPositionMillis) return false
        val total = pos.durationMillis ?: duration ?: return false
        return pos.positionMillis < total - 15_000L
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}
