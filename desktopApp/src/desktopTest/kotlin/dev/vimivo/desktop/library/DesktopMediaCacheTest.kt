package dev.vimivo.desktop.library

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMediaCacheTest {
    @Test
    fun storesScaledArtworkWithDeterministicIdentity() {
        val directory = Files.createTempDirectory("vimivo-artwork-cache")
        val source = BufferedImage(2_048, 1_024, BufferedImage.TYPE_INT_RGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.color = Color(20, 80, 160)
                graphics.fillRect(0, 0, image.width, image.height)
            } finally {
                graphics.dispose()
            }
        }
        val encoded = ByteArrayOutputStream().use { output ->
            ImageIO.write(source, "png", output)
            output.toByteArray()
        }
        val cache = DesktopMediaCache(directory, maximumBytes = 10L * 1024 * 1024)

        val first = cache.storeArtwork("stable-media-identity", encoded).getOrThrow()
        val second = cache.storeArtwork("stable-media-identity", encoded).getOrThrow()
        val cached = ImageIO.read(java.net.URI(first).toURL())

        assertEquals(first, second)
        assertEquals(1_024, cached.width)
        assertEquals(512, cached.height)
        assertTrue(Files.isRegularFile(java.nio.file.Path.of(java.net.URI(first))))
    }

    @Test
    fun rejectsCorruptArtworkWithoutLeavingFiles() {
        val directory = Files.createTempDirectory("vimivo-corrupt-artwork")
        val result = DesktopMediaCache(directory).storeArtwork("bad", byteArrayOf(1, 2, 3))

        assertTrue(result.isFailure)
        assertTrue(Files.notExists(directory) || Files.list(directory).use { it.findAny().isEmpty })
    }
}
