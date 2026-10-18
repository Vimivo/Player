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
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.ResumePolicy
import dev.vimivo.settings.AppSettings
import dev.vimivo.settings.ResumeBehavior
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackController(
    context: Context,
    settingsRepository: SettingsRepository,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settings = AppSettings()
    private val mutableController = MutableStateFlow<MediaController?>(null)
    private val future = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, VimivoPlaybackService::class.java)),
    ).buildAsync()

    val controller: StateFlow<MediaController?> = mutableController.asStateFlow()

    init {
        scope.launch { settingsRepository.settings.collect { settings = it } }
        future.addListener(
            { runCatching { future.get() }.onSuccess { mutableController.value = it } },
            MoreExecutors.directExecutor(),
        )
    }

    fun play(item: VimivoMediaItem, positionMillis: Long? = null) {
        withController {
            setMediaItem(item.toMedia3(), positionMillis ?: item.preferredResumePosition())
            prepare()
            play()
        }
    }

    fun playQueue(
        items: List<VimivoMediaItem>,
        index: Int = 0,
        shuffle: Boolean = false,
        positionMillis: Long? = null,
    ) {
        if (items.isEmpty()) return
        withController {
            shuffleModeEnabled = shuffle
            val start = positionMillis ?: items.getOrNull(index)?.preferredResumePosition() ?: 0
            setMediaItems(items.map(VimivoMediaItem::toMedia3), index.coerceIn(items.indices), start)
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

    fun retry() = withController {
        prepare()
        play()
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
        scope.cancel()
    }

    private fun VimivoMediaItem.preferredResumePosition(): Long {
        val behavior = if (kind == MediaKind.VIDEO) settings.videoResumeBehavior else settings.musicResumeBehavior
        return if (behavior == ResumeBehavior.ALWAYS) safeResumePosition() else 0
    }
}

/** Only called when the user chose to always resume this kind of media, so songs are included. */
internal fun VimivoMediaItem.safeResumePosition(): Long = ResumePolicy.startPositionMillis(
    kind = kind,
    savedPositionMillis = lastPlaybackPositionMillis,
    durationMillis = durationMillis,
    completed = false,
    includeShortAudio = true,
)

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
