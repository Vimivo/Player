package dev.vimivo.platform.android.library

import android.content.Context
import android.net.Uri
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.Playlist
import dev.vimivo.domain.repository.PlaylistRepository
import dev.vimivo.platform.android.database.LibraryDao
import java.io.BufferedReader
import java.io.OutputStreamWriter

class PlaylistDocuments(
    private val context: Context,
    private val libraryDao: LibraryDao,
    private val playlists: PlaylistRepository,
) {
    suspend fun import(uri: Uri): Result<Playlist> = runCatching {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readLines)
            ?: error("The selected playlist cannot be opened")
        val references = parseM3u(lines)
        require(references.isNotEmpty()) { "The playlist contains no supported media references" }
        val matches = libraryDao.mediaByUris(references).associateBy { it.uri }
        val ids = references.mapNotNull { matches[it]?.id }.distinct().map(::MediaId)
        require(ids.isNotEmpty()) { "None of the playlist entries are in the local library" }
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported playlist"
        playlists.create(name, ids).getOrThrow()
    }

    suspend fun export(uri: Uri, playlist: Playlist): Result<Unit> = runCatching {
        val media = libraryDao.mediaByIds(playlist.items.map(MediaId::value)).associateBy { it.id }
        val ordered = playlist.items.mapNotNull { media[it.value] }
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { writer ->
                writer.appendLine("#EXTM3U")
                ordered.forEach { item ->
                    writer.appendLine("#EXTINF:${item.durationMillis?.div(1_000) ?: -1},${item.title}")
                    writer.appendLine(item.uri)
                }
            }
        } ?: error("The export destination cannot be opened")
    }
}

internal fun parseM3u(lines: List<String>): List<String> = lines.asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith('#') }
    .filter { it.startsWith("content://") || it.startsWith("file://") || it.startsWith("http://") || it.startsWith("https://") }
    .toList()
