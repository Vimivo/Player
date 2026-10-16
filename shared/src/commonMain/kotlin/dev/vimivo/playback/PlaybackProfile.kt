@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength")

package dev.vimivo.playback

data class PlaybackProfile(
    val id: String,
    val name: String,
    val speed: Float = 1.0f,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val subtitleEnabled: Boolean = true,
    val description: String = "",
) {
    fun applyTo(engine: PlayerEngine): Result<Unit> = runCatching {
        engine.setPlaybackSpeed(speed)
        engine.setReplayGainMode(replayGainMode)
        engine.applyAudioEnhancements(
            AudioEnhancementSettings(
                enabled = equalizerPreset != EqualizerPreset.FLAT,
                preset = equalizerPreset,
                replayGainMode = replayGainMode,
            ),
        )
        engine.applyVideoAdjustments(
            VideoAdjustments(
                aspectRatio = aspectRatio,
            ),
        )
        if (!subtitleEnabled) {
            engine.selectSubtitleTrack(null)
        }
    }

    companion object {
        val DEFAULT = PlaybackProfile(
            id = "default",
            name = "Default",
            speed = 1.0f,
            equalizerPreset = EqualizerPreset.FLAT,
            aspectRatio = VideoAspectRatio.ORIGINAL,
            replayGainMode = ReplayGainMode.OFF,
            subtitleEnabled = true,
            description = "Standard neutral playback with default aspect ratio and speed.",
        )

        val MUSIC_FOCUS = PlaybackProfile(
            id = "music_focus",
            name = "Music Focus",
            speed = 1.0f,
            equalizerPreset = EqualizerPreset.ROCK,
            aspectRatio = VideoAspectRatio.ORIGINAL,
            replayGainMode = ReplayGainMode.TRACK,
            subtitleEnabled = false,
            description = "Optimized for audio listening with Track ReplayGain normalization.",
        )

        val CINEMA = PlaybackProfile(
            id = "cinema",
            name = "Cinema",
            speed = 1.0f,
            equalizerPreset = EqualizerPreset.CLASSICAL,
            aspectRatio = VideoAspectRatio.RATIO_16_9,
            replayGainMode = ReplayGainMode.OFF,
            subtitleEnabled = true,
            description = "Widescreen 16:9 presentation with cinematic equalization and active subtitles.",
        )

        val PODCAST_LECTURE = PlaybackProfile(
            id = "podcast_lecture",
            name = "Podcast & Lecture",
            speed = 1.25f,
            equalizerPreset = EqualizerPreset.VOCAL,
            aspectRatio = VideoAspectRatio.ORIGINAL,
            replayGainMode = ReplayGainMode.TRACK,
            subtitleEnabled = false,
            description = "Vocal-boosted EQ with 1.25x speed and pitch correction for speech clarity.",
        )

        val BUILTIN_PROFILES = listOf(DEFAULT, MUSIC_FOCUS, CINEMA, PODCAST_LECTURE)
    }
}
