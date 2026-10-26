@file:Suppress("ArgumentListWrapping", "ParameterListWrapping", "CyclomaticComplexMethod", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.desktop.playback

import androidx.compose.ui.input.key.Key
import dev.vimivo.desktop.player.HudMessage
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.SnapshotNaming
import dev.vimivo.playback.SnapshotOptions
import dev.vimivo.playback.TrackCycleResult
import dev.vimivo.playback.VideoAspectRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

/** Player commands reachable from single-key shortcuts and the menu bar. */
internal enum class DesktopPlayerCommand(val label: String) {
    TOGGLE_PLAYBACK("Play / Pause"),
    SEEK_BACK_SHORT("Seek backward 5 s"),
    SEEK_FORWARD_SHORT("Seek forward 5 s"),
    SEEK_BACK_MEDIUM("Seek backward 10 s"),
    SEEK_FORWARD_MEDIUM("Seek forward 10 s"),
    SEEK_BACK_LONG("Seek backward 30 s"),
    SEEK_FORWARD_LONG("Seek forward 30 s"),
    VOLUME_UP("Volume up"),
    VOLUME_DOWN("Volume down"),
    VOLUME_UP_FINE("Volume up 1%"),
    VOLUME_DOWN_FINE("Volume down 1%"),
    TOGGLE_MUTE("Mute / Unmute"),
    TOGGLE_SHUFFLE("Toggle shuffle"),
    CYCLE_REPEAT("Cycle repeat mode"),
    SPEED_UP("Faster"),
    SPEED_DOWN("Slower"),
    SPEED_RESET("Normal speed"),
    TOGGLE_FULLSCREEN("Toggle full screen"),
    EXIT_FULLSCREEN("Exit full screen"),
    FRAME_STEP_FORWARD("Next frame"),
    FRAME_STEP_BACKWARD("Previous frame"),
    CYCLE_AUDIO_TRACK("Cycle audio track"),
    CYCLE_SUBTITLE_TRACK("Cycle subtitle track"),
    SUBTITLE_DELAY_DOWN("Subtitle delay −${PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS} ms"),
    SUBTITLE_DELAY_UP("Subtitle delay +${PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS} ms"),
    AUDIO_DELAY_DOWN("Audio delay −${PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS} ms"),
    AUDIO_DELAY_UP("Audio delay +${PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS} ms"),
    AB_LOOP("A-B loop"),
    SNAPSHOT("Take snapshot"),
    CYCLE_ASPECT_RATIO("Cycle aspect ratio"),
    TOGGLE_DEINTERLACE("Toggle deinterlacing"),
    MEDIA_INFO("Media information"),
}

/** Maps unmodified (or Shift-modified) keys to commands; Ctrl/Cmd/Alt combinations belong to the menu bar. */
internal object DesktopPlayerShortcuts {
    fun resolve(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean, meta: Boolean): DesktopPlayerCommand? {
        if (ctrl || alt || meta) return null
        return if (shift) shifted(key) else plain(key)
    }

    private fun plain(key: Key): DesktopPlayerCommand? = when (key) {
        Key.Spacebar, Key.K -> DesktopPlayerCommand.TOGGLE_PLAYBACK
        Key.DirectionLeft -> DesktopPlayerCommand.SEEK_BACK_SHORT
        Key.DirectionRight -> DesktopPlayerCommand.SEEK_FORWARD_SHORT
        Key.J -> DesktopPlayerCommand.SEEK_BACK_MEDIUM
        Key.L -> DesktopPlayerCommand.SEEK_FORWARD_MEDIUM
        Key.DirectionUp -> DesktopPlayerCommand.VOLUME_UP
        Key.DirectionDown -> DesktopPlayerCommand.VOLUME_DOWN
        Key.M -> DesktopPlayerCommand.TOGGLE_MUTE
        Key.S -> DesktopPlayerCommand.TOGGLE_SHUFFLE
        Key.R -> DesktopPlayerCommand.CYCLE_REPEAT
        Key.RightBracket -> DesktopPlayerCommand.SPEED_UP
        Key.LeftBracket -> DesktopPlayerCommand.SPEED_DOWN
        Key.Backspace, Key.Equals -> DesktopPlayerCommand.SPEED_RESET
        Key.F -> DesktopPlayerCommand.TOGGLE_FULLSCREEN
        Key.Escape -> DesktopPlayerCommand.EXIT_FULLSCREEN
        Key.E -> DesktopPlayerCommand.FRAME_STEP_FORWARD
        Key.B -> DesktopPlayerCommand.CYCLE_AUDIO_TRACK
        Key.V -> DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK
        Key.G -> DesktopPlayerCommand.SUBTITLE_DELAY_DOWN
        Key.H -> DesktopPlayerCommand.SUBTITLE_DELAY_UP
        Key.A -> DesktopPlayerCommand.CYCLE_ASPECT_RATIO
        Key.D -> DesktopPlayerCommand.TOGGLE_DEINTERLACE
        Key.I -> DesktopPlayerCommand.MEDIA_INFO
        else -> null
    }

    private fun shifted(key: Key): DesktopPlayerCommand? = when (key) {
        Key.DirectionUp -> DesktopPlayerCommand.VOLUME_UP_FINE
        Key.DirectionDown -> DesktopPlayerCommand.VOLUME_DOWN_FINE
        Key.DirectionLeft -> DesktopPlayerCommand.SEEK_BACK_LONG
        Key.DirectionRight -> DesktopPlayerCommand.SEEK_FORWARD_LONG
        Key.E -> DesktopPlayerCommand.FRAME_STEP_BACKWARD
        Key.S -> DesktopPlayerCommand.SNAPSHOT
        Key.L -> DesktopPlayerCommand.AB_LOOP
        Key.G -> DesktopPlayerCommand.AUDIO_DELAY_DOWN
        Key.H -> DesktopPlayerCommand.AUDIO_DELAY_UP
        else -> null
    }
}

/** Window full-screen state, owned by the Compose window. */
internal interface DesktopFullscreenControl {
    fun toggle()

    /** Leaves full screen; returns false when the window was not full screen. */
    fun exit(): Boolean
}

/**
 * Executes [DesktopPlayerCommand]s against the engine. Window and queue actions are injected so the
 * executor stays testable without a window or a real queue.
 */
internal class DesktopPlayerCommandExecutor(
    private val engine: PlayerEngine,
    private val togglePlayback: () -> Unit,
    /** Relative seek through the shared SeekController (one exact seek, relative to a pending target). */
    private val seekBy: (deltaMillis: Long) -> Unit,
    private val toggleShuffle: () -> Unit,
    private val cycleRepeat: () -> Unit,
    private val fullscreen: DesktopFullscreenControl,
    private val snapshots: SnapshotPlan = SnapshotPlan(),
) {
    private val mutableStatus = MutableStateFlow<HudMessage?>(null)
    private val mutableMediaInfoVisible = MutableStateFlow(false)
    private var volumeBeforeMute = 1f

    /** Short feedback for the last command (on-screen display), or null. */
    val status: StateFlow<HudMessage?> = mutableStatus.asStateFlow()
    val mediaInfoVisible: StateFlow<Boolean> = mutableMediaInfoVisible.asStateFlow()

    fun dismissMediaInfo() {
        mutableMediaInfoVisible.value = false
    }

    fun clearStatus() {
        mutableStatus.value = null
    }

    /** Returns true when the command was handled and the key event should be consumed. */
    fun execute(command: DesktopPlayerCommand): Boolean {
        val state = engine.state.value
        val hasMedia = state.media != null
        return when (command) {
            DesktopPlayerCommand.TOGGLE_PLAYBACK -> { togglePlayback(); true }
            DesktopPlayerCommand.TOGGLE_SHUFFLE -> { toggleShuffle(); report(HudMessage.ShuffleToggled) }
            DesktopPlayerCommand.CYCLE_REPEAT -> { cycleRepeat(); report(HudMessage.RepeatChanged) }
            DesktopPlayerCommand.TOGGLE_FULLSCREEN -> { fullscreen.toggle(); true }
            DesktopPlayerCommand.EXIT_FULLSCREEN -> fullscreen.exit() || dismissMediaInfoIfVisible()
            DesktopPlayerCommand.MEDIA_INFO -> {
                if (hasMedia) mutableMediaInfoVisible.value = !mutableMediaInfoVisible.value
                hasMedia
            }
            else -> if (hasMedia) executeMediaCommand(command) else false
        }
    }

    private fun executeMediaCommand(command: DesktopPlayerCommand): Boolean {
        val state = engine.state.value
        fun seek(delta: Long): Result<Unit> = Result.success(seekBy(delta))
        val result: Result<HudMessage?> = when (command) {
            DesktopPlayerCommand.SEEK_BACK_SHORT -> seek(-PlaybackControlPolicy.SHORT_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_SHORT -> seek(PlaybackControlPolicy.SHORT_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_BACK_MEDIUM -> seek(-MEDIUM_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_MEDIUM -> seek(MEDIUM_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_BACK_LONG -> seek(-PlaybackControlPolicy.LONG_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_LONG -> seek(PlaybackControlPolicy.LONG_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.VOLUME_UP -> stepVolume(state.volume, PlaybackControlPolicy.DEFAULT_VOLUME_STEP)
            DesktopPlayerCommand.VOLUME_DOWN -> stepVolume(state.volume, -PlaybackControlPolicy.DEFAULT_VOLUME_STEP)
            DesktopPlayerCommand.VOLUME_UP_FINE -> stepVolume(state.volume, PlaybackControlPolicy.FINE_VOLUME_STEP)
            DesktopPlayerCommand.VOLUME_DOWN_FINE -> stepVolume(state.volume, -PlaybackControlPolicy.FINE_VOLUME_STEP)
            DesktopPlayerCommand.TOGGLE_MUTE -> if (state.volume > 0f) {
                volumeBeforeMute = state.volume
                engine.setVolume(0f).map { HudMessage.Muted }
            } else {
                setVolume(volumeBeforeMute.takeIf { it > 0f } ?: 1f)
            }
            DesktopPlayerCommand.SPEED_UP -> setSpeed(PlaybackControlPolicy.fasterSpeed(state.speed, engine.capabilities.supportedSpeedRange))
            DesktopPlayerCommand.SPEED_DOWN -> setSpeed(PlaybackControlPolicy.slowerSpeed(state.speed, engine.capabilities.supportedSpeedRange))
            DesktopPlayerCommand.SPEED_RESET -> setSpeed(1f)
            DesktopPlayerCommand.FRAME_STEP_FORWARD -> engine.frameStep(forward = true).map { null }
            DesktopPlayerCommand.FRAME_STEP_BACKWARD -> engine.frameStep(forward = false).map { null }
            DesktopPlayerCommand.CYCLE_AUDIO_TRACK -> when (val next = PlaybackControlPolicy.nextTrack(state.audioTracks, allowOff = false)) {
                is TrackCycleResult.Select -> engine.selectAudioTrack(next.track.id).map { HudMessage.AudioTrack(next.track.label) }
                else -> Result.success(HudMessage.NoAudioTracks)
            }
            DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK -> when (val next = PlaybackControlPolicy.nextTrack(state.subtitleTracks, allowOff = true)) {
                is TrackCycleResult.Select -> engine.selectSubtitleTrack(next.track.id).map { HudMessage.SubtitleTrack(next.track.label) }
                TrackCycleResult.Disable -> engine.selectSubtitleTrack(null).map { HudMessage.SubtitlesOff }
                TrackCycleResult.Unavailable -> Result.success(HudMessage.NoSubtitleTracks)
            }
            DesktopPlayerCommand.SUBTITLE_DELAY_DOWN -> setSubtitleDelay(state.subtitleDelayMillis, -PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.SUBTITLE_DELAY_UP -> setSubtitleDelay(state.subtitleDelayMillis, PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AUDIO_DELAY_DOWN -> setAudioDelay(state.audioDelayMillis, -PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AUDIO_DELAY_UP -> setAudioDelay(state.audioDelayMillis, PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AB_LOOP -> {
                val next = PlaybackControlPolicy.nextABRepeat(state.abRepeat, state.positionMillis)
                engine.setABRepeat(next).map {
                    when {
                        next.enabled -> HudMessage.LoopOn
                        next.pointA != null -> HudMessage.LoopPointA
                        else -> HudMessage.LoopOff
                    }
                }
            }
            DesktopPlayerCommand.SNAPSHOT -> {
                val options = snapshots.options()
                val target = snapshots.fileFor?.invoke(options) ?: defaultSnapshotFile(options, state)
                runCatching { target.parentFile?.mkdirs() }
                    .mapCatching { engine.takeSnapshot(target.absolutePath, options).getOrThrow() }
                    .map { HudMessage.SnapshotSaved(target.name) }
            }
            DesktopPlayerCommand.CYCLE_ASPECT_RATIO -> {
                val ratios = VideoAspectRatio.entries
                val next = ratios[(ratios.indexOf(state.videoAdjustments.aspectRatio) + 1) % ratios.size]
                engine.applyVideoAdjustments(state.videoAdjustments.copy(aspectRatio = next)).map { HudMessage.AspectRatio(next.label) }
            }
            DesktopPlayerCommand.TOGGLE_DEINTERLACE -> {
                val enabled = !state.deinterlace
                engine.setDeinterlace(enabled).map { HudMessage.Deinterlace(enabled) }
            }
            else -> Result.success(null)
        }
        result.onSuccess { message -> if (message != null) mutableStatus.value = message }
            .onFailure { error -> mutableStatus.value = HudMessage.Failed(command.label, error.message) }
        return true
    }

    /** `<folder>/<title>-<position>-<time>.<ext>`; the folder is the user's choice or the Pictures/Vimivo default. */
    private fun defaultSnapshotFile(options: SnapshotOptions, state: PlaybackState): File {
        val folder = options.folder?.takeIf(String::isNotBlank)?.let(::File) ?: defaultSnapshotDirectory()
        val name = SnapshotNaming.fileName(state.media?.title, state.positionMillis, System.currentTimeMillis(), options.format)
        return File(folder, name)
    }

    private fun stepVolume(current: Float, delta: Float) =
        setVolume(PlaybackControlPolicy.stepVolume(current, delta, engine.capabilities.maxVolume))

    private fun setVolume(volume: Float): Result<HudMessage> {
        val clamped = volume.coerceIn(0f, engine.capabilities.maxVolume)
        return engine.setVolume(clamped).map { HudMessage.Volume((clamped * PERCENT).roundToInt()) }
    }

    /** Absolute volume from the slider; clamped to the engine maximum and echoed on the HUD. */
    fun setVolumeLevel(volume: Float) {
        setVolume(volume)
            .onSuccess { mutableStatus.value = it }
            .onFailure { error -> mutableStatus.value = HudMessage.Failed(DesktopPlayerCommand.VOLUME_UP.label, error.message) }
    }
    private fun setSpeed(speed: Float) = engine.setPlaybackSpeed(speed).map { HudMessage.Speed(speed) }
    private fun setSubtitleDelay(current: Long, delta: Long): Result<HudMessage> {
        val next = PlaybackControlPolicy.stepDelay(current, delta)
        return engine.setSubtitleDelay(next).map { HudMessage.SubtitleDelay(next) }
    }
    private fun setAudioDelay(current: Long, delta: Long): Result<HudMessage> {
        val next = PlaybackControlPolicy.stepDelay(current, delta)
        return engine.setAudioDelay(next).map { HudMessage.AudioDelay(next) }
    }

    private fun dismissMediaInfoIfVisible(): Boolean {
        if (!mutableMediaInfoVisible.value) return false
        mutableMediaInfoVisible.value = false
        return true
    }

    /**
     * Runs a control from a panel. A failure is shown on the HUD ("<action> failed: <reason>") and playback carries
     * on; returns whether the control succeeded.
     */
    fun perform(action: String, control: () -> Result<*>): Boolean =
        control().onFailure { error -> mutableStatus.value = HudMessage.Failed(action, error.message) }.isSuccess

    /** Shows [message] on the HUD; returns true so command handlers can use it as their result. */
    fun report(message: HudMessage): Boolean {
        mutableStatus.value = message
        return true
    }

    private companion object {
        const val MEDIUM_SEEK_MILLIS = 10_000L
        const val PERCENT = 100
    }
}

/**
 * How snapshots are taken: the user's [options] (format, subtitles, folder), read at the moment of the snapshot, and
 * optionally a fixed target for [fileFor] (tests); without it the name and folder come from the options.
 */
internal class SnapshotPlan(
    val options: () -> SnapshotOptions = { SnapshotOptions() },
    val fileFor: ((SnapshotOptions) -> File)? = null,
)

/** Snapshots go to the user's Pictures folder (VLC-style) and fall back to the home directory. */
internal fun defaultSnapshotDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val pictures = File(home, "Pictures")
    return if (pictures.isDirectory) File(pictures, "Vimivo") else home
}
