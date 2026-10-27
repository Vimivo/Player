package dev.vimivo.platform.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Audio focus for engines that do not manage it themselves (Media3 does): pauses on loss, ducks on "may duck", and
 * resumes after a transient loss, following [AudioFocusPolicy].
 */
internal class MediaAudioFocusController(
    context: Context,
    private val onPause: () -> Boolean,
    private val onResume: () -> Unit,
    private val onDuck: (Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val policy = AudioFocusPolicy()
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val wasPlaying = if (change in setOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) {
            onPause()
        } else {
            false
        }
        val action = policy.onChange(change, wasPlaying)
        action.duck?.let(onDuck)
        if (action.resume) onResume()
    }
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(listener)
        .build()

    fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    fun abandon() = Unit.also { audioManager.abandonAudioFocusRequest(request) }
}
