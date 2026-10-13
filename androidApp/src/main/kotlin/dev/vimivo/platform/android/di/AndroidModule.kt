@file:Suppress("ImportOrdering")

package dev.vimivo.platform.android.di

import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.domain.repository.PlaylistRepository
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.database.VimivoDatabase
import dev.vimivo.platform.android.library.AndroidPlaylistRepository
import dev.vimivo.platform.android.library.AndroidMediaStoreRepository
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.LibraryViewModel
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.MediaStoreScanner
import dev.vimivo.platform.android.library.PlaylistViewModel
import dev.vimivo.platform.android.library.SafMediaScanner
import dev.vimivo.platform.android.playback.LibVlcPlayerEngineFactory
import dev.vimivo.platform.android.playback.Media3PlayerEngineFactory
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.platform.android.playback.PlaybackStateStore
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.settings.SettingsViewModel
import dev.vimivo.platform.android.network.NetworkViewModel
import dev.vimivo.platform.android.settings.AndroidSettingsRepository
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import androidx.room.Room

val androidModule = module {
    single {
        Room.databaseBuilder(get(), VimivoDatabase::class.java, "vimivo-library.db")
            .build()
    }
    single { get<VimivoDatabase>().libraryDao() }
    single { get<VimivoDatabase>().playlistDao() }
    single { MediaPermissionPolicy(get()) }
    single { MediaStoreScanner(get(), get()) }
    single { SafMediaScanner(get(), get()) }
    single { AndroidThumbnailRepository(get()) }
    single<MediaRepository> { AndroidMediaStoreRepository(get(), get(), get(), get(), get(), get()) }
    single<PlaylistRepository> { AndroidPlaylistRepository(get(), get()) }
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    single { Media3PlayerEngineFactory(get()) }
    single { LibVlcPlayerEngineFactory(get()) }
    single { PlaybackStateStore(get()) }
    single { PlaybackController(get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    viewModel { HomeViewModel(get()) }
    viewModel { LibraryViewModel(get()) }
    viewModel { PlaybackViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { NetworkViewModel(get(), get()) }
    viewModel { PlaylistViewModel(get(), get()) }
}
