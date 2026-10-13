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

    fun play(item: VimivoMediaItem, positionMillis: Long = item.safeResumePosition()) {
        withController {
            setMediaItem(item.toMedia3(), positionMillis)
            prepare()
            play()
        }
    }

    fun playQueue(
        items: List<VimivoMediaItem>,
        index: Int = 0,
        shuffle: Boolean = false,
        positionMillis: Long = items.getOrNull(index)?.safeResumePosition() ?: 0,
    ) {
        if (items.isEmpty()) return
        withController {
            shuffleModeEnabled = shuffle
            setMediaItems(items.map(VimivoMediaItem::toMedia3), index.coerceIn(items.indices), positionMillis)
            prepare()
            play()
        }
    }

    fun addToQueue(items: List<VimivoMediaItem>) {
        if (items.isEmpty()) return
        withController { addMediaItems(items.map(VimivoMediaItem::toMedia3)) }
    }

    fun playNext(items: List<VimivoMediaItem>) {
        if (items.isEmpty()) return
        withController {
            val index = (currentMediaItemIndex + 1).coerceAtLeast(0)
            addMediaItems(index, items.map(VimivoMediaItem::toMedia3))
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

internal fun VimivoMediaItem.safeResumePosition(): Long {
    val duration = durationMillis ?: return lastPlaybackPositionMillis
    return lastPlaybackPositionMillis.takeUnless { it >= duration - 10_000 } ?: 0
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
