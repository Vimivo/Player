@file:Suppress(
    "ArgumentListWrapping",
    "MaximumLineLength",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
)

package dev.vimivo.desktop.update

import dev.vimivo.core.update.ReleaseArtifact
import dev.vimivo.core.update.UpdateChecker
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DesktopUpdateDownloadManager(
    val connectionTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    val maxAllowedSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    val urlStreamProvider: ((url: String) -> java.io.InputStream)? = null,
) {

    fun downloadArtifact(
        artifact: ReleaseArtifact,
        destinationDir: File,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ): Result<File> = runCatching {
        require(UpdateChecker.isSecureDownloadUrl(artifact.downloadUrl)) {
            "Insecure or credential-leaking artifact download URL: ${artifact.downloadUrl}"
        }
        require(UpdateChecker.validateSha256(artifact.sha256)) {
            "Malformed or invalid SHA-256 digest in artifact metadata: ${artifact.sha256}"
        }
        require(artifact.sizeBytes in 1..maxAllowedSizeBytes) {
            "Artifact declared size ${artifact.sizeBytes} exceeds maximum permitted size of $maxAllowedSizeBytes bytes"
        }

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val requiredSpace = artifact.sizeBytes + MIN_FREE_DISK_HEADROOM_BYTES
        if (destinationDir.usableSpace > 0 && destinationDir.usableSpace < requiredSpace) {
            throw IllegalStateException(
                "Insufficient disk space in ${destinationDir.absolutePath}: " +
                    "available ${destinationDir.usableSpace} bytes, required $requiredSpace bytes",
            )
        }

        val sanitizedFilename = File(artifact.filename).name
        require(sanitizedFilename.isNotBlank() && !sanitizedFilename.contains("..")) {
            "Unsafe artifact filename: ${artifact.filename}"
        }

        val partFile = File(destinationDir, "$sanitizedFilename.part")
        val finalFile = File(destinationDir, sanitizedFilename)

        if (partFile.exists()) {
            partFile.delete()
        }

        try {
            val (inputStream, contentLength) = if (urlStreamProvider != null) {
                Pair(urlStreamProvider.invoke(artifact.downloadUrl), artifact.sizeBytes)
            } else {
                val connection = URI(artifact.downloadUrl).toURL().openConnection()
                connection.connectTimeout = connectionTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("User-Agent", "Vimivo-Desktop-Updater")
                Pair(connection.getInputStream(), connection.contentLengthLong)
            }

            check(contentLength <= maxAllowedSizeBytes) {
                "Server reported Content-Length $contentLength exceeds limit $maxAllowedSizeBytes"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesRead = 0L

            inputStream.use { stream ->
                FileOutputStream(partFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw InterruptedException("Update download was cancelled by user")
                        }
                        totalBytesRead += bytesRead
                        check(totalBytesRead <= maxAllowedSizeBytes) {
                            "Downloaded bytes ($totalBytesRead) exceeded limit $maxAllowedSizeBytes"
                        }
                        digest.update(buffer, 0, bytesRead)
                        outputStream.write(buffer, 0, bytesRead)
                        onProgress?.invoke(totalBytesRead, if (contentLength > 0) contentLength else artifact.sizeBytes)
                    }
                    outputStream.flush()
                }
            }

            val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!computedSha256.equals(artifact.sha256.trim(), ignoreCase = true)) {
                throw SecurityException(
                    "Artifact SHA-256 digest verification failed. " +
                        "Expected: ${artifact.sha256.lowercase()}, Computed: $computedSha256",
                )
            }

            try {
                Files.move(partFile.toPath(), finalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(partFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            finalFile
        } catch (e: Throwable) {
            if (partFile.exists()) {
                partFile.delete()
            }
            throw e
        }
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10000
        const val DEFAULT_READ_TIMEOUT_MS = 30000
        const val DEFAULT_MAX_SIZE_BYTES = 500L * 1024 * 1024 // 500 MB
        const val MIN_FREE_DISK_HEADROOM_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val BUFFER_SIZE = 64 * 1024
    }
}
