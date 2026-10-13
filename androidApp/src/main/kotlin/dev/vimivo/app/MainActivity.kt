@file:Suppress("ImportOrdering")

package dev.vimivo.app

import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.ui.VimivoAndroidApp
import dev.vimivo.platform.android.playback.PlaybackController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val playbackController: PlaybackController by inject()
    private val thumbnailRepository: AndroidThumbnailRepository by inject()
    private val permissionPolicy: MediaPermissionPolicy by inject()
    private var videoPlayerActive = false
    private var videoPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VimivoAndroidApp(
                playbackController,
                thumbnailRepository,
                permissionPolicy,
                onVideoPlayerState = { active, playing ->
                    videoPlayerActive = active
                    videoPlaying = playing
                },
                enterPictureInPicture = ::enterVideoPictureInPicture,
            )
        }
    }

    override fun onUserLeaveHint() {
        if (videoPlayerActive && videoPlaying) enterVideoPictureInPicture()
        super.onUserLeaveHint()
    }

    private fun enterVideoPictureInPicture() {
        if (!packageManager.hasSystemFeature("android.software.picture_in_picture")) return
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(videoPlayerActive && videoPlaying)
        }
        enterPictureInPictureMode(builder.build())
    }
}
