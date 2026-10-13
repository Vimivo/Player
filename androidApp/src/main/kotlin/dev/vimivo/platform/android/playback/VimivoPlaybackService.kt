@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@file:Suppress("ImportOrdering", "UnstableApiUsage")

package dev.vimivo.platform.android.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.vimivo.core.model.LibraryQuery
import dev.vimivo.core.model.MediaKind
import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import dev.vimivo.settings.SettingsRepository

class VimivoPlaybackService : MediaLibraryService() {
    private val media3Factory: Media3PlayerEngineFactory by inject()
    private val libVlcFactory: LibVlcPlayerEngineFactory by inject()
    private val mediaRepository: MediaRepository by inject()
    private val stateStore: PlaybackStateStore by inject()
    private val diagnostics: PlaybackDiagnostics by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionPlayer: SessionPlayerAdapter
    private lateinit var mediaSession: MediaLibrarySession

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) sessionPlayer.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val engineManager = PlaybackEngineManager(
            factories = mapOf(EngineId.MEDIA3 to media3Factory, EngineId.LIBVLC to libVlcFactory),
            scope = serviceScope,
        )
        diagnostics.attach(engineManager)
        sessionPlayer = SessionPlayerAdapter(engineManager, serviceScope, stateStore)
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, LibraryCallback()).build()
        registerNoisyReceiver()
        serviceScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.restoreQueue) sessionPlayer.restoreWithoutPlaying()
            sessionPlayer.applyDefaults(settings.shuffleBehavior, settings.repeatBehavior)
        }
        serviceScope.launch {
            var lastSavedAt = 0L
            engineManager.state.collect { state ->
                diagnostics.publish(state)
                val media = state.media ?: return@collect
                val completed = state.status == PlaybackStatus.Completed
                if (!completed && state.status != PlaybackStatus.Playing && state.status != PlaybackStatus.Paused) {
                    return@collect
                }
                val now = SystemClock.elapsedRealtime()
                if (completed || now - lastSavedAt >= HISTORY_SAVE_INTERVAL_MILLIS) {
                    mediaRepository.recordPlayback(media.id, state.positionMillis, completed)
                    lastSavedAt = now
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!sessionPlayer.playWhenReady) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        mediaSession.release()
        sessionPlayer.release()
        diagnostics.detach()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, filter)
        }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            LibraryResult.ofItem(folder(ROOT_ID, "Vimivo library"), params),
        )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future(Dispatchers.IO) {
            val result = when (parentId) {
                ROOT_ID -> listOf(folder(AUDIO_ID, "Songs"), folder(VIDEO_ID, "Videos"))
                AUDIO_ID -> libraryPage(MediaKind.AUDIO, page, pageSize)
                VIDEO_ID -> libraryPage(MediaKind.VIDEO, page, pageSize)
                else -> emptyList()
            }
            LibraryResult.ofItemList(result, params)
        }

        private suspend fun libraryPage(kind: MediaKind, page: Int, pageSize: Int): List<MediaItem> =
            mediaRepository.page(
                LibraryQuery(kind = kind, offset = page * pageSize, limit = pageSize.coerceAtMost(500)),
            ).getOrThrow().items.map { item ->
                MediaItem.Builder()
                    .setMediaId(item.id.value)
                    .setUri(item.uri)
                    .setMimeType(item.mimeType)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setAlbumTitle(item.album)
                            .setArtworkUri(item.artworkUri?.let(android.net.Uri::parse))
                            .setIsPlayable(true)
                            .build(),
                    )
                    .build()
            }
    }

    private companion object {
        const val ROOT_ID = "root"
        const val AUDIO_ID = "audio"
        const val VIDEO_ID = "video"
        const val HISTORY_SAVE_INTERVAL_MILLIS = 5_000L

        fun folder(id: String, title: String) = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build())
            .build()
    }
}
