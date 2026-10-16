@file:Suppress(
    "ArgumentListWrapping", "CyclomaticComplexMethod", "Indentation", "LongMethod",
    "LongParameterList", "MaximumLineLength", "MaxLineLength", "MultiLineIfElse", "ParameterListWrapping",
    "TooGenericExceptionCaught", "Wrapping",
)

package dev.vimivo.desktop.library

import dev.vimivo.core.model.FieldAction
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MetadataChangeset
import dev.vimivo.core.model.MetadataEditItemPlan
import dev.vimivo.core.model.MetadataWriteMode
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class MetadataOverride(
    val mediaId: MediaId,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
)

internal class DesktopMetadataEditorService(private val database: DesktopDatabase) {

    fun isFormatSupportedForEmbeddedWrite(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in WRITABLE_AUDIO_EXTENSIONS
    }

    fun saveOverride(
        mediaId: MediaId,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        genre: String? = null,
        releaseYear: Int? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
        now: Long = System.currentTimeMillis(),
    ) = database.transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO user_metadata_overrides(media_id, title, artist, album, album_artist, genre, release_year, track_number, disc_number, updated_at_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(media_id) DO UPDATE SET title=excluded.title, artist=excluded.artist, album=excluded.album, " +
                "album_artist=excluded.album_artist, genre=excluded.genre, release_year=excluded.release_year, " +
                "track_number=excluded.track_number, disc_number=excluded.disc_number, updated_at_ms=excluded.updated_at_ms",
        ).use { statement ->
            statement.setString(1, mediaId.value)
            statement.setString(2, title)
            statement.setString(3, artist)
            statement.setString(4, album)
            statement.setString(5, albumArtist)
            statement.setString(6, genre)
            if (releaseYear == null) statement.setNull(7, java.sql.Types.INTEGER) else statement.setInt(7, releaseYear)
            if (trackNumber == null) statement.setNull(8, java.sql.Types.INTEGER) else statement.setInt(8, trackNumber)
            if (discNumber == null) statement.setNull(9, java.sql.Types.INTEGER) else statement.setInt(9, discNumber)
            statement.setLong(10, now)
            statement.executeUpdate()
        }
    }

    fun getOverride(mediaId: MediaId): MetadataOverride? = database.transaction { connection ->
        connection.prepareStatement(
            "SELECT title, artist, album, album_artist, genre, release_year, track_number, disc_number " +
                "FROM user_metadata_overrides WHERE media_id=?",
        ).use { statement ->
            statement.setString(1, mediaId.value)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    MetadataOverride(
                        mediaId = mediaId,
                        title = rows.getString(1),
                        artist = rows.getString(2),
                        album = rows.getString(3),
                        albumArtist = rows.getString(4),
                        genre = rows.getString(5),
                        releaseYear = rows.getInt(6).takeIf { !rows.wasNull() },
                        trackNumber = rows.getInt(7).takeIf { !rows.wasNull() },
                        discNumber = rows.getInt(8).takeIf { !rows.wasNull() },
                    )
                } else null
            }
        }
    }

    fun clearOverride(mediaId: MediaId) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM user_metadata_overrides WHERE media_id=?").use { statement ->
            statement.setString(1, mediaId.value)
            statement.executeUpdate()
        }
    }

    fun saveArtworkOverride(
        mediaId: MediaId,
        artworkUri: String,
        now: Long = System.currentTimeMillis(),
    ) = database.transaction { connection ->
        connection.prepareStatement(
            "INSERT INTO user_artwork_overrides(media_id, artwork_uri, updated_at_ms) VALUES(?, ?, ?) " +
                "ON CONFLICT(media_id) DO UPDATE SET artwork_uri=excluded.artwork_uri, updated_at_ms=excluded.updated_at_ms",
        ).use { statement ->
            statement.setString(1, mediaId.value)
            statement.setString(2, artworkUri)
            statement.setLong(3, now)
            statement.executeUpdate()
        }
    }

    fun getArtworkOverride(mediaId: MediaId): String? = database.transaction { connection ->
        connection.prepareStatement("SELECT artwork_uri FROM user_artwork_overrides WHERE media_id=?").use { statement ->
            statement.setString(1, mediaId.value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    fun clearArtworkOverride(mediaId: MediaId) = database.transaction { connection ->
        connection.prepareStatement("DELETE FROM user_artwork_overrides WHERE media_id=?").use { statement ->
            statement.setString(1, mediaId.value)
            statement.executeUpdate()
        }
    }

    fun planEdits(
        items: List<MediaItem>,
        changeset: MetadataChangeset,
        mode: MetadataWriteMode,
    ): List<MetadataEditItemPlan> = items.map { item ->
        val file = runCatching { File(URI(item.uri)) }.getOrNull()
        val exists = file != null && file.isFile
        val isSupported = exists && isFormatSupportedForEmbeddedWrite(file!!)
        val isWritable = exists && file!!.canWrite()
        val statusMessage = when {
            !exists -> "Source file unavailable"
            mode == MetadataWriteMode.EMBEDDED_FILE_TAG && !isSupported -> "Format not supported for embedded writing; use override"
            mode == MetadataWriteMode.EMBEDDED_FILE_TAG && !isWritable -> "File is read-only"
            else -> "Ready"
        }
        MetadataEditItemPlan(
            mediaId = item.id,
            originalUri = item.uri,
            originalTitle = item.title,
            originalArtist = item.artist,
            originalAlbum = item.album,
            originalAlbumArtist = item.albumArtist,
            originalGenre = item.genre,
            originalYear = item.releaseYear,
            originalTrackNumber = item.trackNumber,
            originalDiscNumber = item.discNumber,
            proposedChanges = changeset,
            writeMode = mode,
            isSupportedFormat = isSupported,
            isWritable = isWritable,
            statusMessage = statusMessage,
        )
    }

    fun applyEmbeddedTagWrite(
        file: File,
        changeset: MetadataChangeset,
        backupDir: Path? = null,
    ): Result<Unit> = runCatching {
        require(file.isFile && file.canWrite()) { "Target file is not writable: ${file.name}" }
        require(isFormatSupportedForEmbeddedWrite(file)) { "Format not supported for embedded writing: ${file.extension}" }

        // Create backup if directory provided
        val backupPath = backupDir?.let { dir ->
            Files.createDirectories(dir)
            val backup = dir.resolve("${file.nameWithoutExtension}_backup_${System.currentTimeMillis()}.${file.extension}")
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING)
            backup
        }

        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            applyFieldAction(tag, FieldKey.TITLE, changeset.title)
            applyFieldAction(tag, FieldKey.ARTIST, changeset.artist)
            applyFieldAction(tag, FieldKey.ALBUM, changeset.album)
            applyFieldAction(tag, FieldKey.ALBUM_ARTIST, changeset.albumArtist)
            applyFieldAction(tag, FieldKey.GENRE, changeset.genre)

            when (val year = changeset.releaseYear) {
                is FieldAction.Set -> tag.setField(FieldKey.YEAR, year.value.toString())
                is FieldAction.Clear -> tag.deleteField(FieldKey.YEAR)
                FieldAction.Unchanged -> Unit
            }
            when (val track = changeset.trackNumber) {
                is FieldAction.Set -> tag.setField(FieldKey.TRACK, track.value.toString())
                is FieldAction.Clear -> tag.deleteField(FieldKey.TRACK)
                FieldAction.Unchanged -> Unit
            }
            when (val disc = changeset.discNumber) {
                is FieldAction.Set -> tag.setField(FieldKey.DISC_NO, disc.value.toString())
                is FieldAction.Clear -> tag.deleteField(FieldKey.DISC_NO)
                FieldAction.Unchanged -> Unit
            }

            audioFile.commit()

            // Post-write verification: Reopen and read file
            val verified = AudioFileIO.read(file)
            require(verified.audioHeader.trackLength >= 0) { "Post-write verification failed: audio header unreadable" }
        } catch (error: Throwable) {
            // Restore from backup on failure if backup was created
            if (backupPath != null && Files.exists(backupPath)) {
                Files.copy(backupPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            throw error
        }
    }

    fun applyEmbeddedArtworkWrite(
        file: File,
        artworkImageFile: File,
        backupDir: Path? = null,
    ): Result<Unit> = runCatching {
        require(file.isFile && file.canWrite()) { "Target audio file is not writable: ${file.name}" }
        require(artworkImageFile.isFile && artworkImageFile.canRead()) { "Artwork image file cannot be read: ${artworkImageFile.name}" }
        require(isFormatSupportedForEmbeddedWrite(file)) { "Format not supported for embedded writing: ${file.extension}" }

        val backupPath = backupDir?.let { dir ->
            Files.createDirectories(dir)
            val backup = dir.resolve("${file.nameWithoutExtension}_art_backup_${System.currentTimeMillis()}.${file.extension}")
            Files.copy(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING)
            backup
        }

        try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            val artwork = ArtworkFactory.createArtworkFromFile(artworkImageFile)
            tag.deleteArtworkField()
            tag.setField(artwork)
            audioFile.commit()

            // Post-write verification
            val verified = AudioFileIO.read(file)
            require(verified.tag?.firstArtwork != null) { "Post-write artwork verification failed" }
        } catch (error: Throwable) {
            if (backupPath != null && Files.exists(backupPath)) {
                Files.copy(backupPath, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            throw error
        }
    }

    private fun applyFieldAction(tag: Tag, key: FieldKey, action: FieldAction<String>) {
        when (action) {
            is FieldAction.Set -> tag.setField(key, action.value)
            is FieldAction.Clear -> tag.deleteField(key)
            FieldAction.Unchanged -> Unit
        }
    }

    companion object {
        val WRITABLE_AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "m4a", "wav")
    }
}
