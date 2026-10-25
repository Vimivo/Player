package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.FileSnapshot
import dev.vimivo.playback.GrowingFilePolicy
import java.io.File
import java.net.URI

/**
 * Follows a local file that may still be written (a download, a recording): remembers its size and modification time
 * when it was (re)loaded and tells the engine when end-of-file should reload it instead of completing (V1 #3).
 */
internal class GrowingFileTracker private constructor(private val file: File, private var loaded: FileSnapshot) {
    private var lastReloadMillis: Long? = null

    /** True when playback should reload the file now rather than finish. */
    fun reloadDue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val now = snapshot() ?: return false
        return GrowingFilePolicy.shouldReload(loaded, now, nowMillis, lastReloadMillis)
    }

    /** Records that the file was reloaded, so the next end-of-file compares against its new size. */
    fun markReloaded(nowMillis: Long = System.currentTimeMillis()) {
        snapshot()?.let { loaded = it }
        lastReloadMillis = nowMillis
    }

    private fun snapshot(): FileSnapshot? =
        file.takeIf(File::isFile)?.let { FileSnapshot(it.length(), it.lastModified()) }

    companion object {
        /** A tracker for a local file URI, or null for streams and files that cannot be read. */
        fun forMedia(media: MediaItem): GrowingFileTracker? {
            if (!media.uri.startsWith("file:")) return null
            val file = runCatching { File(URI(media.uri)) }.getOrNull()?.takeIf(File::isFile) ?: return null
            return GrowingFileTracker(file, FileSnapshot(file.length(), file.lastModified()))
        }
    }
}
