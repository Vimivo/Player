package dev.vimivo.desktop.player

import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.desktop.library.DesktopLibraryController
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlayerEngine
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame

class DesktopPlayerModuleTest {
    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun isolatedGraphResolvesAndSharesOneEngine() {
        val koin = startKoin { modules(desktopPlayerModule(isolated = true)) }.koin

        val viewModel = koin.get<PlayerViewModel>()
        koin.get<MprisService>()
        koin.get<PlaybackQueueController>()

        assertSame(koin.get<PlayerEngine>(), koin.get<dev.vimivo.desktop.playback.MpvPlayerEngine>())
        assertSame(viewModel, koin.get<PlayerViewModel>())
    }

    @Test
    fun playerGraphNeverCreatesTheLibrary() {
        var libraryCreated = false
        val koin = startKoin {
            allowOverride(true)
            modules(
                desktopPlayerModule(isolated = true),
                module {
                    single {
                        libraryCreated = true
                        DesktopLibraryController(get(), get(), closeDatabase = false)
                    }
                },
            )
        }.koin

        koin.get<PlayerViewModel>()
        koin.get<MprisService>()

        assertFalse(libraryCreated, "Opening a file must not create, load or index the library")
    }
}
