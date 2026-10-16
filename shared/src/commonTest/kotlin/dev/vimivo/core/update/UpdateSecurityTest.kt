@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateSecurityTest {

    @Test
    fun testSha256DigestValidation() {
        val validHash = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8"
        assertTrue(UpdateChecker.validateSha256(validHash))

        assertFalse(UpdateChecker.validateSha256(""), "Empty string is not valid SHA-256")
        assertFalse(UpdateChecker.validateSha256("short-hash"), "Short string is not valid SHA-256")
        assertFalse(UpdateChecker.validateSha256("42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153ccZ"), "Contains non-hex character")
        assertFalse(UpdateChecker.validateSha256("${validHash}aa"), "66 chars is too long")
    }

    @Test
    fun testDowngradeAttackRejection() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.0.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-1.0.0.dmg",
                    downloadUrl = "https://example.com/Vimivo-1.0.0.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "arm64",
                ),
            ),
        )

        // Running 1.0.1 and receiving an older 1.0.0 manifest with preventDowngrade enabled
        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.1",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            preventDowngrade = true,
        )

        assertTrue(result is UpdateCheckResult.DowngradeBlocked)
        assertEquals("1.0.1", result.currentVersion)
        assertEquals("1.0.0", result.attemptedVersion)
        assertTrue(result.reason.contains("blocked for security"))
    }

    @Test
    fun testRejectionOfTamperedArtifactSha256() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "windows",
                    format = "exe",
                    filename = "Vimivo-1.1.0-Setup.exe",
                    downloadUrl = "https://example.com/Vimivo-1.1.0-Setup.exe",
                    sizeBytes = 140000000,
                    sha256 = "INVALID_TAMPERED_CHECKSUM",
                    architecture = "x64",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "windows",
            architecture = "x64",
        )

        assertTrue(result is UpdateCheckResult.Error)
        assertTrue(result.reason.contains("Invalid or malformed SHA-256 digest"))
    }

    @Test
    fun testRejectionOfInsecureArtifactDownloadUrl() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "linux",
                    format = "tar.gz",
                    filename = "Vimivo-1.1.0.tar.gz",
                    downloadUrl = "http://unencrypted.server.com/Vimivo.tar.gz",
                    sizeBytes = 120000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "x64",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "linux",
            architecture = "x64",
        )

        assertTrue(result is UpdateCheckResult.Error)
        assertTrue(result.reason.contains("Insecure or credential-leaking download URL"))
    }

    @Test
    fun testStrictArchitectureMismatchRejection() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-1.1.0-x64.dmg",
                    downloadUrl = "https://example.com/Vimivo-1.1.0-x64.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "x64",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            strictArchitecture = true,
        )

        assertTrue(result is UpdateCheckResult.ArchitectureMismatch)
        assertEquals("macos", result.currentPlatform)
        assertEquals("arm64", result.currentArchitecture)
        assertEquals(listOf("x64"), result.availableArchitectures)
    }

    @Test
    fun testIncompatibleDatabaseVersionRejection() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "2.0.0",
            releaseDate = "2026-10-16",
            minSupportedDatabaseVersion = 7,
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-2.0.0.dmg",
                    downloadUrl = "https://example.com/Vimivo-2.0.0.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "arm64",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            currentDatabaseVersion = 6,
        )

        assertTrue(result is UpdateCheckResult.IncompatibleDatabaseVersion)
        assertEquals(6, result.currentDatabaseVersion)
        assertEquals(7, result.requiredDatabaseVersion)
        assertTrue(result.reason.contains("schema version >= 7"))
    }

    @Test
    fun testSignatureEnforcementRejection() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-1.1.0.dmg",
                    downloadUrl = "https://example.com/Vimivo-1.1.0.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "arm64",
                    signature = null,
                    signingKeyId = null,
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            enforceSignature = true,
        )

        assertTrue(result is UpdateCheckResult.SignatureVerificationFailed)
        assertTrue(result.reason.contains("Missing required digital signature"))
    }

    @Test
    fun testUntrustedSigningKeyRejection() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-1.1.0.dmg",
                    downloadUrl = "https://example.com/Vimivo-1.1.0.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "arm64",
                    signature = "deadbeef01020304",
                    signingKeyId = "untrusted-key-id",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            enforceSignature = true,
            trustedKeyIds = setOf("vimivo-release-key-2026"),
        )

        assertTrue(result is UpdateCheckResult.SignatureVerificationFailed)
        assertTrue(result.reason.contains("not in trusted keys set"))
    }

    @Test
    fun testSignatureVerificationSuccess() {
        val manifest = UpdateManifest(
            product = "Vimivo",
            version = "1.1.0",
            releaseDate = "2026-10-16",
            artifacts = listOf(
                ReleaseArtifact(
                    platform = "macos",
                    format = "dmg",
                    filename = "Vimivo-1.1.0.dmg",
                    downloadUrl = "https://example.com/Vimivo-1.1.0.dmg",
                    sizeBytes = 150000000,
                    sha256 = "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                    architecture = "arm64",
                    signature = "valid-sig-hex",
                    signingKeyId = "vimivo-release-key-2026",
                ),
            ),
        )

        val result = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
            enforceSignature = true,
            trustedKeyIds = setOf("vimivo-release-key-2026"),
            signatureVerifier = { _, sig, key -> sig == "valid-sig-hex" && key == "vimivo-release-key-2026" },
        )

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals("1.1.0", result.newVersion)
    }
}
