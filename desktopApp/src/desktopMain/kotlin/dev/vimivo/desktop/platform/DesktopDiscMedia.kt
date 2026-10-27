package dev.vimivo.desktop.platform

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import java.io.File

/**
 * Handles optical disc structures (Blu-ray and DVD folders or ISO disk images).
 */
internal object DesktopDiscMedia {

    /**
     * Checks if the given directory contains a Blu-ray or DVD structure.
     */
    fun isDiscFolder(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val children = directory.listFiles().orEmpty()
        val hasBdmv = children.any { it.isDirectory && it.name.equals("BDMV", ignoreCase = true) }
        val hasVideoTs = children.any { it.isDirectory && it.name.equals("VIDEO_TS", ignoreCase = true) }
        return hasBdmv || hasVideoTs
    }

    /**
     * Resolves a disc folder to a playable MediaItem.
     * For Blu-ray: picks the largest .m2ts stream in BDMV/STREAM (the main movie), or uses bd:// folder path.
     * For DVD: picks the largest VOB in VIDEO_TS or uses dvd:// folder path.
     */
    fun resolveDiscMedia(directory: File): MediaItem? {
        if (!directory.isDirectory) return null
        val bdmv = directory.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("BDMV", ignoreCase = true) }
        if (bdmv != null) {
            val streamDir = File(bdmv, "STREAM")
            val largestM2ts = streamDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("m2ts", ignoreCase = true) }
                ?.maxByOrNull { it.length() }

            val targetFile = largestM2ts ?: directory
            val uri = targetFile.toPath().toUri().toString()
            return MediaItem(
                id = MediaId("bluray://${directory.name}"),
                uri = uri,
                title = "Blu-ray: ${directory.name}",
                kind = MediaKind.VIDEO,
                fileName = targetFile.name,
            )
        }

        val videoTs = directory.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("VIDEO_TS", ignoreCase = true) }
        if (videoTs != null) {
            val largestVob = videoTs.listFiles()
                ?.filter { it.isFile && it.extension.equals("vob", ignoreCase = true) && !it.name.startsWith("VIDEO_TS", ignoreCase = true) }
                ?.maxByOrNull { it.length() }

            val targetFile = largestVob ?: directory
            val uri = targetFile.toPath().toUri().toString()
            return MediaItem(
                id = MediaId("dvd://${directory.name}"),
                uri = uri,
                title = "DVD: ${directory.name}",
                kind = MediaKind.VIDEO,
                fileName = targetFile.name,
            )
        }

        return null
    }
}
