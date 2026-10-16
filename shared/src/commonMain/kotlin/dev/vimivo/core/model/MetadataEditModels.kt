package dev.vimivo.core.model

import kotlinx.serialization.Serializable

enum class MetadataWriteMode {
    DATABASE_OVERRIDE,
    EMBEDDED_FILE_TAG,
}

enum class EditField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    GENRE,
    YEAR,
    TRACK_NUMBER,
    DISC_NUMBER,
}

@Serializable
sealed class FieldAction<out T> {
    @Serializable
    data object Unchanged : FieldAction<Nothing>()

    @Serializable
    data object Clear : FieldAction<Nothing>()

    @Serializable
    data class Set<T>(val value: T) : FieldAction<T>()
}

@Serializable
data class MetadataChangeset(
    val title: FieldAction<String> = FieldAction.Unchanged,
    val artist: FieldAction<String> = FieldAction.Unchanged,
    val album: FieldAction<String> = FieldAction.Unchanged,
    val albumArtist: FieldAction<String> = FieldAction.Unchanged,
    val genre: FieldAction<String> = FieldAction.Unchanged,
    val releaseYear: FieldAction<Int> = FieldAction.Unchanged,
    val trackNumber: FieldAction<Int> = FieldAction.Unchanged,
    val discNumber: FieldAction<Int> = FieldAction.Unchanged,
)

data class MetadataEditItemPlan(
    val mediaId: MediaId,
    val originalUri: String,
    val originalTitle: String,
    val originalArtist: String?,
    val originalAlbum: String?,
    val originalAlbumArtist: String?,
    val originalGenre: String?,
    val originalYear: Int?,
    val originalTrackNumber: Int?,
    val originalDiscNumber: Int?,
    val proposedChanges: MetadataChangeset,
    val writeMode: MetadataWriteMode,
    val isSupportedFormat: Boolean,
    val isWritable: Boolean,
    val statusMessage: String = "",
)

data class MetadataEditBatchPlan(
    val plans: List<MetadataEditItemPlan>,
    val writeMode: MetadataWriteMode,
)

data class RenameTemplate(val template: String) {
    companion object {
        val TOKENS = listOf(
            "{artist}",
            "{albumArtist}",
            "{album}",
            "{title}",
            "{track}",
            "{disc}",
            "{year}",
            "{genre}",
            "{originalName}",
        )
    }
}

data class RenamePlanItem(
    val mediaId: MediaId,
    val sourceUri: String,
    val originalFileName: String,
    val proposedFileName: String,
    val proposedUri: String,
    val conflict: Boolean,
    val conflictReason: String? = null,
)

data class OrganizePlanItem(
    val mediaId: MediaId,
    val sourceUri: String,
    val originalPath: String,
    val proposedRelativePath: String,
    val proposedDestinationPath: String,
    val conflict: Boolean,
    val conflictReason: String? = null,
)

data class MissingMediaCandidate(
    val candidateMedia: MediaItem,
    val confidence: String,
    val reason: String,
)
