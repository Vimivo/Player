@file:Suppress("ArgumentListWrapping", "ParameterListWrapping", "MaxLineLength", "MaximumLineLength", "TooManyFunctions", "Wrapping")

package dev.vimivo.desktop.playback

import androidx.compose.ui.input.key.Key
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.player.HudMessage
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.AudioEnhancementSettings
import dev.vimivo.playback.AudioEnhancementState
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SeekController
import dev.vimivo.playback.SnapshotFormat
import dev.vimivo.playback.SnapshotOptions
import dev.vimivo.playback.SupportLevel
import dev.vimivo.playback.TrackInfo
import dev.vimivo.playback.TrackType
import dev.vimivo.playback.VideoAdjustments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopPlayerCommandsTest {
    @Test
    fun documentedShortcutsResolve() {
        fun resolve(key: Key, shift: Boolean = false) = DesktopPlayerShortcuts.resolve(key, shift, ctrl = false, alt = false, meta = false)
        assertEquals(DesktopPlayerCommand.TOGGLE_PLAYBACK, resolve(Key.Spacebar))
        assertEquals(DesktopPlayerCommand.SEEK_BACK_SHORT, resolve(Key.DirectionLeft))
        assertEquals(DesktopPlayerCommand.SEEK_FORWARD_LONG, resolve(Key.DirectionRight, shift = true))
        assertEquals(DesktopPlayerCommand.VOLUME_UP, resolve(Key.DirectionUp))
        assertEquals(DesktopPlayerCommand.TOGGLE_MUTE, resolve(Key.M))
        assertEquals(DesktopPlayerCommand.TOGGLE_SHUFFLE, resolve(Key.S))
        assertEquals(DesktopPlayerCommand.SNAPSHOT, resolve(Key.S, shift = true))
        assertEquals(DesktopPlayerCommand.CYCLE_REPEAT, resolve(Key.R))
        assertEquals(DesktopPlayerCommand.SPEED_UP, resolve(Key.RightBracket))
        assertEquals(DesktopPlayerCommand.SPEED_DOWN, resolve(Key.LeftBracket))
        assertEquals(DesktopPlayerCommand.SPEED_RESET, resolve(Key.Backspace))
        assertEquals(DesktopPlayerCommand.TOGGLE_FULLSCREEN, resolve(Key.F))
        assertEquals(DesktopPlayerCommand.FRAME_STEP_FORWARD, resolve(Key.E))
        assertEquals(DesktopPlayerCommand.FRAME_STEP_BACKWARD, resolve(Key.E, shift = true))
        assertNull(resolve(Key.Q))
    }

    @Test
    fun modifiedKeysAreLeftToTheMenuBar() {
        assertNull(DesktopPlayerShortcuts.resolve(Key.O, shift = false, ctrl = true, alt = false, meta = false))
        assertNull(DesktopPlayerShortcuts.resolve(Key.DirectionRight, shift = false, ctrl = false, alt = false, meta = true))
        assertNull(DesktopPlayerShortcuts.resolve(Key.DirectionLeft, shift = false, ctrl = false, alt = true, meta = false))
    }

    @Test
    fun mediaCommandsAreIgnoredWithoutMedia() {
        val engine = FakeEngine()
        val executor = executor(engine)
        assertFalse(executor.execute(DesktopPlayerCommand.SEEK_FORWARD_SHORT))
        assertFalse(executor.execute(DesktopPlayerCommand.MEDIA_INFO))
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun seekVolumeAndSpeedUseSharedPolicy() {
        val engine = FakeEngine(loaded(position = 2_000, duration = 60_000, volume = 0.5f, speed = 1f))
        val executor = executor(engine)
        executor.execute(DesktopPlayerCommand.SEEK_BACK_SHORT)
        executor.execute(DesktopPlayerCommand.VOLUME_UP)
        executor.execute(DesktopPlayerCommand.SPEED_UP)
        assertEquals(listOf("seek:0", "volume:0.55", "speed:1.25"), engine.calls)
        assertEquals<HudMessage?>(HudMessage.Speed(1.25f), executor.status.value)
    }

    @Test
    fun muteRestoresPreviousVolume() {
        val engine = FakeEngine(loaded(volume = 0.4f))
        val executor = executor(engine)
        executor.execute(DesktopPlayerCommand.TOGGLE_MUTE)
        executor.execute(DesktopPlayerCommand.TOGGLE_MUTE)
        assertEquals(listOf("volume:0.0", "volume:0.4"), engine.calls)
    }

    @Test
    fun trackCyclingAndDelaysUpdateEngine() {
        val subs = listOf(TrackInfo("1", TrackType.SUBTITLE, "English", isSelected = true))
        val engine = FakeEngine(loaded().copy(subtitleTracks = subs, subtitleDelayMillis = 200))
        val executor = executor(engine)
        executor.execute(DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK)
        executor.execute(DesktopPlayerCommand.SUBTITLE_DELAY_UP)
        executor.execute(DesktopPlayerCommand.CYCLE_AUDIO_TRACK)
        assertEquals(listOf("sid:null", "subdelay:300"), engine.calls)
        assertEquals<HudMessage?>(HudMessage.NoAudioTracks, executor.status.value)
    }

    @Test
    fun abLoopFrameStepAspectAndDeinterlace() {
        val engine = FakeEngine(loaded(position = 5_000))
        val executor = executor(engine)
        executor.execute(DesktopPlayerCommand.AB_LOOP)
        executor.execute(DesktopPlayerCommand.FRAME_STEP_BACKWARD)
        executor.execute(DesktopPlayerCommand.CYCLE_ASPECT_RATIO)
        executor.execute(DesktopPlayerCommand.TOGGLE_DEINTERLACE)
        assertEquals(listOf("ab:5000-null-false", "frame:false", "aspect:FIT", "deinterlace:true"), engine.calls)
    }

    @Test
    fun snapshotIsWrittenToSnapshotDirectory() {
        val directory = Files.createTempDirectory("vimivo-snapshots").toFile().resolve("Vimivo")
        val engine = FakeEngine(loaded())
        val executor = executor(engine, snapshotFile = { directory.resolve("vimivo-snapshot-42.png") })
        executor.execute(DesktopPlayerCommand.SNAPSHOT)
        assertTrue(directory.isDirectory)
        assertEquals("snap:${directory.resolve("vimivo-snapshot-42.png").absolutePath}:PNG:false", engine.calls.single())
        directory.parentFile.deleteRecursively()
    }

    @Test
    fun snapshotOptionsReachTheEngineAndChooseTheFolderAndExtension() {
        val directory = Files.createTempDirectory("vimivo-snapshot-options").toFile()
        val options = SnapshotOptions(SnapshotFormat.JPEG, includeSubtitles = true, folder = directory.absolutePath)
        val engine = FakeEngine(loaded())
        val plan = SnapshotPlan(options = { options })
        DesktopPlayerCommandExecutor(engine, {}, seekVia(engine), {}, {}, NoFullscreen, plan).execute(DesktopPlayerCommand.SNAPSHOT)
        val call = engine.calls.single()
        assertTrue(call.startsWith("snap:${directory.absolutePath}${java.io.File.separator}"), call)
        assertTrue(call.endsWith(".jpg:JPEG:true"), call)
        directory.deleteRecursively()
    }

    @Test
    fun snapshotNamesCarryTheTitleAndPosition() {
        val directory = Files.createTempDirectory("vimivo-snapshot-name").toFile()
        val engine = FakeEngine(loaded())
        val plan = SnapshotPlan(options = { SnapshotOptions(folder = directory.absolutePath) })
        DesktopPlayerCommandExecutor(engine, {}, seekVia(engine), {}, {}, NoFullscreen, plan).execute(DesktopPlayerCommand.SNAPSHOT)
        val name = java.io.File(engine.calls.single().removePrefix("snap:").substringBefore(":PNG")).name
        assertTrue(name.matches(Regex(".+-\\dh\\d\\dm\\d\\ds-\\d+\\.png")), name)
        directory.deleteRecursively()
    }

    @Test
    fun failuresAreReportedInsteadOfSwallowed() {
        val engine = FakeEngine(loaded(), failFrameStep = true)
        val executor = executor(engine)
        assertTrue(executor.execute(DesktopPlayerCommand.FRAME_STEP_FORWARD))
        assertEquals<HudMessage?>(HudMessage.Failed("Next frame", "no frames"), executor.status.value)
    }

    @Test
    fun escapeExitsFullscreenThenClosesMediaInfo() {
        var fullscreen = true
        val engine = FakeEngine(loaded())
        val executor = DesktopPlayerCommandExecutor(
            engine, {}, seekVia(engine), {}, {},
            object : DesktopFullscreenControl {
                override fun toggle() { fullscreen = !fullscreen }
                override fun exit() = fullscreen.also { fullscreen = false }
            },
        )
        executor.execute(DesktopPlayerCommand.MEDIA_INFO)
        assertTrue(executor.mediaInfoVisible.value)
        assertTrue(executor.execute(DesktopPlayerCommand.EXIT_FULLSCREEN))
        assertFalse(fullscreen)
        assertTrue(executor.mediaInfoVisible.value)
        assertTrue(executor.execute(DesktopPlayerCommand.EXIT_FULLSCREEN))
        assertFalse(executor.mediaInfoVisible.value)
        assertFalse(executor.execute(DesktopPlayerCommand.EXIT_FULLSCREEN))
    }

    @Test
    fun normalizerIsIndependentOfEqualizer() {
        val normalizeOnly = DesktopAudioEffects.filter(AudioEnhancementSettings(volumeNormalization = true))
        assertEquals("lavfi=[${DesktopAudioEffects.NORMALIZER_FILTER}]", normalizeOnly)
        val both = DesktopAudioEffects.filter(AudioEnhancementSettings(enabled = true, volumeNormalization = true)).orEmpty()
        assertTrue(both.startsWith("lavfi=[equalizer=") && both.endsWith("dB,${DesktopAudioEffects.NORMALIZER_FILTER}]"))
    }

    /** Regression (V0 B2): a failed panel control is reported on the HUD instead of being dropped. */
    @Test
    fun failedPanelControlIsReportedOnTheHud() {
        val commands = executor(FakeEngine(loaded()))
        val ok = commands.perform("Fill aspect") { Result.failure<Unit>(IllegalArgumentException("not supported")) }
        assertFalse(ok)
        assertEquals(HudMessage.Failed("Fill aspect", "not supported"), commands.status.value)
        assertTrue(commands.perform("Rotate") { Result.success(Unit) })
    }

    private fun executor(
        engine: FakeEngine,
        snapshotFile: (SnapshotOptions) -> java.io.File = { java.io.File("unused.png") },
        snapshotOptions: () -> SnapshotOptions = { SnapshotOptions() },
    ) = DesktopPlayerCommandExecutor(engine, {}, seekVia(engine), {}, {}, NoFullscreen, SnapshotPlan(snapshotOptions, snapshotFile))

    @Test
    fun volumeAmplifiesToTwoHundredPercentWithAStopAtOneHundred() {
        val engine = FakeEngine(loaded(volume = 0.98f))
        val executor = executor(engine)

        executor.execute(DesktopPlayerCommand.VOLUME_UP)
        assertEquals<HudMessage?>(HudMessage.Volume(100), executor.status.value)
        executor.execute(DesktopPlayerCommand.VOLUME_UP)
        executor.execute(DesktopPlayerCommand.VOLUME_UP_FINE)
        assertEquals<HudMessage?>(HudMessage.Volume(106), executor.status.value)

        executor.setVolumeLevel(5f)
        assertEquals(listOf("volume:1.0", "volume:1.05", "volume:1.06", "volume:2.0"), engine.calls)
        assertEquals<HudMessage?>(HudMessage.Volume(200), executor.status.value)
    }

    @Test
    fun shiftArrowsAreFineVolumeSteps() {
        assertEquals(DesktopPlayerCommand.VOLUME_UP_FINE, DesktopPlayerShortcuts.resolve(Key.DirectionUp, shift = true, ctrl = false, alt = false, meta = false))
        assertEquals(DesktopPlayerCommand.VOLUME_DOWN_FINE, DesktopPlayerShortcuts.resolve(Key.DirectionDown, shift = true, ctrl = false, alt = false, meta = false))
    }

    @Test
    fun delayStepsMatchTheirLabelsSoKeysMenuAndPanelAgree() {
        val engine = FakeEngine(loaded())
        val executor = executor(engine)

        executor.execute(DesktopPlayerCommand.SUBTITLE_DELAY_UP)
        executor.execute(DesktopPlayerCommand.AUDIO_DELAY_DOWN)

        assertEquals(100L, PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS)
        assertEquals(50L, PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS)
        assertEquals("Subtitle delay +100 ms", DesktopPlayerCommand.SUBTITLE_DELAY_UP.label)
        assertEquals("Audio delay −50 ms", DesktopPlayerCommand.AUDIO_DELAY_DOWN.label)
        assertEquals(listOf("subdelay:100", "audiodelay:-50"), engine.calls)
    }

    @Test
    fun snapshotsGoToOneFolderNamedForVimivo() {
        val directory = defaultSnapshotDirectory()
        val pictures = java.io.File(System.getProperty("user.home"), "Pictures")
        assertEquals(if (pictures.isDirectory) java.io.File(pictures, "Vimivo") else pictures.parentFile, directory)
    }

    @Test
    fun mpvAdvertisesTwoHundredPercentVolume() {
        assertEquals(2f, MpvCapabilities.forPlatform(dev.vimivo.desktop.platform.DesktopPlatform.LINUX).maxVolume)
    }

    private fun loaded(position: Long = 0, duration: Long = 120_000, volume: Float = 1f, speed: Float = 1f) = PlaybackState(
        engine = EngineId.MPV,
        media = MediaItem(MediaId("m"), "file:///m.mkv", "Movie", kind = MediaKind.VIDEO),
        status = PlaybackStatus.Playing,
        positionMillis = position,
        durationMillis = duration,
        volume = volume,
        speed = speed,
    )

    private object NoFullscreen : DesktopFullscreenControl {
        override fun toggle() = Unit
        override fun exit() = false
    }

    private class FakeEngine(initial: PlaybackState = PlaybackState(), private val failFrameStep: Boolean = false) : PlayerEngine {
        val calls = mutableListOf<String>()
        private val mutableState = MutableStateFlow(initial)
        override val id = EngineId.MPV
        override val state = mutableState.asStateFlow()
        override val capabilities = MpvCapabilities.forPlatform(dev.vimivo.desktop.platform.DesktopPlatform.MACOS)
        override val audioEnhancementState = MutableStateFlow(AudioEnhancementState(capabilities.audioEnhancements)).asStateFlow()

        private fun record(call: String): Result<Unit> = Result.success(Unit).also { calls += call }
        override fun supportFor(media: MediaItem) = SupportLevel.PREFERRED
        override suspend fun open(media: MediaItem, startPositionMillis: Long) = Result.success(Unit)
        override fun play() = Result.success(Unit)
        override fun pause() = Result.success(Unit)
        override fun stop() = Result.success(Unit)
        override fun seekTo(positionMillis: Long) = record("seek:$positionMillis")
        override fun setPlaybackSpeed(speed: Float) = record("speed:$speed")
        override fun setVolume(volume: Float) = record("volume:$volume").also { mutableState.update { it.copy(volume = volume) } }
        override fun selectAudioTrack(trackId: String?) = record("aid:$trackId")
        override fun selectVideoTrack(trackId: String?) = record("vid:$trackId")
        override fun selectSubtitleTrack(trackId: String?) = record("sid:$trackId")
        override fun setSubtitleDelay(delayMillis: Long) = record("subdelay:$delayMillis")
        override fun setAudioDelay(delayMillis: Long) = record("audiodelay:$delayMillis")
        override fun setABRepeat(range: ABRepeatRange) = record("ab:${range.pointA}-${range.pointB}-${range.enabled}")
        override fun frameStep(forward: Boolean): Result<Unit> =
            if (failFrameStep) Result.failure(IllegalStateException("no frames")) else record("frame:$forward")
        override fun setDeinterlace(enabled: Boolean) = record("deinterlace:$enabled")
        override fun applyVideoAdjustments(adjustments: VideoAdjustments) = record("aspect:${adjustments.aspectRatio.name}")
        override fun takeScreenshot(destinationFilePath: String) = record("shot:$destinationFilePath")
        override fun takeSnapshot(destinationFilePath: String, options: SnapshotOptions) =
            record("snap:$destinationFilePath:${options.format.name}:${options.includeSubtitles}")
        override fun close() = Unit
    }

/** Keyboard seeks go through the shared [SeekController], exactly as in the app. */
private fun seekVia(engine: PlayerEngine): (Long) -> Unit {
    val controller = SeekController { position, exact -> engine.seekTo(position, exact) }
    return { delta ->
        val state = engine.state.value
        controller.onEngineReport(state.positionMillis, state.durationMillis, isSeekable = true, isSeeking = false, nowMillis = 0)
        controller.seekBy(delta, state.positionMillis, nowMillis = 0)
    }
}
}
