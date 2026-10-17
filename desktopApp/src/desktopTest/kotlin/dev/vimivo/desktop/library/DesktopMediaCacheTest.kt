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

    @Test
    fun handlesTransparentPngCompositedOntoDarkBackground() {
        val directory = Files.createTempDirectory("vimivo-transparent-artwork")
        val source = BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.color = Color(255, 0, 0, 180)
                graphics.fillOval(50, 50, 400, 400)
            } finally {
                graphics.dispose()
            }
        }
        val encoded = ByteArrayOutputStream().use { output ->
            ImageIO.write(source, "png", output)
            output.toByteArray()
        }
        val cache = DesktopMediaCache(directory)
        val uri = cache.storeArtwork("transparent-test", encoded).getOrThrow()
        val cached = ImageIO.read(java.net.URI(uri).toURL())
        assertTrue(cached.width == 500 && cached.height == 500)
        val rgb = cached.getRGB(10, 10)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        assertTrue(r in 20..30, "Red channel should be near 24, was $r")
        assertTrue(g in 20..30, "Green channel should be near 24, was $g")
        assertTrue(b in 20..35, "Blue channel should be near 27, was $b")
    }
}
