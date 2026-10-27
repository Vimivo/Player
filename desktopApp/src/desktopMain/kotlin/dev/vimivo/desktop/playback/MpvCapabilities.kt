package dev.vimivo.desktop.playback

import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.playback.AudioEnhancementCapabilities
import dev.vimivo.playback.EngineCapabilities
import dev.vimivo.playback.PlaybackSpeeds

/** What the libmpv engine can do on each desktop OS. */
internal object MpvCapabilities {
    private const val MAX_VOLUME = 2f

    fun forPlatform(platform: DesktopPlatform) = EngineCapabilities(
        localAudio = true,
        localVideo = platform != DesktopPlatform.UNKNOWN,
        httpStreaming = true,
        hlsStreaming = true,
        dashStreaming = true,
        liveStreaming = true,
        hardwareDecoding = true,
        softwareDecoding = true,
        playbackSpeed = true,
        audioTrackSelection = true,
        videoTrackSelection = true,
        embeddedSubtitles = true,
        externalSubtitles = true,
        hdr = platform != DesktopPlatform.UNKNOWN,
        subtitleDelay = true,
        audioDelay = true,
        subtitleAppearance = true,
        subtitlePosition = true,
        chapters = true,
        abRepeat = true,
        videoAdjustments = true,
        screenshots = true,
        frameStepping = platform != DesktopPlatform.UNKNOWN,
        deinterlacing = platform != DesktopPlatform.UNKNOWN,
        supportedSpeedRange = PlaybackSpeeds.RANGE,
        maxVolume = MAX_VOLUME,
        audioEnhancements = AudioEnhancementCapabilities(
            graphicEqualizer = true,
            bassEnhancement = true,
            trebleAdjustment = true,
            attenuatingPreamp = true,
            replayGain = true,
            gapless = true,
            stereoBalance = true,
            pitchPreservation = true,
            outputSelection = true,
            normalization = true,
            limitation = "Equalizer uses libmpv's FFmpeg filter chain; " +
                "crossfade and bit-perfect output are not available",
        ),
    )
}
