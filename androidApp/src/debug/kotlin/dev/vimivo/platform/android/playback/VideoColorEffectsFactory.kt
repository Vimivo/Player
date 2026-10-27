package dev.vimivo.platform.android.playback

import androidx.media3.exoplayer.ExoPlayer

internal fun createVideoColorEffects(
    player: ExoPlayer,
    video: Media3VideoOutput,
    onChanged: () -> Unit,
): VideoColorEffects = Media3VideoColorEffects(player, video, onChanged)
