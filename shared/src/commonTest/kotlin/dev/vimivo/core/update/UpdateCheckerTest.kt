@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun semanticVersionParsingAndComparison() {
        val v09 = SemanticVersion.parse("0.9.5")
        val v10rc1 = SemanticVersion.parse("1.0.0-rc.1")
        val v10rc2 = SemanticVersion.parse("1.0.0-rc.2")
        val v10 = SemanticVersion.parse("1.0.0")
        val v101 = SemanticVersion.parse("1.0.1")

        assertNotNull(v09)
        assertNotNull(v10rc1)
        assertNotNull(v10rc2)
        assertNotNull(v10)
        assertNotNull(v101)

        assertTrue(v10rc1.isNewerThan(v09))
        assertTrue(v10rc2.isNewerThan(v10rc1))
        assertTrue(v10.isNewerThan(v10rc2), "Stable release must be newer than any prerelease candidate")
        assertTrue(v101.isNewerThan(v10))
        assertFalse(v10.isNewerThan(v10))
    }

    @Test
    fun parseValidManifestJson() {
        val sampleJson = """
            {
              "product": "Vimivo",
              "version": "1.0.0",
              "releaseDate": "2026-10-16T12:00:00Z",
              "channel": "stable",
              "releaseNotesUrl": "https://github.com/Vimivo/Player/releases/tag/v1.0.0",
              "minSupportedDatabaseVersion": 4,
              "artifacts": [
                {
                  "platform": "macos",
                  "format": "dmg",
                  "filename": "Vimivo-1.0.0.dmg",
                  "downloadUrl": "https://github.com/Vimivo/Player/releases/download/v1.0.0/Vimivo-1.0.0.dmg",
                  "sizeBytes": 150423483,
                  "sha256": "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                  "architecture": "arm64"
                },
                {
                  "platform": "windows",
                  "format": "exe",
                  "filename": "Vimivo-Setup-1.0.0.exe",
                  "downloadUrl": "https://github.com/Vimivo/Player/releases/download/v1.0.0/Vimivo-Setup-1.0.0.exe",
                  "sizeBytes": 140000000,
                  "sha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                  "architecture": "x64"
                }
              ]
            }
        """.trimIndent()

        val result = UpdateChecker.parseManifestJson(sampleJson)
        assertTrue(result.isSuccess)
        val manifest = result.getOrThrow()
        assertEquals("Vimivo", manifest.product)
        assertEquals("1.0.0", manifest.version)
        assertEquals(2, manifest.artifacts.size)

        val macArtifact = manifest.findArtifactFor("macos", architecture = "arm64")
        assertNotNull(macArtifact)
        assertEquals("Vimivo-1.0.0.dmg", macArtifact.filename)

        val winArtifact = manifest.findArtifactFor("windows", architecture = "x64")
        assertNotNull(winArtifact)
        assertEquals("Vimivo-Setup-1.0.0.exe", winArtifact.filename)
    }

    @Test
    fun rejectInvalidProductOrMalformedJson() {
        val wrongProductJson = """
            {
              "product": "OtherPlayer",
              "version": "1.0.0",
              "releaseDate": "2026-10-16T12:00:00Z"
            }
        """.trimIndent()
        val res1 = UpdateChecker.parseManifestJson(wrongProductJson)
        assertTrue(res1.isFailure, "Must reject manifest with mismatched product name")

        val malformedJson = "{ not real json }"
        val res2 = UpdateChecker.parseManifestJson(malformedJson)
        assertTrue(res2.isFailure, "Must reject malformed JSON syntax")
    }

    @Test
    fun evaluateManifestDetectsUpdatesAccurately() {
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

        // When running an older RC version
        val resultUpdateAvailable = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0-rc.1",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
        )
        assertTrue(resultUpdateAvailable is UpdateCheckResult.UpdateAvailable)
        assertEquals("1.0.0", resultUpdateAvailable.newVersion)
        assertEquals("Vimivo-1.0.0.dmg", resultUpdateAvailable.matchedArtifact?.filename)

        // When already running the latest version
        val resultUpToDate = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.0.0",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
        )
        assertTrue(resultUpToDate is UpdateCheckResult.UpToDate)

        // When running a newer development version
        val resultNewer = UpdateChecker.evaluateManifest(
            currentVersionStr = "1.1.0-alpha.1",
            manifest = manifest,
            platform = "macos",
            architecture = "arm64",
        )
        assertTrue(resultNewer is UpdateCheckResult.UpToDate)
    }

    @Test
    fun isSecureManifestUrlEnforcesHttpsAndRejectsSecrets() {
        assertTrue(UpdateChecker.isSecureManifestUrl("https://raw.githubusercontent.com/Vimivo/Player/main/docs/release-manifest.json"))
        assertFalse(UpdateChecker.isSecureManifestUrl("http://insecure.example.com/manifest.json"), "Must reject plain HTTP")
        assertFalse(UpdateChecker.isSecureManifestUrl("https://example.com/manifest.json?token=secret123"), "Must reject URL with token")
        assertFalse(UpdateChecker.isSecureManifestUrl("https://example.com/manifest.json?password=pass"), "Must reject URL with password")
    }
}
