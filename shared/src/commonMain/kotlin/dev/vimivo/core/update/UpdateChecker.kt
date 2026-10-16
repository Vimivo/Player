@file:Suppress(
    "ArgumentListWrapping",
    "LongParameterList",
    "MaximumLineLength",
    "MaxLineLength",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "ComplexCondition",
)

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

    data class ArchitectureMismatch(
        val currentPlatform: String,
        val currentArchitecture: String,
        val availableArchitectures: List<String>,
    ) : UpdateCheckResult()

    data class IncompatibleDatabaseVersion(
        val currentDatabaseVersion: Int,
        val requiredDatabaseVersion: Int,
        val reason: String,
    ) : UpdateCheckResult()

    data class SignatureVerificationFailed(
        val artifactFilename: String,
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
     * Evaluates a validated manifest against the current running application version, platform, and architecture.
     * Prevents malicious or accidental rollback attacks when preventDowngrade is enabled.
     */
    fun evaluateManifest(
        currentVersionStr: String,
        manifest: UpdateManifest,
        platform: String,
        architecture: String? = null,
        preferredFormat: String? = null,
        preventDowngrade: Boolean = false,
        strictArchitecture: Boolean = false,
        enforceSignature: Boolean = false,
        trustedKeyIds: Set<String> = emptySet(),
        signatureVerifier: ((artifact: ReleaseArtifact, signatureHex: String, keyId: String) -> Boolean)? = null,
        currentDatabaseVersion: Int? = null,
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

        if (currentDatabaseVersion != null && manifest.minSupportedDatabaseVersion > currentDatabaseVersion) {
            return UpdateCheckResult.IncompatibleDatabaseVersion(
                currentDatabaseVersion = currentDatabaseVersion,
                requiredDatabaseVersion = manifest.minSupportedDatabaseVersion,
                reason = "Update requires database schema version >= ${manifest.minSupportedDatabaseVersion}, but current is $currentDatabaseVersion.",
            )
        }

        return if (manifestVer.isNewerThan(currentVer)) {
            val matchedArtifact = manifest.findArtifactFor(platform, architecture, preferredFormat, strictArchitecture)
            val platformArtifacts = manifest.artifacts.filter { it.platform.equals(platform, ignoreCase = true) }

            if (platformArtifacts.isNotEmpty() && matchedArtifact == null && strictArchitecture && architecture != null) {
                return UpdateCheckResult.ArchitectureMismatch(
                    currentPlatform = platform,
                    currentArchitecture = architecture,
                    availableArchitectures = platformArtifacts.mapNotNull { it.architecture }.distinct(),
                )
            }

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
                if (enforceSignature) {
                    val signature = matchedArtifact.signature
                    val keyId = matchedArtifact.signingKeyId
                    if (signature.isNullOrBlank() || keyId.isNullOrBlank()) {
                        return UpdateCheckResult.SignatureVerificationFailed(
                            artifactFilename = matchedArtifact.filename,
                            reason = "Missing required digital signature or signing key ID for artifact: ${matchedArtifact.filename}",
                        )
                    }
                    if (trustedKeyIds.isNotEmpty() && keyId !in trustedKeyIds) {
                        return UpdateCheckResult.SignatureVerificationFailed(
                            artifactFilename = matchedArtifact.filename,
                            reason = "Signing key ID '$keyId' is not in trusted keys set: $trustedKeyIds",
                        )
                    }
                    if (signatureVerifier != null && !signatureVerifier(matchedArtifact, signature, keyId)) {
                        return UpdateCheckResult.SignatureVerificationFailed(
                            artifactFilename = matchedArtifact.filename,
                            reason = "Cryptographic signature verification failed for artifact: ${matchedArtifact.filename}",
                        )
                    }
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

    /**
     * Enforces security policy on artifact download URLs:
     * - Must use secure HTTPS protocol.
     * - Must not contain sensitive tokens or passwords in query string.
     */
    fun isSecureDownloadUrl(url: String): Boolean = isSecureManifestUrl(url)
}
