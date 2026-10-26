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

    /** What playback should do at the end of the file: finish, reload now, or wait a moment (see the policy). */
    fun decide(nowMillis: Long = System.currentTimeMillis()): GrowingFilePolicy.Decision {
        val now = snapshot() ?: return GrowingFilePolicy.Decision.COMPLETE
        return GrowingFilePolicy.decide(loaded, now, nowMillis, lastReloadMillis)
    }

    /**
     * True when the file is still growing and playback must not finish: it is reloaded through [reload] when the policy
     * allows, and otherwise playback waits a moment (a reload just happened). False when the file is finished.
     */
    fun keepFollowing(reload: () -> Boolean): Boolean = when (decide()) {
        GrowingFilePolicy.Decision.COMPLETE -> false
        GrowingFilePolicy.Decision.WAIT -> true
        GrowingFilePolicy.Decision.RELOAD -> reload().also { reloaded -> if (reloaded) markReloaded() }
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
