@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.platform.android.playback

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LyricLine(val timeMillis: Long?, val text: String)
data class LyricsDocument(val lines: List<LyricLine>, val synchronized: Boolean, val sourceUri: String)

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
            parseLrc(text, uri.toString())
        }
    }

    suspend fun autoDetect(mediaUri: Uri): LyricsDocument? = withContext(Dispatchers.IO) {
        if (mediaUri.scheme != "file") return@withContext null
        val media = java.io.File(mediaUri.path ?: return@withContext null)
        val base = media.nameWithoutExtension
        val candidate = media.parentFile?.listFiles()?.filter { file ->
            file.isFile && file.extension.equals("lrc", true) &&
                (file.nameWithoutExtension == base || file.nameWithoutExtension.startsWith("$base."))
        }?.sortedWith(compareBy<java.io.File> { it.nameWithoutExtension != base }.thenBy { it.name })?.firstOrNull()
            ?: return@withContext null
        load(Uri.fromFile(candidate)).getOrNull()
    }
}

internal fun parseLrc(text: String, sourceUri: String = "memory:"): LyricsDocument {
    val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val lines = buildList {
        text.lineSequence().forEach { raw ->
            val matches = timestamp.findAll(raw).toList()
            val content = timestamp.replace(raw, "").trim()
            if (matches.isEmpty()) {
                if (content.isNotEmpty() && !content.startsWith('[')) add(LyricLine(null, content))
            } else if (content.isNotEmpty()) {
                matches.forEach { match ->
                    val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                    val millis = (match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1_000 + fraction
                    add(LyricLine(millis, content))
                }
            }
        }
    }.sortedWith(compareBy(nullsLast()) { it.timeMillis })
    require(lines.isNotEmpty()) { "No readable lyrics were found" }
    return LyricsDocument(lines, lines.any { it.timeMillis != null }, sourceUri)
}
