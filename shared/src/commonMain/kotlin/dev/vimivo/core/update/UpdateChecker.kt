@file:Suppress("ArgumentListWrapping", "LongParameterList", "MaximumLineLength", "MaxLineLength", "ReturnCount")

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

    data class DowngradeBlocked(
        val currentVersion: String,
        val attemptedVersion: String,
        val reason: String,
    ) : UpdateCheckResult()

    data class Error(val reason: String) : UpdateCheckResult()
}

/**
 * Opt-in, privacy-first software update verification logic.
 * Never performs background polling, auto-installation, or execution of arbitrary code.
 * Validates HTTPS integrity, SHA-256 digest format, and rollback/downgrade protection.
 */
object UpdateChecker {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

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
     * Prevents malicious or accidental rollback attacks when preventDowngrade is enabled.
     */
    fun evaluateManifest(
        currentVersionStr: String,
        manifest: UpdateManifest,
        platform: String,
        architecture: String? = null,
        preferredFormat: String? = null,
        preventDowngrade: Boolean = false,
    ): UpdateCheckResult {
        val currentVer = SemanticVersion.parse(currentVersionStr)
            ?: return UpdateCheckResult.Error("Failed to parse current version: $currentVersionStr")

        val manifestVer = SemanticVersion.parse(manifest.version)
            ?: return UpdateCheckResult.Error("Failed to parse manifest version: ${manifest.version}")

        if (preventDowngrade && currentVer.isNewerThan(manifestVer)) {
            return UpdateCheckResult.DowngradeBlocked(
                currentVersion = currentVersionStr,
                attemptedVersion = manifest.version,
                reason = "Attempted downgrade from $currentVersionStr to ${manifest.version} was blocked for security.",
            )
        }

        return if (manifestVer.isNewerThan(currentVer)) {
            val matchedArtifact = manifest.findArtifactFor(platform, architecture, preferredFormat)
            if (matchedArtifact != null) {
                if (!validateSha256(matchedArtifact.sha256)) {
                    return UpdateCheckResult.Error(
                        "Invalid or malformed SHA-256 digest for artifact: ${matchedArtifact.filename}",
                    )
                }
                if (!isSecureManifestUrl(matchedArtifact.downloadUrl)) {
                    return UpdateCheckResult.Error(
                        "Insecure or credential-leaking download URL for artifact: ${matchedArtifact.filename}",
                    )
                }
            }
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
     * Validates that a string is a standard 64-character hexadecimal SHA-256 digest.
     */
    fun validateSha256(hash: String): Boolean {
        return SHA256_REGEX.matches(hash.trim())
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
