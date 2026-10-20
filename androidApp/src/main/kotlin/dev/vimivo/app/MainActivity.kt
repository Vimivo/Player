@file:Suppress("ImportOrdering")

package dev.vimivo.app

import android.os.Bundle
import android.os.Build
import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.vimivo.platform.android.library.ui.ExternalOpenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.ui.VimivoAndroidApp
import dev.vimivo.platform.android.playback.PlaybackController
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val playbackController: PlaybackController by inject()
    private val thumbnailRepository: AndroidThumbnailRepository by inject()
    private val permissionPolicy: MediaPermissionPolicy by inject()
    private val folderSiblings by lazy { AndroidFolderSiblings(applicationContext) }
    private var videoPlayerActive = false
    private var videoPlaying = false
    private var externalOpen by mutableStateOf<ExternalOpenRequest?>(null)

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
                externalOpen = externalOpen,
            )
        }
        // A recreated activity must not replay the file it was originally launched with.
        if (savedInstanceState == null) openExternalMedia(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openExternalMedia(intent)
    }

    private fun openExternalMedia(intent: Intent?) {
        val data = intent?.data ?: return
        lifecycleScope.launch {
            val displayName = withContext(Dispatchers.IO) { displayName(data) }
            val mimeType = intent.type ?: contentResolver.getType(data)
            val request = ExternalMediaRequest.parse(intent.action, data.toString(), mimeType, displayName)
                ?: return@launch
            // Like desktop: the opened file, then the rest of its folder ("Up Next"), in natural order.
            val queue = withContext(Dispatchers.IO) { folderSiblings.queueFor(request.media) }
            playbackController.playQueue(queue.items, queue.startIndex)
            externalOpen = ExternalOpenRequest(System.nanoTime(), request.media.kind)
        }
    }

    private fun displayName(uri: Uri): String? = if (uri.scheme != "content") {
        uri.lastPathSegment
    } else {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.onFailure { error -> Log.w(TAG, "Could not read the opened file's name", error) }.getOrNull()
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

    private companion object {
        const val TAG = "VimivoMainActivity"
    }
}
