@file:Suppress(
    "ArgumentListWrapping", "Indentation", "MaximumLineLength", "MaxLineLength", "ParameterListWrapping", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.MediaId
import dev.vimivo.playback.PlaybackQueueSnapshot
import dev.vimivo.playback.QueueRepeatMode

internal data class StoredQueueItem(
    val queueId: Long,
    val mediaId: MediaId,
    val sourcePosition: Int,
    val playbackPosition: Int,
)

internal data class StoredPlaybackQueue(
    val items: List<StoredQueueItem>,
    val currentQueueId: Long?,
    val shuffleEnabled: Boolean,
    val repeatMode: QueueRepeatMode,
    val sourceLabel: String?,
)

/**
 * SQLite persistence for the temporary queue. It stores only stable indexed media references and occurrence
 * identities, never metadata copies or native player handles. Saves replace the queue in one transaction, so an
 * interrupted or failed save leaves the previous complete queue in place.
 */
internal class DesktopPlaybackQueueRepository(private val database: DesktopDatabase) {
    fun save(snapshot: PlaybackQueueSnapshot, now: Long = System.currentTimeMillis()): Unit = database.transaction { connection ->
        val sourceById = snapshot.sourceItems.withIndex().associate { (position, item) -> item.queueId to position }
        val playbackById = snapshot.playbackItems.withIndex().associate { (position, item) -> item.queueId to position }
        require(sourceById.size == snapshot.sourceItems.size && playbackById.size == snapshot.playbackItems.size) {
            "Queue contains duplicate occurrence identifiers"
        }
        require(sourceById.keys == playbackById.keys) { "Queue source and playback items differ" }
        connection.createStatement().use { it.executeUpdate("DELETE FROM playback_queue_items") }
        connection.prepareStatement(
            "INSERT INTO playback_queue_items(queue_id,media_id,source_position,playback_position) VALUES(?,?,?,?)",
        ).use { statement ->
            snapshot.sourceItems.forEach { item ->
                statement.setLong(1, item.queueId)
                statement.setString(2, item.media.id.value)
                statement.setInt(3, sourceById.getValue(item.queueId))
                statement.setInt(4, playbackById.getValue(item.queueId))
                statement.addBatch()
            }
            statement.executeBatch()
        }
        connection.prepareStatement(
            "INSERT INTO playback_queue_state(singleton,current_queue_id,shuffle_enabled,repeat_mode,source_label,updated_at_ms) VALUES(1,?,?,?,?,?) " +
                "ON CONFLICT(singleton) DO UPDATE SET current_queue_id=excluded.current_queue_id,shuffle_enabled=excluded.shuffle_enabled," +
                "repeat_mode=excluded.repeat_mode,source_label=excluded.source_label,updated_at_ms=excluded.updated_at_ms",
        ).use { statement ->
            val currentQueueId = snapshot.currentQueueId
            if (currentQueueId == null) statement.setNull(1, java.sql.Types.BIGINT) else statement.setLong(1, currentQueueId)
            statement.setInt(2, if (snapshot.shuffleEnabled) 1 else 0)
            statement.setString(3, snapshot.repeatMode.name)
            statement.setString(4, snapshot.sourceLabel)
            statement.setLong(5, now)
            statement.executeUpdate()
        }
    }

    fun load(): StoredPlaybackQueue? = database.transaction { connection ->
        val state = connection.createStatement().executeQuery(
            "SELECT current_queue_id,shuffle_enabled,repeat_mode,source_label FROM playback_queue_state WHERE singleton=1",
        ).use { rows ->
            if (!rows.next()) return@transaction null
            StoredState(rows.getLong(1).takeIf { !rows.wasNull() }, rows.getInt(2) != 0, rows.getString(3), rows.getString(4))
        }
        val items = connection.createStatement().executeQuery(
            "SELECT queue_id,media_id,source_position,playback_position FROM playback_queue_items ORDER BY playback_position",
        ).use { rows ->
            buildList {
                while (rows.next()) add(StoredQueueItem(rows.getLong(1), MediaId(rows.getString(2)), rows.getInt(3), rows.getInt(4)))
            }
        }
        if (items.isEmpty()) return@transaction null
        val repeatMode = QueueRepeatMode.entries.firstOrNull { it.name == state.repeatMode } ?: QueueRepeatMode.OFF
        StoredPlaybackQueue(items, state.currentQueueId, state.shuffleEnabled, repeatMode, state.sourceLabel)
    }

    /** Returns which ids are indexed media rows, regardless of current availability. */
    fun indexedMediaIds(ids: Collection<MediaId>): Set<MediaId> {
        if (ids.isEmpty()) return emptySet()
        return database.transaction { connection ->
            ids.distinct().chunked(MAX_SQL_PARAMETERS).flatMapTo(mutableSetOf()) { chunk ->
                connection.prepareStatement(
                    "SELECT id FROM media_items WHERE id IN (${chunk.joinToString(",") { "?" }})",
                ).use { statement ->
                    chunk.forEachIndexed { index, id -> statement.setString(index + 1, id.value) }
                    statement.executeQuery().use { rows -> buildList { while (rows.next()) add(MediaId(rows.getString(1))) } }
                }
            }
        }
    }

    fun clear(): Unit = database.transaction { connection ->
        connection.createStatement().use { it.executeUpdate("DELETE FROM playback_queue_items") }
        connection.createStatement().use { it.executeUpdate("DELETE FROM playback_queue_state") }
    }

    private data class StoredState(val currentQueueId: Long?, val shuffleEnabled: Boolean, val repeatMode: String?, val sourceLabel: String?)

    private companion object {
        const val MAX_SQL_PARAMETERS = 500
    }
}
