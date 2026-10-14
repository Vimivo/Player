@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "PropertyWrapping")

package dev.vimivo.desktop.library

import dev.vimivo.core.model.ExtractedMediaMetadata
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem

/** Persists verified metadata and avoids reprocessing unchanged unsupported files. */
internal class DesktopMetadataRepository(private val database: DesktopDatabase) {
    fun candidates(rootId: String): List<MediaItem> = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT m.id,m.uri,m.title,m.kind,m.file_name,m.size_bytes,m.mime_type,m.modified_ms FROM media_items m " +
                "LEFT JOIN media_metadata x ON x.media_id=m.id WHERE m.root_id=? AND m.available=1 AND " +
                "(x.media_id IS NULL OR x.extracted_size_bytes IS NOT m.size_bytes OR x.extracted_modified_ms IS NOT m.modified_ms) ORDER BY m.indexed_ms LIMIT 200",
        ).use { statement ->
            statement.setString(1, rootId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(
                        MediaItem(
                            id = MediaId(rows.getString(1)), uri = rows.getString(2), title = rows.getString(3),
                            kind = dev.vimivo.core.model.MediaKind.valueOf(rows.getString(4)), fileName = rows.getString(5),
                            sizeBytes = rows.getLong(6).takeIf { !rows.wasNull() }, mimeType = rows.getString(7),
                            dateModifiedEpochSeconds = rows.getLong(8).takeIf { !rows.wasNull() }?.div(1_000),
                        ),
                    )
                }
            }
        }
    }

    fun save(media: MediaItem, metadata: ExtractedMediaMetadata, error: String? = null) = database.transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO media_metadata(media_id,title,artist,album,album_artist,genre,track_number,disc_number,release_year,duration_ms,bitrate,sample_rate,channels,video_width,video_height,frame_rate,video_codec,audio_codec,container,audio_track_count,subtitle_track_count,extraction_status,extraction_error,extracted_size_bytes,extracted_modified_ms,updated_at_ms) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(media_id) DO UPDATE SET title=excluded.title,artist=excluded.artist,album=excluded.album,album_artist=excluded.album_artist,genre=excluded.genre,track_number=excluded.track_number,disc_number=excluded.disc_number,release_year=excluded.release_year,duration_ms=excluded.duration_ms,bitrate=excluded.bitrate,sample_rate=excluded.sample_rate,channels=excluded.channels,video_width=excluded.video_width,video_height=excluded.video_height,frame_rate=excluded.frame_rate,video_codec=excluded.video_codec,audio_codec=excluded.audio_codec,container=excluded.container,audio_track_count=excluded.audio_track_count,subtitle_track_count=excluded.subtitle_track_count,extraction_status=excluded.extraction_status,extraction_error=excluded.extraction_error,extracted_size_bytes=excluded.extracted_size_bytes,extracted_modified_ms=excluded.extracted_modified_ms,updated_at_ms=excluded.updated_at_ms",
        ).use { statement ->
            val values = listOf<Any?>(media.id.value, metadata.title, metadata.artist, metadata.album, metadata.albumArtist, metadata.genre, metadata.trackNumber, metadata.discNumber, metadata.releaseYear, metadata.durationMillis, metadata.bitrate, metadata.sampleRate, metadata.channels, metadata.width, metadata.height, metadata.frameRate, metadata.videoCodec, metadata.audioCodec, metadata.container, metadata.audioTrackCount, metadata.subtitleTrackCount, if (error == null) "SUCCESS" else "FAILED", error, media.sizeBytes, media.dateModifiedEpochSeconds?.times(1_000), System.currentTimeMillis())
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }
}
