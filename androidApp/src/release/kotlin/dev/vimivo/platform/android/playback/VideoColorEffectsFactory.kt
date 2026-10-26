package dev.vimivo.platform.android.playback

import androidx.media3.exoplayer.ExoPlayer

@Suppress("UNUSED_PARAMETER")
internal fun createVideoColorEffects(
    player: ExoPlayer,
    video: Media3VideoOutput,
    onChanged: () -> Unit,
): VideoColorEffects = DisabledVideoColorEffects
