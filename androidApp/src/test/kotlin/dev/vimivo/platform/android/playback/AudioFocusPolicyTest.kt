package dev.vimivo.platform.android.playback

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusPolicyTest {
    @Test
    fun transientLossResumesOnlyWhenPlaybackWasActive() {
        val policy = AudioFocusPolicy()
        assertTrue(policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, wasPlaying = true).pause)
        assertTrue(policy.onChange(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false).resume)

        policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, wasPlaying = false)
        assertFalse(policy.onChange(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false).resume)
    }

    @Test
    fun permanentLossNeverAutoResumesAndDuckIsReversedOnGain() {
        val policy = AudioFocusPolicy()
        policy.onChange(AudioManager.AUDIOFOCUS_LOSS, wasPlaying = true)
        assertFalse(policy.onChange(AudioManager.AUDIOFOCUS_GAIN, wasPlaying = false).resume)
        assertTrue(policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, false).duck == true)
        assertTrue(policy.onChange(AudioManager.AUDIOFOCUS_GAIN, false).duck == false)
    }
}
