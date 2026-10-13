@file:Suppress("ImportOrdering", "UnstableApiUsage")

package dev.vimivo.platform.android.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.vimivo.core.model.MediaItem as VimivoMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackController(context: Context) : AutoCloseable {
    private val mutableController = MutableStateFlow<MediaController?>(null)
    private val future = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, VimivoPlaybackService::class.java)),
    ).buildAsync()

    val controller: StateFlow<MediaController?> = mutableController.asStateFlow()

    init {
        future.addListener(
            { runCatching { future.get() }.onSuccess { mutableController.value = it } },
            MoreExecutors.directExecutor(),
        )
    }

    fun play(item: VimivoMediaItem, positionMillis: Long = item.lastPlaybackPositionMillis) {
        withController {
            setMediaItem(item.toMedia3(), positionMillis)
            prepare()
            play()
        }
    }

    private fun withController(block: MediaController.() -> Unit) {
        mutableController.value?.let { controller ->
            controller.block()
            return
        }
        future.addListener(
            { runCatching { future.get() }.onSuccess { controller -> controller.block() } },
            MoreExecutors.directExecutor(),
        )
    }

    override fun close() {
        mutableController.value?.release()
        mutableController.value = null
        future.cancel(true)
    }
}

fun VimivoMediaItem.toMedia3(): MediaItem = MediaItem.Builder()
    .setMediaId(id.value)
    .setUri(uri)
    .setMimeType(mimeType)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri?.let(android.net.Uri::parse))
            .setIsPlayable(true)
            .build(),
    )
    .build()
