package dev.vimivo.desktop.update

import dev.vimivo.core.update.UpdateCheckResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopUpdateServiceTest {

    @Test
    fun detectsCurrentPlatformAndArchitecture() {
        val service = DesktopUpdateService()
        assertTrue(service.currentPlatform in listOf("macos", "windows", "linux"))
        assertTrue(service.currentArchitecture in listOf("arm64", "x64"))
    }

    @Test
    fun evaluatesLocalManifestAccurately() {
        val service = DesktopUpdateService(currentVersion = "1.0.0-rc.1")

        val sampleJson = """
            {
              "product": "Vimivo",
              "version": "1.0.0",
              "releaseDate": "2026-10-16",
              "artifacts": [
                {
                  "platform": "macos",
                  "format": "dmg",
                  "filename": "Vimivo-1.0.0.dmg",
                  "downloadUrl": "https://example.com/Vimivo-1.0.0.dmg",
                  "sizeBytes": 150423483,
                  "sha256": "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
                  "architecture": "arm64"
                },
                {
                  "platform": "linux",
                  "format": "deb",
                  "filename": "vimivo_1.0.0_amd64.deb",
                  "downloadUrl": "https://example.com/vimivo.deb",
                  "sizeBytes": 140000000,
                  "sha256": "1234567890123456789012345678901234567890123456789012345678901234",
                  "architecture": "x64"
                },
                {
                  "platform": "windows",
                  "format": "exe",
                  "filename": "Vimivo-Setup-1.0.0.exe",
                  "downloadUrl": "https://example.com/Vimivo-Setup-1.0.0.exe",
                  "sizeBytes": 145000000,
                  "sha256": "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
                  "architecture": "x64"
                }
              ]
            }
        """.trimIndent()

        val result = service.checkFromManifestContent(sampleJson)
        assertTrue(result is UpdateCheckResult.UpdateAvailable, "got $result")
        assertEquals("1.0.0", result.newVersion)
        assertNotNull(result.matchedArtifact, "Must match an artifact for platform ${service.currentPlatform}")
    }

    @Test
    fun reportsUpToDateWhenVersionMatchesOrExceeds() {
        val service = DesktopUpdateService(currentVersion = "1.0.0")

        val sampleJson = """
            {
              "product": "Vimivo",
              "version": "1.0.0",
              "releaseDate": "2026-10-16",
              "artifacts": []
            }
        """.trimIndent()

        val result = service.checkFromManifestContent(sampleJson)
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun rejectsInsecureUrlInCheckFromUrl() {
        val service = DesktopUpdateService()
        val insecureResult = service.checkFromUrl("http://insecure.example.com/manifest.json")
        assertTrue(insecureResult.isFailure)
        assertTrue(insecureResult.exceptionOrNull()?.message?.contains("Insecure") == true)
    }
}
