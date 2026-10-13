@file:Suppress("ImportOrdering")

package dev.vimivo.app

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VimivoAndroidApp(playbackController, thumbnailRepository, permissionPolicy) }
    }
}
