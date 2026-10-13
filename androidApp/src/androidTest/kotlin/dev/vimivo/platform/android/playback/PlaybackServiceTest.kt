package dev.vimivo.platform.android.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @Test
    fun controllerOwnsQueueModesAndRestoresWithoutAutoplay() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, VimivoPlaybackService::class.java)),
        ).buildAsync()
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            val first = MediaItem.Builder().setMediaId("one").setUri("file:///missing-one.mp3").build()
            val second = MediaItem.Builder().setMediaId("two").setUri("file:///missing-two.mp3").build()
            controller.setMediaItems(listOf(first, second))
            controller.addMediaItem(1, first)
            controller.moveMediaItem(2, 0)
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = true

            assertEquals(3, controller.mediaItemCount)
            assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode)
            assertTrue(controller.shuffleModeEnabled)
            assertFalse(controller.playWhenReady)

            controller.removeMediaItem(1)
            assertEquals(2, controller.mediaItemCount)
        } finally {
            controller.release()
        }
    }
}
