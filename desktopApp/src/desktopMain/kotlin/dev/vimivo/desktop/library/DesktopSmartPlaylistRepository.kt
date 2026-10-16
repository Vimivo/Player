@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "ImportOrdering", "Indentation", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.SmartPlaylistDefinition
import dev.vimivo.core.model.SmartRuleCondition
import dev.vimivo.core.model.SmartRuleField
import dev.vimivo.core.model.SmartRuleMatchMode
import dev.vimivo.core.model.SmartRuleOperator
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopSmartPlaylistRepository(private val database: DesktopDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    fun createSmartPlaylist(
        name: String,
        conditions: List<SmartRuleCondition>,
        matchMode: SmartRuleMatchMode = SmartRuleMatchMode.ALL,
        sortField: SmartRuleField = SmartRuleField.TITLE,
        sortDescending: Boolean = false,
        limit: Int? = null,
        now: Long = System.currentTimeMillis(),
    ): SmartPlaylistDefinition {
        val id = UUID.randomUUID().toString()
        val definition = SmartPlaylistDefinition(
            id = id,
            name = name.trim(),
            conditions = conditions,
            matchMode = matchMode,
            sortField = sortField,
            sortDescending = sortDescending,
            limit = limit,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO smart_playlists(id, name, rules_json, sort_field, sort_descending, item_limit, created_at_ms, updated_at_ms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, definition.id)
                statement.setString(2, definition.name)
                statement.setString(3, json.encodeToString(definition.conditions))
                statement.setString(4, definition.sortField.name)
                statement.setInt(5, if (definition.sortDescending) 1 else 0)
                val lim = definition.limit
                if (lim == null) statement.setNull(6, java.sql.Types.INTEGER) else statement.setInt(6, lim)
                statement.setLong(7, now)
                statement.setLong(8, now)
                statement.executeUpdate()
            }
        }
        return definition
    }

    fun smartPlaylists(): List<SmartPlaylistDefinition> = database.transaction { connection ->
        connection.createStatement().executeQuery(
            "SELECT id, name, rules_json, sort_field, sort_descending, item_limit, created_at_ms, updated_at_ms " +
                "FROM smart_playlists ORDER BY name COLLATE NOCASE",
        ).use { rows ->
            buildList {
                while (rows.next()) {
                    val rawJson = rows.getString(3)
                    val conditions: List<SmartRuleCondition> = runCatching {
                        json.decodeFromString<List<SmartRuleCondition>>(rawJson)
                    }.getOrDefault(emptyList())
                    add(
                        SmartPlaylistDefinition(
                            id = rows.getString(1),
                            name = rows.getString(2),
                            conditions = conditions,
                            matchMode = SmartRuleMatchMode.ALL,
                            sortField = runCatching { SmartRuleField.valueOf(rows.getString(4)) }.getOrDefault(SmartRuleField.TITLE),
                            sortDescending = rows.getInt(5) != 0,
                            limit = rows.getInt(6).takeIf { !rows.wasNull() },
                            createdAtEpochMillis = rows.getLong(7),
                            updatedAtEpochMillis = rows.getLong(8),
                        ),
                    )
                }
            }
        }
    }

    fun deleteSmartPlaylist(id: String) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM smart_playlists WHERE id=?").use { statement ->
            statement.setString(1, id)
            statement.executeUpdate()
        }
    }

    fun evaluate(definition: SmartPlaylistDefinition, items: List<MediaItem>): List<MediaItem> {
        val filtered = items.filter { item ->
            if (definition.conditions.isEmpty()) return@filter true
            when (definition.matchMode) {
                SmartRuleMatchMode.ALL -> definition.conditions.all { matches(item, it) }
                SmartRuleMatchMode.ANY -> definition.conditions.any { matches(item, it) }
            }
        }

        val sorted = when (definition.sortField) {
            SmartRuleField.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SmartRuleField.ARTIST -> filtered.sortedBy { it.artist.orEmpty().lowercase() }
            SmartRuleField.ALBUM -> filtered.sortedBy { it.album.orEmpty().lowercase() }
            SmartRuleField.ALBUM_ARTIST -> filtered.sortedBy { (it.albumArtist ?: it.artist).orEmpty().lowercase() }
            SmartRuleField.GENRE -> filtered.sortedBy { it.genre.orEmpty().lowercase() }
            SmartRuleField.DURATION_MS -> filtered.sortedBy { it.durationMillis ?: 0L }
            SmartRuleField.PLAY_COUNT -> filtered.sortedBy { 0 }
            SmartRuleField.IS_FAVORITE -> filtered.sortedBy { if (it.isFavorite) 0 else 1 }
            SmartRuleField.MEDIA_KIND -> filtered.sortedBy { it.kind.name }
            SmartRuleField.RELEASE_YEAR -> filtered.sortedBy { it.releaseYear ?: 0 }
            SmartRuleField.DATE_ADDED -> filtered.sortedBy { it.dateAddedEpochSeconds ?: 0L }
        }

        val directed = if (definition.sortDescending) sorted.reversed() else sorted
        val lim = definition.limit
        return if (lim != null && lim > 0) directed.take(lim) else directed
    }

    private fun matches(item: MediaItem, condition: SmartRuleCondition): Boolean {
        val expected = condition.value.trim()
        return when (condition.field) {
            SmartRuleField.TITLE -> matchString(item.title, condition.operator, expected)
            SmartRuleField.ARTIST -> matchString(item.artist.orEmpty(), condition.operator, expected)
            SmartRuleField.ALBUM -> matchString(item.album.orEmpty(), condition.operator, expected)
            SmartRuleField.ALBUM_ARTIST -> matchString((item.albumArtist ?: item.artist).orEmpty(), condition.operator, expected)
            SmartRuleField.GENRE -> matchString(item.genre.orEmpty(), condition.operator, expected)
            SmartRuleField.DURATION_MS -> matchNumber(item.durationMillis ?: 0L, condition.operator, expected.toLongOrNull() ?: 0L)
            SmartRuleField.PLAY_COUNT -> true
            SmartRuleField.IS_FAVORITE -> when (condition.operator) {
                SmartRuleOperator.IS_TRUE -> item.isFavorite
                SmartRuleOperator.IS_FALSE -> !item.isFavorite
                else -> item.isFavorite == expected.toBoolean()
            }
            SmartRuleField.MEDIA_KIND -> item.kind.name.equals(expected, ignoreCase = true)
            SmartRuleField.RELEASE_YEAR -> matchNumber((item.releaseYear ?: 0).toLong(), condition.operator, expected.toLongOrNull() ?: 0L)
            SmartRuleField.DATE_ADDED -> matchNumber(item.dateAddedEpochSeconds ?: 0L, condition.operator, expected.toLongOrNull() ?: 0L)
        }
    }

    private fun matchString(actual: String, operator: SmartRuleOperator, expected: String): Boolean = when (operator) {
        SmartRuleOperator.EQUALS -> actual.equals(expected, ignoreCase = true)
        SmartRuleOperator.CONTAINS -> actual.contains(expected, ignoreCase = true)
        SmartRuleOperator.STARTS_WITH -> actual.startsWith(expected, ignoreCase = true)
        else -> actual.contains(expected, ignoreCase = true)
    }

    private fun matchNumber(actual: Long, operator: SmartRuleOperator, expected: Long): Boolean = when (operator) {
        SmartRuleOperator.EQUALS -> actual == expected
        SmartRuleOperator.GREATER_THAN -> actual > expected
        SmartRuleOperator.LESS_THAN -> actual < expected
        else -> actual == expected
    }

    companion object {
        fun builtInCollections(): List<SmartPlaylistDefinition> = listOf(
            SmartPlaylistDefinition(
                id = "builtin-favorites",
                name = "Favorites",
                conditions = listOf(SmartRuleCondition(SmartRuleField.IS_FAVORITE, SmartRuleOperator.IS_TRUE)),
                sortField = SmartRuleField.TITLE,
            ),
            SmartPlaylistDefinition(
                id = "builtin-long-form",
                name = "Long-Form Audio",
                conditions = listOf(
                    SmartRuleCondition(SmartRuleField.MEDIA_KIND, SmartRuleOperator.EQUALS, MediaKind.AUDIO.name),
                    SmartRuleCondition(SmartRuleField.DURATION_MS, SmartRuleOperator.GREATER_THAN, "1200000"), // > 20 mins
                ),
                sortField = SmartRuleField.DURATION_MS,
                sortDescending = true,
            ),
            SmartPlaylistDefinition(
                id = "builtin-short-tracks",
                name = "Short Tracks",
                conditions = listOf(
                    SmartRuleCondition(SmartRuleField.MEDIA_KIND, SmartRuleOperator.EQUALS, MediaKind.AUDIO.name),
                    SmartRuleCondition(SmartRuleField.DURATION_MS, SmartRuleOperator.LESS_THAN, "180000"), // < 3 mins
                ),
                sortField = SmartRuleField.DURATION_MS,
            ),
        )
    }
}
