package dev.vimivo.desktop.diagnostics

import dev.vimivo.core.diagnostics.DiagnosticBuffer
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDiagnosticExporterTest {

    @Test
    fun generatesPopulatedReport() {
        val buffer = DiagnosticBuffer()
        buffer.recordEvent("TEST", "Desktop event at /Users/developer/test.flac")
        val exporter = DesktopDiagnosticExporter(buffer)

        val report = exporter.createReport()
        assertEquals(DesktopDiagnosticExporter.APP_VERSION, report.appVersion)
        assertEquals(DesktopDiagnosticExporter.BUILD_COMMIT, report.buildCommit)
        assertTrue(report.osVersion.isNotBlank())
        assertTrue(report.architecture.isNotBlank())
        assertEquals(1, report.recentEvents.size)
        assertFalse(report.recentEvents.single().message.contains("/Users/developer"))
    }

    @Test
    fun exportsReportToUserSelectedFile() {
        val buffer = DiagnosticBuffer()
        buffer.recordEvent("AUDIO", "Playing audio stream")
        val exporter = DesktopDiagnosticExporter(buffer)

        val tempDir = Files.createTempDirectory("vimivo-diagnostics-test")
        val targetPath = tempDir.resolve("vimivo-diagnostic-report.md")

        val result = exporter.exportToPath(targetPath)
        assertTrue(result.isSuccess)
        assertEquals(targetPath, result.getOrThrow())

        assertTrue(Files.exists(targetPath))
        val text = targetPath.readText()
        assertTrue(text.contains("# Vimivo Privacy-Safe Diagnostic Report"))
        assertTrue(text.contains("Playing audio stream"))
    }

    @Test
    fun refusesToExportToProtectedSystemLocations() {
        val exporter = DesktopDiagnosticExporter()
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val protectedPath = Paths.get(if (windows) "C:\\Windows\\vimivo-leak.md" else "/etc/vimivo-leak.md")

        val result = exporter.exportToPath(protectedPath)
        assertTrue(result.isFailure, "Must refuse to export to protected system directory")
        assertTrue(result.exceptionOrNull()?.message?.contains("protected system directory") == true)
    }

    @Test
    fun supportsCustomAppVersionAndBuildCommit() {
        val exporter = DesktopDiagnosticExporter(
            appVersion = "1.0.0-rc.2",
            buildCommit = "custom-commit-abc",
        )
        val report = exporter.createReport()
        assertEquals("1.0.0-rc.2", report.appVersion)
        assertEquals("custom-commit-abc", report.buildCommit)
    }
}
