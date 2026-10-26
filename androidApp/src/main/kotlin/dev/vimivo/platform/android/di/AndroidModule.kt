@file:Suppress("ImportOrdering")

package dev.vimivo.platform.android.di

import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.domain.repository.PlaylistRepository
import dev.vimivo.domain.repository.OrganizationRepository
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.database.VimivoDatabase
import dev.vimivo.platform.android.library.AndroidPlaylistRepository
import dev.vimivo.platform.android.library.AndroidOrganizationRepository
import dev.vimivo.platform.android.library.AndroidMediaStoreRepository
import dev.vimivo.platform.android.library.AndroidThumbnailRepository
import dev.vimivo.platform.android.library.LibraryViewModel
import dev.vimivo.platform.android.library.MediaPermissionPolicy
import dev.vimivo.platform.android.library.MediaStoreScanner
import dev.vimivo.platform.android.library.PlaylistViewModel
import dev.vimivo.platform.android.library.PlaylistDocuments
import dev.vimivo.platform.android.library.SafMediaScanner
import dev.vimivo.platform.android.playback.LibmpvPlayerEngineFactory
import dev.vimivo.platform.android.playback.AndroidSubtitleRepository
import dev.vimivo.platform.android.playback.Media3PlayerEngineFactory
import dev.vimivo.platform.android.playback.PlaybackController
import dev.vimivo.platform.android.playback.AndroidSnapshotStore
import dev.vimivo.platform.android.playback.PlaybackDiagnostics
import dev.vimivo.platform.android.playback.DiagnosticsSleepTimerPlayer
import dev.vimivo.playback.PlaybackSleepTimer
import dev.vimivo.platform.android.playback.LyricsRepository
import dev.vimivo.platform.android.playback.PlaybackStateStore
import dev.vimivo.platform.android.playback.PlaybackViewModel
import dev.vimivo.platform.android.settings.SettingsViewModel
import dev.vimivo.platform.android.network.NetworkViewModel
import dev.vimivo.platform.android.network.NetworkHistoryStore
import dev.vimivo.platform.android.network.AndroidNetworkSourceRepository
import dev.vimivo.platform.android.network.NetworkCredentialVault
import dev.vimivo.platform.android.settings.AndroidSettingsRepository
import dev.vimivo.platform.android.settings.LocalBackupManager
import dev.vimivo.settings.SettingsRepository
import dev.vimivo.network.NetworkSourceRepository
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
    single { get<VimivoDatabase>().organizationDao() }
    single { MediaPermissionPolicy(get()) }
    single { MediaStoreScanner(get(), get()) }
    single { SafMediaScanner(get(), get(), get()) }
    single { AndroidThumbnailRepository(get()) }
    single<MediaRepository> { AndroidMediaStoreRepository(get(), get(), get(), get(), get(), get()) }
    single<PlaylistRepository> { AndroidPlaylistRepository(get(), get()) }
    single<OrganizationRepository> { AndroidOrganizationRepository(get()) }
    single { PlaylistDocuments(get(), get(), get()) }
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    single { NetworkHistoryStore(get()) }
    single { NetworkCredentialVault(get()) }
    single<NetworkSourceRepository> { AndroidNetworkSourceRepository(get(), get()) }
    single { LocalBackupManager(get(), get(), get(), get(), get(), get(), get()) }
    single { Media3PlayerEngineFactory(get()) }
    single { LibmpvPlayerEngineFactory(get(), get()) }
    single { AndroidSubtitleRepository(get(), get()) }
    single { PlaybackStateStore(get()) }
    single { PlaybackController(get(), get()) }
    single { PlaybackDiagnostics() }
    single { AndroidSnapshotStore(get()) }
    single {
        PlaybackSleepTimer(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            System::currentTimeMillis,
            DiagnosticsSleepTimerPlayer(get()),
        )
    }
    single { LyricsRepository(get()) }
    single { dev.vimivo.platform.android.playback.AndroidWaveformRepository(get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    viewModel { HomeViewModel(get()) }
    viewModel { LibraryViewModel(get(), get()) }
    viewModel { PlaybackViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { NetworkViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PlaylistViewModel(get(), get(), get()) }
}
