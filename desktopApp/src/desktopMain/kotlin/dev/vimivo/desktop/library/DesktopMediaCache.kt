@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "Wrapping")

package dev.vimivo.desktop.library

import dev.vimivo.desktop.platform.DesktopDirectories
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO

/** Bounded, non-authoritative image cache rooted in the platform's per-user cache directory. */
internal class DesktopMediaCache(
    private val artworkDirectory: Path = DesktopDirectories.resolve().artwork,
    private val thumbnailDirectory: Path = DesktopDirectories.resolve().thumbnails,
    private val maximumBytes: Long = 256L * 1024 * 1024,
) {
    @Synchronized
    fun storeArtwork(cacheIdentity: String, encodedImage: ByteArray): Result<String> =
        storeImage(artworkDirectory, cacheIdentity, encodedImage, MAX_ARTWORK_EDGE)

    @Synchronized
    fun storeThumbnail(cacheIdentity: String, encodedImage: ByteArray): Result<String> =
        storeImage(thumbnailDirectory, cacheIdentity, encodedImage, MAX_THUMBNAIL_EDGE)

    fun getArtwork(cacheIdentity: String): String? {
        val file = artworkDirectory.resolve("${sha256(cacheIdentity)}.jpg")
        return if (Files.isRegularFile(file) && Files.size(file) > 0) file.toUri().toString() else null
    }

    fun getThumbnail(cacheIdentity: String): String? {
        val file = thumbnailDirectory.resolve("${sha256(cacheIdentity)}.jpg")
        return if (Files.isRegularFile(file) && Files.size(file) > 0) file.toUri().toString() else null
    }

    private fun storeImage(
        directory: Path,
        cacheIdentity: String,
        encodedImage: ByteArray,
        maximumEdge: Int,
    ): Result<String> = runCatching {
        require(encodedImage.isNotEmpty() && encodedImage.size <= MAX_SOURCE_IMAGE_BYTES) { "Source image is empty or too large" }
        val source = ImageIO.read(ByteArrayInputStream(encodedImage)) ?: error("Source image is not a supported format")
        val target = scale(source, maximumEdge)
        Files.createDirectories(directory)
        val destination = directory.resolve("${sha256(cacheIdentity)}.jpg")
        val temporary = Files.createTempFile(directory, ".cache-", ".tmp")
        try {
            require(ImageIO.write(target, "jpg", temporary.toFile())) { "JPEG encoder is unavailable" }
            runCatching {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        trim(directory)
        destination.toUri().toString()
    }

    @Synchronized
    fun trim(directory: Path = artworkDirectory) {
        if (!Files.isDirectory(directory)) return
        val files = Files.list(directory).use { stream ->
            stream.filter(Files::isRegularFile).map { path -> path to Files.getLastModifiedTime(path).toMillis() }.toList()
        }.sortedByDescending { it.second }
        var retainedBytes = 0L
        files.forEach { (path, _) ->
            val size = runCatching { Files.size(path) }.getOrDefault(0)
            if (retainedBytes + size <= maximumBytes) retainedBytes += size else Files.deleteIfExists(path)
        }
    }

    private fun scale(source: BufferedImage, maximumEdge: Int): BufferedImage {
        val ratio = minOf(1.0, maximumEdge.toDouble() / maxOf(source.width, source.height))
        val width = maxOf(1, (source.width * ratio).toInt())
        val height = maxOf(1, (source.height * ratio).toInt())
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ARTWORK_EDGE = 1_024
        const val MAX_THUMBNAIL_EDGE = 640
        const val MAX_SOURCE_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
