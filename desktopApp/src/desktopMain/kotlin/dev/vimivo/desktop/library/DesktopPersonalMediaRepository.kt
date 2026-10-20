@file:Suppress(
    "ArgumentListWrapping", "Indentation", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping",
    "TooManyFunctions", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaPlaybackPreference
import dev.vimivo.core.model.PlaybackHistory
import dev.vimivo.core.model.Playlist
import java.util.UUID

internal enum class FavoriteTarget { MEDIA, PLAYLIST, STREAM }

internal data class ResumePosition(
    val mediaId: MediaId,
    val positionMillis: Long,
    val durationMillis: Long?,
    val completed: Boolean,
    val updatedAtMillis: Long,
)

/** Authoritative SQLite store for user-created desktop media data. */
internal class DesktopPersonalMediaRepository(private val database: DesktopDatabase) {
    fun containsMedia(mediaId: MediaId): Boolean = database.transaction { connection ->
        connection.prepareStatement("SELECT 1 FROM media_items WHERE id=? AND available=1").use { statement ->
            statement.setString(1, mediaId.value)
            statement.executeQuery().use { it.next() }
        }
    }

    fun createPlaylist(name: String, now: Long = System.currentTimeMillis()): Playlist {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty" }
        val id = UUID.randomUUID().toString()
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO playlists(id,name,created_at_ms,updated_at_ms) VALUES(?,?,?,?)").use { statement ->
                statement.setString(1, id); statement.setString(2, normalized); statement.setLong(3, now); statement.setLong(4, now)
                statement.executeUpdate()
            }
        }
        return Playlist(id, normalized, emptyList(), now)
    }

    fun renamePlaylist(id: String, name: String, now: Long = System.currentTimeMillis()) {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Playlist name cannot be empty" }
        database.transaction { connection ->
            connection.prepareStatement("UPDATE playlists SET name=?,updated_at_ms=? WHERE id=?").use { statement ->
                statement.setString(1, normalized); statement.setLong(2, now); statement.setString(3, id)
                require(statement.executeUpdate() == 1) { "Playlist was not found" }
            }
        }
    }

    fun deletePlaylist(id: String) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM playlists WHERE id=?").use { statement ->
            statement.setString(1, id); require(statement.executeUpdate() == 1) { "Playlist was not found" }
        }
    }

    fun addToPlaylist(playlistId: String, mediaId: MediaId, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            val position = connection.prepareStatement("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_items WHERE playlist_id=?").use { query ->
                query.setString(1, playlistId); query.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
            connection.prepareStatement("INSERT OR IGNORE INTO playlist_items(playlist_id,media_id,position,added_at_ms) VALUES(?,?,?,?)").use { statement ->
                statement.setString(1, playlistId); statement.setString(2, mediaId.value); statement.setInt(3, position); statement.setLong(4, now)
                statement.executeUpdate()
            }
            touchPlaylist(connection, playlistId, now)
        }

    fun addMultipleToPlaylist(playlistId: String, mediaIds: Collection<MediaId>, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            var position = connection.prepareStatement("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_items WHERE playlist_id=?").use { query ->
                query.setString(1, playlistId); query.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
            connection.prepareStatement("INSERT OR IGNORE INTO playlist_items(playlist_id,media_id,position,added_at_ms) VALUES(?,?,?,?)").use { statement ->
                mediaIds.forEach { id ->
                    statement.setString(1, playlistId); statement.setString(2, id.value); statement.setInt(3, position++); statement.setLong(4, now)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            touchPlaylist(connection, playlistId, now)
        }

    fun removeFromPlaylist(playlistId: String, mediaId: MediaId, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM playlist_items WHERE playlist_id=? AND media_id=?").use { statement ->
                statement.setString(1, playlistId); statement.setString(2, mediaId.value); statement.executeUpdate()
            }
            normalizePlaylistPositions(connection, playlistId)
            touchPlaylist(connection, playlistId, now)
        }

    fun removeMultipleFromPlaylist(playlistId: String, mediaIds: Collection<MediaId>, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM playlist_items WHERE playlist_id=? AND media_id=?").use { statement ->
                mediaIds.forEach { id ->
                    statement.setString(1, playlistId); statement.setString(2, id.value); statement.addBatch()
                }
                statement.executeBatch()
            }
            normalizePlaylistPositions(connection, playlistId)
            touchPlaylist(connection, playlistId, now)
        }

    fun movePlaylistItem(playlistId: String, mediaId: MediaId, targetPosition: Int, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            val ids = playlistMediaIds(connection, playlistId).toMutableList()
            require(targetPosition in ids.indices) { "Target playlist position is invalid" }
            require(ids.remove(mediaId.value)) { "Playlist item was not found" }
            ids.add(targetPosition, mediaId.value)
            connection.prepareStatement("UPDATE playlist_items SET position=position+1000000 WHERE playlist_id=?").use { statement ->
                statement.setString(1, playlistId); statement.executeUpdate()
            }
            connection.prepareStatement("UPDATE playlist_items SET position=? WHERE playlist_id=? AND media_id=?").use { statement ->
                ids.forEachIndexed { index, id ->
                    statement.setInt(1, index); statement.setString(2, playlistId); statement.setString(3, id); statement.addBatch()
                }
                statement.executeBatch()
            }
            touchPlaylist(connection, playlistId, now)
        }

    fun playlists(): List<Playlist> = database.transaction { connection ->
        connection.createStatement().executeQuery("SELECT id,name,updated_at_ms FROM playlists ORDER BY name COLLATE NOCASE").use { rows ->
            buildList {
                while (rows.next()) add(Playlist(rows.getString(1), rows.getString(2), playlistMediaIds(connection, rows.getString(1)).map(::MediaId), rows.getLong(3)))
            }
        }
    }

    fun duplicatePlaylist(id: String, name: String? = null, now: Long = System.currentTimeMillis()): Playlist {
        val original = playlists().firstOrNull { it.id == id } ?: error("Playlist was not found")
        val duplicate = createPlaylist(name ?: "${original.name} copy", now)
        original.items.forEach { addToPlaylist(duplicate.id, it, now) }
        return playlists().first { it.id == duplicate.id }
    }

    fun setFavorite(type: FavoriteTarget, id: String, favorite: Boolean, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            val sql = if (favorite) "INSERT OR IGNORE INTO favorites(target_type,target_id,created_at_ms) VALUES(?,?,?)" else "DELETE FROM favorites WHERE target_type=? AND target_id=?"
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, type.name); statement.setString(2, id)
                if (favorite) statement.setLong(3, now)
                statement.executeUpdate()
            }
        }

    fun setFavoritesBulk(type: FavoriteTarget, ids: Collection<String>, favorite: Boolean, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            val sql = if (favorite) "INSERT OR IGNORE INTO favorites(target_type,target_id,created_at_ms) VALUES(?,?,?)" else "DELETE FROM favorites WHERE target_type=? AND target_id=?"
            connection.prepareStatement(sql).use { statement ->
                ids.forEach { id ->
                    statement.setString(1, type.name); statement.setString(2, id)
                    if (favorite) statement.setLong(3, now)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }

    fun favoriteIds(type: FavoriteTarget): Set<String> = database.transaction { connection ->
        connection.prepareStatement("SELECT target_id FROM favorites WHERE target_type=? ORDER BY created_at_ms DESC").use { statement ->
            statement.setString(1, type.name)
            statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    fun recordPlayback(mediaId: MediaId, positionMillis: Long, completed: Boolean, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO playback_history(media_id,last_played_ms,play_count,recent_position_ms,completed,last_completed_ms) VALUES(?,?,1,?,?,?) " +
                    "ON CONFLICT(media_id) DO UPDATE SET last_played_ms=excluded.last_played_ms,play_count=playback_history.play_count+1," +
                    "recent_position_ms=excluded.recent_position_ms,completed=excluded.completed,last_completed_ms=excluded.last_completed_ms",
            ).use { statement ->
                statement.setString(1, mediaId.value); statement.setLong(2, now); statement.setLong(3, positionMillis)
                statement.setInt(4, if (completed) 1 else 0)
                if (completed) statement.setLong(5, now) else statement.setNull(5, java.sql.Types.BIGINT)
                statement.executeUpdate()
            }
        }

    fun markPlaybackCompleted(mediaId: MediaId, positionMillis: Long, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement(
                "UPDATE playback_history SET last_played_ms=?,recent_position_ms=?,completed=1,last_completed_ms=? WHERE media_id=?",
            ).use { statement ->
                statement.setLong(1, now); statement.setLong(2, positionMillis); statement.setLong(3, now)
                statement.setString(4, mediaId.value); statement.executeUpdate()
            }
        }

    fun history(limit: Int = 100): List<PlaybackHistory> = database.transaction { connection ->
        connection.prepareStatement("SELECT media_id,recent_position_ms,last_played_ms,play_count,completed FROM playback_history ORDER BY last_played_ms DESC LIMIT ?").use { statement ->
            statement.setInt(1, limit.coerceIn(1, 500)); statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(PlaybackHistory(MediaId(rows.getString(1)), rows.getLong(2), rows.getLong(3), rows.getInt(4), rows.getInt(5) != 0)) }
            }
        }
    }

    fun removeHistory(mediaId: MediaId) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM playback_history WHERE media_id=?").use { statement ->
            statement.setString(1, mediaId.value); statement.executeUpdate()
        }
    }

    fun clearHistory() = database.transaction { connection ->
        connection.createStatement().executeUpdate("DELETE FROM playback_history")
    }

    fun savePosition(mediaId: MediaId, positionMillis: Long, durationMillis: Long?, completed: Boolean, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO playback_positions(media_id,position_ms,duration_ms,completed,updated_at_ms) VALUES(?,?,?,?,?) " +
                    "ON CONFLICT(media_id) DO UPDATE SET " +
                    "position_ms=CASE WHEN excluded.position_ms > 0 OR excluded.completed = 1 THEN excluded.position_ms ELSE playback_positions.position_ms END, " +
                    "duration_ms=COALESCE(excluded.duration_ms, playback_positions.duration_ms), " +
                    "completed=excluded.completed, " +
                    "updated_at_ms=excluded.updated_at_ms",
            ).use { statement ->
                statement.setString(1, mediaId.value); statement.setLong(2, positionMillis.coerceAtLeast(0))
                if (durationMillis == null) statement.setNull(3, java.sql.Types.BIGINT) else statement.setLong(3, durationMillis)
                statement.setInt(4, if (completed) 1 else 0); statement.setLong(5, now); statement.executeUpdate()
            }
        }

    fun position(mediaId: MediaId): ResumePosition? = database.transaction { connection ->
        connection.prepareStatement("SELECT position_ms,duration_ms,completed,updated_at_ms FROM playback_positions WHERE media_id=?").use { statement ->
            statement.setString(1, mediaId.value); statement.executeQuery().use { rows ->
                if (!rows.next()) null else ResumePosition(mediaId, rows.getLong(1), rows.getLong(2).takeIf { !rows.wasNull() }, rows.getInt(3) != 0, rows.getLong(4))
            }
        }
    }

    /** Resume position for a file opened outside the library, keyed by its URI. */
    fun saveExternalPosition(uri: String, positionMillis: Long, durationMillis: Long?, completed: Boolean, now: Long = System.currentTimeMillis()) =
        database.transaction { connection ->
            connection.prepareStatement(
                "INSERT INTO external_playback_positions(uri,position_ms,duration_ms,completed,updated_at_ms) VALUES(?,?,?,?,?) " +
                    "ON CONFLICT(uri) DO UPDATE SET position_ms=excluded.position_ms, " +
                    "duration_ms=COALESCE(excluded.duration_ms, external_playback_positions.duration_ms), " +
                    "completed=excluded.completed, updated_at_ms=excluded.updated_at_ms",
            ).use { statement ->
                statement.setString(1, uri)
                statement.setLong(2, positionMillis.coerceAtLeast(0))
                if (durationMillis != null) statement.setLong(3, durationMillis) else statement.setNull(3, java.sql.Types.INTEGER)
                statement.setInt(4, if (completed) 1 else 0)
                statement.setLong(5, now)
                statement.executeUpdate()
            }
        }

    fun externalPosition(uri: String): ResumePosition? = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT position_ms,duration_ms,completed,updated_at_ms FROM external_playback_positions WHERE uri=?",
        ).use { statement ->
            statement.setString(1, uri)
            statement.executeQuery().use { rows ->
                if (!rows.next()) {
                    null
                } else {
                    val duration = rows.getLong(2).takeIf { !rows.wasNull() }
                    ResumePosition(MediaId(uri), rows.getLong(1), duration, rows.getInt(3) != 0, rows.getLong(4))
                }
            }
        }
    }

    fun allPositions(): Map<String, ResumePosition> = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT media_id,position_ms,duration_ms,completed,updated_at_ms FROM playback_positions",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        val idStr = rows.getString(1)
                        put(
                            idStr,
                            ResumePosition(
                                mediaId = MediaId(idStr),
                                positionMillis = rows.getLong(2),
                                durationMillis = rows.getLong(3).takeIf { !rows.wasNull() },
                                completed = rows.getInt(4) != 0,
                                updatedAtMillis = rows.getLong(5),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun addBookmark(mediaId: MediaId, positionMillis: Long, label: String?, now: Long = System.currentTimeMillis()): Bookmark {
        require(positionMillis >= 0) { "Bookmark position cannot be negative" }
        val bookmark = Bookmark(UUID.randomUUID().toString(), mediaId, label?.trim().orEmpty(), positionMillis, now)
        database.transaction { connection ->
            connection.prepareStatement("INSERT INTO media_bookmarks(id,media_id,position_ms,label,created_at_ms) VALUES(?,?,?,?,?)").use { statement ->
                statement.setString(1, bookmark.id); statement.setString(2, mediaId.value); statement.setLong(3, positionMillis)
                statement.setString(4, label?.trim()); statement.setLong(5, now); statement.executeUpdate()
            }
        }
        return bookmark
    }

    fun bookmarks(mediaId: MediaId): List<Bookmark> = database.transaction { connection ->
        connection.prepareStatement("SELECT id,position_ms,label,created_at_ms FROM media_bookmarks WHERE media_id=? ORDER BY position_ms").use { statement ->
            statement.setString(1, mediaId.value); statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(Bookmark(rows.getString(1), mediaId, rows.getString(3).orEmpty(), rows.getLong(2), rows.getLong(4))) }
            }
        }
    }

    fun allBookmarks(): List<Bookmark> = database.transaction { connection ->
        connection.createStatement().executeQuery(
            "SELECT id,media_id,position_ms,label,created_at_ms FROM media_bookmarks ORDER BY created_at_ms DESC",
        ).use { rows ->
            buildList {
                while (rows.next()) add(Bookmark(rows.getString(1), MediaId(rows.getString(2)), rows.getString(4).orEmpty(), rows.getLong(3), rows.getLong(5)))
            }
        }
    }

    fun renameBookmark(id: String, label: String) = database.transaction { connection ->
        connection.prepareStatement("UPDATE media_bookmarks SET label=? WHERE id=?").use { statement ->
            statement.setString(1, label.trim()); statement.setString(2, id)
            require(statement.executeUpdate() == 1) { "Bookmark was not found" }
        }
    }

    fun deleteBookmark(id: String) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM media_bookmarks WHERE id=?").use { statement ->
            statement.setString(1, id); require(statement.executeUpdate() == 1) { "Bookmark was not found" }
        }
    }

    fun putSetting(key: String, value: String) = database.transaction { connection ->
        connection.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").use { statement ->
            statement.setString(1, key); statement.setString(2, value); statement.executeUpdate()
        }
    }

    fun setting(key: String): String? = database.transaction { connection ->
        connection.prepareStatement("SELECT value FROM settings WHERE key=?").use { statement ->
            statement.setString(1, key); statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    fun clearSettings() = database.transaction { connection ->
        connection.createStatement().use { it.executeUpdate("DELETE FROM settings") }
    }

    fun saveMediaPreference(pref: MediaPlaybackPreference) = database.transaction { connection ->
        val jsonStr = buildString {
            append("{")
            append("\"speed\":").append(pref.speed).append(",")
            append("\"subtitleTrackId\":").append(pref.subtitleTrackId?.let { "\"$it\"" } ?: "null").append(",")
            append("\"subtitleDelayMillis\":").append(pref.subtitleDelayMillis).append(",")
            append("\"audioTrackId\":").append(pref.audioTrackId?.let { "\"$it\"" } ?: "null").append(",")
            append("\"audioSyncDelayMillis\":").append(pref.audioSyncDelayMillis).append(",")
            append("\"aspectRatio\":").append(pref.aspectRatio?.let { "\"$it\"" } ?: "null").append(",")
            append("\"resumeEligible\":").append(pref.resumeEligible).append(",")
            append("\"updatedAtMillis\":").append(pref.updatedAtMillis)
            append("}")
        }
        val key = "media_pref:${pref.mediaId.value}"
        connection.prepareStatement(
            "INSERT INTO personalization_preferences(key, value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
        ).use { statement ->
            statement.setString(1, key); statement.setString(2, jsonStr); statement.executeUpdate()
        }
    }

    fun mediaPreference(mediaId: MediaId): MediaPlaybackPreference? = database.transaction { connection ->
        val key = "media_pref:${mediaId.value}"
        connection.prepareStatement("SELECT value FROM personalization_preferences WHERE key=?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else parseMediaPreference(mediaId, rows.getString(1))
            }
        }
    }

    fun clearMediaPreference(mediaId: MediaId) = database.transaction { connection ->
        val key = "media_pref:${mediaId.value}"
        connection.prepareStatement("DELETE FROM personalization_preferences WHERE key=?").use { statement ->
            statement.setString(1, key); statement.executeUpdate()
        }
    }

    private fun parseMediaPreference(mediaId: MediaId, raw: String): MediaPlaybackPreference? = runCatching {
        val speed = Regex("\"speed\":([0-9.]+)").find(raw)?.groupValues?.get(1)?.toFloatOrNull()
        val subTrack = Regex("\"subtitleTrackId\":\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
        val subDelay = Regex("\"subtitleDelayMillis\":(-?[0-9]+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()
        val audioTrack = Regex("\"audioTrackId\":\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
        val audioDelay = Regex("\"audioSyncDelayMillis\":(-?[0-9]+)").find(raw)?.groupValues?.get(1)?.toLongOrNull()
        val aspect = Regex("\"aspectRatio\":\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
        val resumeEligible = Regex("\"resumeEligible\":(true|false)").find(raw)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true
        val updated = Regex("\"updatedAtMillis\":([0-9]+)").find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        MediaPlaybackPreference(
            mediaId = mediaId,
            speed = speed,
            subtitleTrackId = subTrack,
            subtitleDelayMillis = subDelay,
            audioTrackId = audioTrack,
            audioSyncDelayMillis = audioDelay,
            aspectRatio = aspect,
            resumeEligible = resumeEligible,
            updatedAtMillis = updated,
        )
    }.getOrNull()

    private fun playlistMediaIds(connection: java.sql.Connection, playlistId: String): List<String> =
        connection.prepareStatement("SELECT media_id FROM playlist_items WHERE playlist_id=? ORDER BY position").use { statement ->
            statement.setString(1, playlistId); statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }

    private fun normalizePlaylistPositions(connection: java.sql.Connection, playlistId: String) {
        val ids = playlistMediaIds(connection, playlistId)
        connection.prepareStatement("UPDATE playlist_items SET position=position+1000000 WHERE playlist_id=?").use { statement ->
            statement.setString(1, playlistId); statement.executeUpdate()
        }
        connection.prepareStatement("UPDATE playlist_items SET position=? WHERE playlist_id=? AND media_id=?").use { statement ->
            ids.forEachIndexed { position, id ->
                statement.setInt(1, position); statement.setString(2, playlistId); statement.setString(3, id); statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun touchPlaylist(connection: java.sql.Connection, playlistId: String, now: Long) {
        connection.prepareStatement("UPDATE playlists SET updated_at_ms=? WHERE id=?").use { statement ->
            statement.setLong(1, now); statement.setString(2, playlistId); require(statement.executeUpdate() == 1) { "Playlist was not found" }
        }
    }
}
