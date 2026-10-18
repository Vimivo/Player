package dev.vimivo.desktop.player

import dev.vimivo.desktop.integration.mpris.MprisService
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlayerEngine
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
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
}
