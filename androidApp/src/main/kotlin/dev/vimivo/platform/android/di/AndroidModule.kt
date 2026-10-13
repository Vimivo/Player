package dev.vimivo.platform.android.di

import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.library.AndroidMediaStoreRepository
import dev.vimivo.platform.android.playback.LibVlcPlayerEngineFactory
import dev.vimivo.platform.android.playback.Media3PlayerEngineFactory
import dev.vimivo.platform.android.settings.AndroidSettingsRepository
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackEngineManager
import dev.vimivo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    single<MediaRepository> { AndroidMediaStoreRepository(get()) }
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    single { Media3PlayerEngineFactory(get()) }
    single { LibVlcPlayerEngineFactory(get()) }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single {
        PlaybackEngineManager(
            factories = mapOf(
                EngineId.MEDIA3 to get<Media3PlayerEngineFactory>(),
                EngineId.LIBVLC to get<LibVlcPlayerEngineFactory>(),
            ),
            scope = get(),
        )
    }
    viewModel { HomeViewModel(get()) }
}
