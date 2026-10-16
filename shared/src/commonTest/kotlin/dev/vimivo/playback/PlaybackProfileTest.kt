@file:Suppress("MaximumLineLength", "MaxLineLength")

package dev.vimivo.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackProfileTest {

    @Test
    fun testBuiltinProfilesExistAndConfiguredCorrectly() {
        val profiles = PlaybackProfile.BUILTIN_PROFILES
        assertEquals(4, profiles.size)

        val defaultProfile = PlaybackProfile.DEFAULT
        assertEquals(1.0f, defaultProfile.speed)
        assertEquals(EqualizerPreset.FLAT, defaultProfile.equalizerPreset)
        assertEquals(VideoAspectRatio.ORIGINAL, defaultProfile.aspectRatio)
        assertEquals(ReplayGainMode.OFF, defaultProfile.replayGainMode)
        assertTrue(defaultProfile.subtitleEnabled)

        val musicProfile = PlaybackProfile.MUSIC_FOCUS
        assertEquals(ReplayGainMode.TRACK, musicProfile.replayGainMode)
        assertFalse(musicProfile.subtitleEnabled)

        val cinemaProfile = PlaybackProfile.CINEMA
        assertEquals(VideoAspectRatio.RATIO_16_9, cinemaProfile.aspectRatio)
        assertEquals(EqualizerPreset.CLASSICAL, cinemaProfile.equalizerPreset)

        val podcastProfile = PlaybackProfile.PODCAST_LECTURE
        assertEquals(1.25f, podcastProfile.speed)
        assertEquals(EqualizerPreset.VOCAL, podcastProfile.equalizerPreset)
    }
}
