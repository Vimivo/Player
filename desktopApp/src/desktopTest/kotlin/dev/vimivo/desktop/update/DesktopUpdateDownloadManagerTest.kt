package dev.vimivo.desktop.update

import dev.vimivo.core.update.ReleaseArtifact
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopUpdateDownloadManagerTest {

    private lateinit var testTempDir: File

    @BeforeTest
    fun setUp() {
        testTempDir = Files.createTempDirectory("vimivo_download_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        testTempDir.deleteRecursively()
    }

    @Test
    fun rejectsInsecureDownloadUrl() {
        val manager = DesktopUpdateDownloadManager()
        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "http://unencrypted.com/Vimivo-1.0.0.dmg",
            sizeBytes = 1000,
            sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
        )

        val result = manager.downloadArtifact(artifact, testTempDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Insecure") == true)
    }

    @Test
    fun rejectsMalformedSha256() {
        val manager = DesktopUpdateDownloadManager()
        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
            sizeBytes = 1000,
            sha256 = "NOT_A_VALID_HEX_DIGEST",
        )

        val result = manager.downloadArtifact(artifact, testTempDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("SHA-256") == true)
    }

    @Test
    fun rejectsExcessiveArtifactSize() {
        val manager = DesktopUpdateDownloadManager(maxAllowedSizeBytes = 1024 * 1024)
        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
            sizeBytes = 10 * 1024 * 1024, // 10MB > 1MB limit
            sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
        )

        val result = manager.downloadArtifact(artifact, testTempDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("exceeds maximum permitted size") == true)
    }

    @Test
    fun successfulDownloadAndAtomicMove() {
        val samplePayload = "Sample binary update artifact content for Vimivo 1.0.0".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(samplePayload)
            .joinToString("") { "%02x".format(it) }

        val manager = DesktopUpdateDownloadManager(
            urlStreamProvider = { ByteArrayInputStream(samplePayload) },
        )

        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
            sizeBytes = samplePayload.size.toLong(),
            sha256 = digest,
        )

        var reportedProgress = 0L
        val result = manager.downloadArtifact(
            artifact = artifact,
            destinationDir = testTempDir,
            onProgress = { bytesDownloaded, _ -> reportedProgress = bytesDownloaded },
        )

        assertTrue(result.isSuccess)
        val downloadedFile = result.getOrThrow()
        assertTrue(downloadedFile.exists(), "Final downloaded file must exist")
        assertEquals(samplePayload.size.toLong(), downloadedFile.length())
        assertEquals(samplePayload.size.toLong(), reportedProgress)

        val partFile = File(testTempDir, "Vimivo-1.0.0.dmg.part")
        assertFalse(partFile.exists(), "Staging .part file must be removed after atomic move")
    }

    @Test
    fun deletesStagingFileOnChecksumMismatch() {
        val samplePayload = "Tampered or corrupted binary payload".toByteArray(Charsets.UTF_8)
        val wrongDigest = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8"

        val manager = DesktopUpdateDownloadManager(
            urlStreamProvider = { ByteArrayInputStream(samplePayload) },
        )

        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
            sizeBytes = samplePayload.size.toLong(),
            sha256 = wrongDigest,
        )

        val result = manager.downloadArtifact(artifact, testTempDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)

        val finalFile = File(testTempDir, "Vimivo-1.0.0.dmg")
        val partFile = File(testTempDir, "Vimivo-1.0.0.dmg.part")
        assertFalse(finalFile.exists(), "Final file must never be created on digest mismatch")
        assertFalse(partFile.exists(), "Staging .part file must be cleanly deleted on failure")
    }

    @Test
    fun handlesCancellationAndDeletesStagingFile() {
        val samplePayload = ByteArray(1024 * 1024) { 0x41 }
        val digest = MessageDigest.getInstance("SHA-256").digest(samplePayload)
            .joinToString("") { "%02x".format(it) }

        var cancelNext = false
        val manager = DesktopUpdateDownloadManager(
            urlStreamProvider = { ByteArrayInputStream(samplePayload) },
        )

        val artifact = ReleaseArtifact(
            platform = "macos",
            format = "dmg",
            filename = "Vimivo-1.0.0.dmg",
            downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
            sizeBytes = samplePayload.size.toLong(),
            sha256 = digest,
        )

        val result = manager.downloadArtifact(
            artifact = artifact,
            destinationDir = testTempDir,
            onProgress = { _, _ -> cancelNext = true },
            isCancelled = { cancelNext },
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InterruptedException)

        val finalFile = File(testTempDir, "Vimivo-1.0.0.dmg")
        val partFile = File(testTempDir, "Vimivo-1.0.0.dmg.part")
        assertFalse(finalFile.exists(), "Final file must not exist when cancelled")
        assertFalse(partFile.exists(), "Staging .part file must be cleaned up when cancelled")
    }
}
