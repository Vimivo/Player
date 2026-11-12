package dev.vimivo.desktop.platform

import dev.vimivo.core.util.NaturalOrder
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** The playable files inside a folder, for "Open Folder". */
internal object FolderMedia {
    /** Stops a mistaken pick of a whole drive from queueing the world. */
    const val MAX_FILES = 5_000

    class Result(val files: List<File>, val truncated: Boolean)

    /**
     * Audio and video files under [root], folders first-to-last in natural order (episode 2 before episode 10), hidden
     * files and folders skipped. Subfolders are included when [recursive] is true.
     */
    fun collect(root: File, recursive: Boolean = true): Result {
        val found = ArrayList<File>()
        var truncated = false
        val visitor = object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = when {
                dir == root.toPath() -> FileVisitResult.CONTINUE
                !recursive || Files.isHidden(dir) -> FileVisitResult.SKIP_SUBTREE
                else -> FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val f = file.toFile()
                if (!Files.isHidden(file) && isPlayable(f)) {
                    if (found.size >= MAX_FILES) {
                        truncated = true
                        return FileVisitResult.TERMINATE
                    }
                    found += f
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        }
        Files.walkFileTree(root.toPath(), visitor)
        val ordered = found.sortedWith(compareBy(NaturalOrder) { it.relativeTo(root).path })
        return Result(ordered, truncated)
    }

    private fun isPlayable(file: File): Boolean =
        DesktopMediaTypes.familyOf(file).let { it == MediaFamily.VIDEO || it == MediaFamily.AUDIO }
}
