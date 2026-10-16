@file:Suppress(
    "ArgumentListWrapping",
    "MaximumLineLength",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "LongParameterList",
)

package dev.vimivo.desktop.update

import dev.vimivo.core.update.ReleaseArtifact
import dev.vimivo.core.update.UpdateCheckResult
import dev.vimivo.core.update.UpdateChecker
import dev.vimivo.desktop.library.DesktopDatabase
import java.net.URI

class DesktopUpdateService(
    val currentVersion: String = CURRENT_APP_VERSION,
    val preventDowngrade: Boolean = false,
    val strictArchitecture: Boolean = false,
    val enforceSignature: Boolean = false,
    val trustedKeyIds: Set<String> = emptySet(),
    val signatureVerifier: ((artifact: ReleaseArtifact, signatureHex: String, keyId: String) -> Boolean)? = null,
    val currentDatabaseVersion: Int? = DesktopDatabase.SCHEMA_VERSION,
) {
    val currentPlatform: String = detectPlatform()
    val currentArchitecture: String = detectArchitecture()

    fun checkFromManifestContent(manifestJson: String): UpdateCheckResult {
        val parseResult = UpdateChecker.parseManifestJson(manifestJson)
        return parseResult.fold(
            onSuccess = { manifest ->
                UpdateChecker.evaluateManifest(
                    currentVersionStr = currentVersion,
                    manifest = manifest,
                    platform = currentPlatform,
                    architecture = currentArchitecture,
                    preventDowngrade = preventDowngrade,
                    strictArchitecture = strictArchitecture,
                    enforceSignature = enforceSignature,
                    trustedKeyIds = trustedKeyIds,
                    signatureVerifier = signatureVerifier,
                    currentDatabaseVersion = currentDatabaseVersion,
                )
            },
            onFailure = { error ->
                UpdateCheckResult.Error(error.message ?: "Failed to parse update manifest")
            },
        )
    }

    fun checkFromUrl(manifestUrl: String = DEFAULT_MANIFEST_URL): Result<UpdateCheckResult> {
        return runCatching {
            require(UpdateChecker.isSecureManifestUrl(manifestUrl)) {
                "Insecure or unauthorized update manifest URL: $manifestUrl"
            }
            val connection = URI(manifestUrl).toURL().openConnection()
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Vimivo-Desktop/$currentVersion")

            val jsonContent = connection.getInputStream().bufferedReader().use { it.readText() }
            checkFromManifestContent(jsonContent)
        }
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            os.contains("mac") -> "macos"
            os.contains("win") -> "windows"
            else -> "linux"
        }
    }

    private fun detectArchitecture(): String {
        val arch = System.getProperty("os.arch")?.lowercase() ?: ""
        return when (arch) {
            "aarch64", "arm64" -> "arm64"
            else -> "x64"
        }
    }

    companion object {
        const val CURRENT_APP_VERSION = "1.0.0-rc.1"
        const val DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/Vimivo/Player/main/docs/release-manifest.json"
        const val NETWORK_TIMEOUT_MS = 5000
    }
}
