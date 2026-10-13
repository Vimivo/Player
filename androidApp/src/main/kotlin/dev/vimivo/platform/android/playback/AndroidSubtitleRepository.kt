package dev.vimivo.platform.android.playback

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dev.vimivo.core.model.MediaItem
import dev.vimivo.platform.android.database.LibraryDao
import dev.vimivo.playback.ExternalSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AndroidSubtitleRepository(
    private val context: Context,
    private val dao: LibraryDao,
) {
    suspend fun inspect(uri: Uri): Result<ExternalSubtitle> = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri) ?: error("The selected subtitle has no file name")
            val extension = name.substringAfterLast('.', "").lowercase()
            val mimeType = subtitleMimeType(extension) ?: error("Unsupported subtitle format: .$extension")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val sample = ByteArray(SAMPLE_SIZE)
                val count = stream.read(sample)
                check(count >= 0) { "The subtitle could not be read" }
                check(sample.take(count).none { it == 0.toByte() }) {
                    "The subtitle appears to use an unsupported binary encoding"
                }
            } ?: error("Subtitle permission is unavailable")
            ExternalSubtitle(uri.toString(), name, subtitleLanguageFromName(name), mimeType)
        }
    }

    suspend fun matching(media: MediaItem): List<ExternalSubtitle> = withContext(Dispatchers.IO) {
        val videoName = displayName(Uri.parse(media.uri)) ?: return@withContext emptyList()
        val baseName = videoName.substringBeforeLast('.').lowercase()
        if (baseName.isBlank()) return@withContext emptyList()
        val readableTrees = dao.safGrants()
            .filter { it.isTree }
            .filter { grant ->
                context.contentResolver.persistedUriPermissions.any {
                    it.isReadPermission && it.uri.toString() == grant.uri
                }
            }
        buildList {
            var inspected = 0
            val queue = ArrayDeque<DocumentFile>()
            readableTrees.mapNotNullTo(queue) { DocumentFile.fromTreeUri(context, Uri.parse(it.uri)) }
            while (queue.isNotEmpty() && inspected < MAX_DOCUMENTS) {
                coroutineContext.ensureActive()
                val document = queue.removeFirst()
                document.listFiles().forEach { child ->
                    if (inspected++ >= MAX_DOCUMENTS) return@forEach
                    if (child.isDirectory) {
                        queue.addLast(child)
                    } else if (isSubtitleMatch(child.name, baseName)) {
                        inspect(child.uri).getOrNull()?.let(::add)
                    }
                }
            }
        }.distinctBy(ExternalSubtitle::uri).sortedWith(
            compareBy<ExternalSubtitle> { it.language == null }.thenBy(ExternalSubtitle::label),
        )
    }

    private fun displayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        const val MIME_SRT = "application/x-subrip"
        const val MIME_ASS = "text/x-ssa"
        const val MIME_WEBVTT = "text/vtt"
        private const val SAMPLE_SIZE = 4_096
        private const val MAX_DOCUMENTS = 5_000
    }
}

internal fun subtitleMimeType(extension: String): String? = when (extension.lowercase()) {
    "srt" -> AndroidSubtitleRepository.MIME_SRT
    "ass", "ssa" -> AndroidSubtitleRepository.MIME_ASS
    "vtt" -> AndroidSubtitleRepository.MIME_WEBVTT
    else -> null
}

internal fun isSubtitleMatch(fileName: String?, videoBase: String): Boolean {
    val name = fileName?.lowercase() ?: return false
    val extension = name.substringAfterLast('.', "")
    if (subtitleMimeType(extension) == null) return false
    val subtitleBase = name.removeSuffix(".$extension")
    return subtitleBase == videoBase.lowercase() || subtitleBase.startsWith("${videoBase.lowercase()}.")
}

internal fun subtitleLanguageFromName(name: String): String? {
    val parts = name.substringBeforeLast('.').split('.')
    return parts.lastOrNull()
        ?.takeIf { parts.size >= 2 }
        ?.takeIf { it.length in 2..8 && it.all { character -> character.isLetter() || character == '-' } }
        ?.lowercase()
}
