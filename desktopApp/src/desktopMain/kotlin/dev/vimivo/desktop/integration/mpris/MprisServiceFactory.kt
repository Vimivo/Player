package dev.vimivo.desktop.integration.mpris

import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController

/**
 * Factory for creating platform media controls integration (e.g. MPRIS 2 on Linux).
 */
internal object MprisServiceFactory {
    fun create(
        engine: MpvPlayerEngine,
        queue: PlaybackQueueController,
        togglePlayback: suspend () -> Unit,
        platform: DesktopPlatform = DesktopPlatform.detect(),
    ): MprisService {
        if (platform != DesktopPlatform.LINUX) return NoOpMprisService()
        return runCatching {
            LinuxMprisService(engine, queue, togglePlayback).also { it.start() }
        }.getOrElse { NoOpMprisService() }
    }
}
