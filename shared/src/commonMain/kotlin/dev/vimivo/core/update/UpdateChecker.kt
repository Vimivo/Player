package dev.vimivo.core.update

import kotlinx.serialization.json.Json

sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val manifest: UpdateManifest,
        val matchedArtifact: ReleaseArtifact?,
    ) : UpdateCheckResult()

    data class Error(val reason: String) : UpdateCheckResult()
}

/**
 * Opt-in, privacy-first software update verification logic.
 * Never performs background polling, auto-installation, or execution of arbitrary code.
 */
object UpdateChecker {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses and validates raw update manifest JSON.
     */
    fun parseManifestJson(jsonString: String): Result<UpdateManifest> {
        return runCatching {
            val manifest = json.decodeFromString<UpdateManifest>(jsonString)
            require(manifest.product.equals("Vimivo", ignoreCase = true)) {
                "Invalid product in update manifest: ${manifest.product}"
            }
            require(manifest.version.isNotBlank()) {
                "Manifest version string must not be blank"
            }
            manifest
        }
    }

    /**
     * Evaluates a validated manifest against the current running application version and platform.
     */
    fun evaluateManifest(
        currentVersionStr: String,
        manifest: UpdateManifest,
        platform: String,
        architecture: String? = null,
        preferredFormat: String? = null,
    ): UpdateCheckResult {
        val currentVer = SemanticVersion.parse(currentVersionStr)
            ?: return UpdateCheckResult.Error("Failed to parse current version: $currentVersionStr")

        val manifestVer = SemanticVersion.parse(manifest.version)
            ?: return UpdateCheckResult.Error("Failed to parse manifest version: ${manifest.version}")

        return if (manifestVer.isNewerThan(currentVer)) {
            val matchedArtifact = manifest.findArtifactFor(platform, architecture, preferredFormat)
            UpdateCheckResult.UpdateAvailable(
                currentVersion = currentVersionStr,
                newVersion = manifest.version,
                manifest = manifest,
                matchedArtifact = matchedArtifact,
            )
        } else {
            UpdateCheckResult.UpToDate(currentVersionStr)
        }
    }

    /**
     * Enforces security policy on update check URLs:
     * - Must use secure HTTPS protocol.
     * - Must not contain query credentials, passwords, or tokens.
     */
    fun isSecureManifestUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = url.lowercase()
        val hasForbiddenToken = lower.contains("token=") ||
            lower.contains("password=") ||
            lower.contains("secret=") ||
            lower.contains("auth=")
        return !hasForbiddenToken
    }
}
