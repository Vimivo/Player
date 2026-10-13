package dev.vimivo.platform.android.playback

import android.media.AudioManager

internal data class AudioFocusAction(
    val pause: Boolean = false,
    val resume: Boolean = false,
    val duck: Boolean? = null,
)

internal class AudioFocusPolicy {
    private var resumeOnGain = false

    fun onChange(change: Int, wasPlaying: Boolean): AudioFocusAction = when (change) {
        AudioManager.AUDIOFOCUS_LOSS -> {
            resumeOnGain = false
            AudioFocusAction(pause = wasPlaying)
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            resumeOnGain = wasPlaying
            AudioFocusAction(pause = wasPlaying)
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusAction(duck = true)
        AudioManager.AUDIOFOCUS_GAIN -> AudioFocusAction(resume = resumeOnGain, duck = false).also {
            resumeOnGain = false
        }
        else -> AudioFocusAction()
    }
}
