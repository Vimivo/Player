package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.ExternalSubtitle
import java.io.File
import java.net.URI

/** Subtitle files beside or chosen for a local video. */
object MpvSubtitleFiles {
    val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "webvtt", "sub")
    private val FOLDERS = listOf("Subs", "Subtitles", "subs", "subtitles")

    /**
     * Subtitle folders that exist beside a local file, once each: on case-insensitive filesystems
     * (macOS, Windows) "Subs" and "subs" are the same folder and must not load its files twice.
     */
    internal fun foldersBeside(media: MediaItem): List<String> {
        val directory = runCatching { File(URI(media.uri)).parentFile }.getOrNull() ?: return emptyList()
        return FOLDERS
            .map { File(directory, it) }
            .filter(File::isDirectory)
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path).lowercase() }
            .map(File::getName)
    }

    /** A subtitle file the user dropped or chose; null for anything that is not a subtitle. */
    fun forFile(file: File): ExternalSubtitle? =
        file.takeIf { it.extension.lowercase() in EXTENSIONS }?.let {
            ExternalSubtitle(
                it.toPath().toUri().toString(),
                it.name,
                languageFromName(it.name),
                mimeType(it.extension),
            )
        }

    private fun languageFromName(fileName: String): String? =
        fileName.substringBeforeLast('.').substringAfterLast('.', "").takeIf { it.length in 2..8 }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "srt" -> "application/x-subrip"
        "vtt", "webvtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/octet-stream"
    }
}
