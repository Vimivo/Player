package dev.vimivo.desktop.player

import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.desktop.integration.mpris.MprisServiceFactory
import dev.vimivo.desktop.library.DesktopDatabase
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.desktop.library.DesktopRecentMediaRepository
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File
import java.net.URI

/** Remote media is always attempted; local files must still exist (they may have been moved or deleted). */
private fun isPlayable(media: dev.vimivo.core.model.MediaItem): Boolean {
    if (!media.isAvailable) return false
    if (!media.uri.startsWith("file:")) return true
    return runCatching { File(URI(media.uri)).isFile }.getOrDefault(false)
}

/** Koin qualifier for the application-lifetime scope that runs on the Swing/Compose event thread. */
internal val PlayerScope = named("playerScope")

/**
 * The desktop player graph. [isolated] swaps persistent storage for in-memory stores, for smoke tests and
 * other verification runs that must never touch the user's library.
 */
internal fun desktopPlayerModule(isolated: Boolean): Module = module {
    single(PlayerScope) { CoroutineScope(SupervisorJob() + Dispatchers.Main) } onClose { it?.cancel() }
    single { MpvPlayerEngine() } bind PlayerEngine::class
    single {
        if (isolated) DesktopLibraryController(DesktopDatabase.inMemory()) else DesktopLibraryController()
    }
    single { DesktopRecentMediaRepository(persistent = !isolated) }
    single {
        PlaybackQueueController(get(), get(PlayerScope), isAvailable = ::isPlayable)
    }
    single { PlayerViewModel(get(), get(), get(), get(), get(PlayerScope)) } onClose { it?.close() }
    single<MprisService> {
        val viewModel = get<PlayerViewModel>()
        MprisServiceFactory.create(get(), get(), viewModel::togglePlayback).also(viewModel::attachMediaControls)
    }
}
