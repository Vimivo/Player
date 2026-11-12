package dev.vimivo.desktop.platform

import java.io.File

/**
 * Finds a poster picture that already sits next to a video, so the library can show it with no network lookup:
 * `<name>-poster.jpg`, `<name>.jpg`, then `poster`, `folder`, `cover`, `movie` or `thumb` in the same folder.
 */
internal object LocalPoster {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp")
    private val folderNames = listOf("poster", "folder", "cover", "movie", "thumb")

    fun find(video: File): File? {
        val folder = video.parentFile ?: return null
        val byName = folder.listFiles { f -> f.isFile && f.extension.lowercase() in imageExtensions }
            ?.associateBy { it.name.lowercase() } ?: return null
        val stem = video.nameWithoutExtension.lowercase()
        val candidates = buildList {
            imageExtensions.forEach { add("$stem-poster.$it") }
            imageExtensions.forEach { add("$stem.$it") }
            folderNames.forEach { base -> imageExtensions.forEach { add("$base.$it") } }
        }
        return candidates.firstNotNullOfOrNull { byName[it] }?.takeIf { it.length() in 1..MAX_BYTES }
    }
}
