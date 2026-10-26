@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.playback

import android.content.Context
import android.net.Uri
import dev.vimivo.core.lyrics.LrcLyricsParser
import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.core.lyrics.LyricsSidecar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsRepository(private val context: Context) {
    suspend fun load(uri: Uri): Result<LyricsDocument> = runCatching {
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("The lyrics file cannot be opened")
            val lyrics = LrcLyricsParser.parse(text, uri.toString())
            require(lyrics.lines.isNotEmpty()) { "No readable lyrics were found" }
            lyrics
        }
    }

    suspend fun autoDetect(mediaUri: Uri): LyricsDocument? = withContext(Dispatchers.IO) {
        if (mediaUri.scheme != "file") return@withContext null
        val media = java.io.File(mediaUri.path ?: return@withContext null)
        val folder = media.parentFile ?: return@withContext null
        val chosen = LyricsSidecar.best(media.name, folder.list()?.toList().orEmpty()) ?: return@withContext null
        val candidate = java.io.File(folder, chosen)
        load(Uri.fromFile(candidate)).getOrNull()
    }
}
