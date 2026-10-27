package dev.vimivo.desktop.integration.mpris

import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController

/**
 * Creates the OS media controls integration: Now Playing on macOS, MPRIS 2 on Linux and System Media
 * Transport Controls on Windows. Each adapter is selected at runtime for its own platform only.
 */
internal object MprisServiceFactory {
    fun create(
        engine: MpvPlayerEngine,
        queue: PlaybackQueueController,
        togglePlayback: suspend () -> Unit,
        platform: DesktopPlatform = DesktopPlatform.detect(),
    ): MprisService {
        return runCatching {
            when (platform) {
                DesktopPlatform.MACOS -> dev.vimivo.desktop.integration.macos.MacNowPlayingService(
                    engine,
                    queue,
                    togglePlayback,
                ).also { it.start() }
                DesktopPlatform.LINUX -> LinuxMprisService(engine, queue, togglePlayback).also { it.start() }
                DesktopPlatform.WINDOWS -> dev.vimivo.desktop.integration.windows.WindowsMediaControlsService(
                    engine,
                    queue,
                ).also { it.start() }
                DesktopPlatform.UNKNOWN -> NoOpMprisService()
            }
        }.getOrElse { error ->
            // Playback works without OS media controls; record why they are missing for local diagnostics.
            DesktopDiagnosticExporter.defaultBuffer.recordError("MEDIA_CONTROLS", error)
            NoOpMprisService()
        }
    }
}
