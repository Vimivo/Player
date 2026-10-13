package dev.vimivo.platform.android.di

import dev.vimivo.domain.repository.MediaRepository
import dev.vimivo.features.home.HomeViewModel
import dev.vimivo.platform.android.library.AndroidMediaStoreRepository
import dev.vimivo.platform.android.settings.AndroidSettingsRepository
import dev.vimivo.settings.SettingsRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    single<MediaRepository> { AndroidMediaStoreRepository(get()) }
    single<SettingsRepository> { AndroidSettingsRepository(get()) }
    viewModel { HomeViewModel(get()) }
}
