@file:Suppress("ArgumentListWrapping", "ParameterListWrapping", "CyclomaticComplexMethod", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.desktop.playback

import androidx.compose.ui.input.key.Key
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlayerEngine
import dev.vimivo.playback.TrackCycleResult
import dev.vimivo.playback.VideoAspectRatio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
    SUBTITLE_DELAY_DOWN("Subtitle delay −100 ms"),
    SUBTITLE_DELAY_UP("Subtitle delay +100 ms"),
    AUDIO_DELAY_DOWN("Audio delay −50 ms"),
    AUDIO_DELAY_UP("Audio delay +50 ms"),
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
    private val toggleShuffle: () -> Unit,
    private val cycleRepeat: () -> Unit,
    private val fullscreen: DesktopFullscreenControl,
    private val snapshotFile: () -> File = { File(defaultSnapshotDirectory(), "vimivo-snapshot-${System.currentTimeMillis()}.png") },
) {
    private val mutableStatus = MutableStateFlow<String?>(null)
    private val mutableMediaInfoVisible = MutableStateFlow(false)
    private var volumeBeforeMute = 1f

    /** Short feedback for the last command (on-screen display), or null. */
    val status: StateFlow<String?> = mutableStatus.asStateFlow()
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
            DesktopPlayerCommand.TOGGLE_SHUFFLE -> { toggleShuffle(); report("Shuffle toggled") }
            DesktopPlayerCommand.CYCLE_REPEAT -> { cycleRepeat(); report("Repeat mode changed") }
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
        fun seek(delta: Long) = engine.seekTo(PlaybackControlPolicy.seekTarget(state.positionMillis, delta, state.durationMillis), exact = false)
        val result: Result<String?> = when (command) {
            DesktopPlayerCommand.SEEK_BACK_SHORT -> seek(-PlaybackControlPolicy.SHORT_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_SHORT -> seek(PlaybackControlPolicy.SHORT_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_BACK_MEDIUM -> seek(-MEDIUM_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_MEDIUM -> seek(MEDIUM_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_BACK_LONG -> seek(-PlaybackControlPolicy.LONG_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.SEEK_FORWARD_LONG -> seek(PlaybackControlPolicy.LONG_SEEK_MILLIS).map { null }
            DesktopPlayerCommand.VOLUME_UP -> setVolume(PlaybackControlPolicy.stepVolume(state.volume, PlaybackControlPolicy.DEFAULT_VOLUME_STEP))
            DesktopPlayerCommand.VOLUME_DOWN -> setVolume(PlaybackControlPolicy.stepVolume(state.volume, -PlaybackControlPolicy.DEFAULT_VOLUME_STEP))
            DesktopPlayerCommand.TOGGLE_MUTE -> if (state.volume > 0f) {
                volumeBeforeMute = state.volume
                engine.setVolume(0f).map { "Muted" }
            } else {
                setVolume(volumeBeforeMute.takeIf { it > 0f } ?: 1f)
            }
            DesktopPlayerCommand.SPEED_UP -> setSpeed(PlaybackControlPolicy.fasterSpeed(state.speed, engine.capabilities.supportedSpeedRange))
            DesktopPlayerCommand.SPEED_DOWN -> setSpeed(PlaybackControlPolicy.slowerSpeed(state.speed, engine.capabilities.supportedSpeedRange))
            DesktopPlayerCommand.SPEED_RESET -> setSpeed(1f)
            DesktopPlayerCommand.FRAME_STEP_FORWARD -> engine.frameStep(forward = true).map { null }
            DesktopPlayerCommand.FRAME_STEP_BACKWARD -> engine.frameStep(forward = false).map { null }
            DesktopPlayerCommand.CYCLE_AUDIO_TRACK -> when (val next = PlaybackControlPolicy.nextTrack(state.audioTracks, allowOff = false)) {
                is TrackCycleResult.Select -> engine.selectAudioTrack(next.track.id).map { "Audio: ${next.track.label}" }
                else -> Result.success("No audio tracks")
            }
            DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK -> when (val next = PlaybackControlPolicy.nextTrack(state.subtitleTracks, allowOff = true)) {
                is TrackCycleResult.Select -> engine.selectSubtitleTrack(next.track.id).map { "Subtitles: ${next.track.label}" }
                TrackCycleResult.Disable -> engine.selectSubtitleTrack(null).map { "Subtitles off" }
                TrackCycleResult.Unavailable -> Result.success("No subtitle tracks")
            }
            DesktopPlayerCommand.SUBTITLE_DELAY_DOWN -> setSubtitleDelay(state.subtitleDelayMillis, -PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.SUBTITLE_DELAY_UP -> setSubtitleDelay(state.subtitleDelayMillis, PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AUDIO_DELAY_DOWN -> setAudioDelay(state.audioDelayMillis, -PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AUDIO_DELAY_UP -> setAudioDelay(state.audioDelayMillis, PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS)
            DesktopPlayerCommand.AB_LOOP -> {
                val next = PlaybackControlPolicy.nextABRepeat(state.abRepeat, state.positionMillis)
                engine.setABRepeat(next).map {
                    when {
                        next.enabled -> "A-B loop on"
                        next.pointA != null -> "Loop point A set"
                        else -> "A-B loop off"
                    }
                }
            }
            DesktopPlayerCommand.SNAPSHOT -> {
                val target = snapshotFile()
                runCatching { target.parentFile?.mkdirs() }
                    .mapCatching { engine.takeScreenshot(target.absolutePath).getOrThrow() }
                    .map { "Snapshot saved: ${target.name}" }
            }
            DesktopPlayerCommand.CYCLE_ASPECT_RATIO -> {
                val ratios = VideoAspectRatio.entries
                val next = ratios[(ratios.indexOf(state.videoAdjustments.aspectRatio) + 1) % ratios.size]
                engine.applyVideoAdjustments(state.videoAdjustments.copy(aspectRatio = next)).map { "Aspect ratio: ${next.label}" }
            }
            DesktopPlayerCommand.TOGGLE_DEINTERLACE -> {
                val enabled = !state.deinterlace
                engine.setDeinterlace(enabled).map { if (enabled) "Deinterlacing on" else "Deinterlacing off" }
            }
            else -> Result.success(null)
        }
        result.onSuccess { message -> if (message != null) mutableStatus.value = message }
            .onFailure { error -> mutableStatus.value = "${command.label} failed: ${error.message ?: "unavailable"}" }
        return true
    }

    private fun setVolume(volume: Float) = engine.setVolume(volume).map { "Volume ${(volume * PERCENT).toInt()}%" }
    private fun setSpeed(speed: Float) = engine.setPlaybackSpeed(speed).map { "Speed ${speed}x" }
    private fun setSubtitleDelay(current: Long, delta: Long): Result<String> {
        val next = PlaybackControlPolicy.stepDelay(current, delta)
        return engine.setSubtitleDelay(next).map { "Subtitle delay $next ms" }
    }
    private fun setAudioDelay(current: Long, delta: Long): Result<String> {
        val next = PlaybackControlPolicy.stepDelay(current, delta)
        return engine.setAudioDelay(next).map { "Audio delay $next ms" }
    }

    private fun dismissMediaInfoIfVisible(): Boolean {
        if (!mutableMediaInfoVisible.value) return false
        mutableMediaInfoVisible.value = false
        return true
    }

    private fun report(message: String): Boolean {
        mutableStatus.value = message
        return true
    }

    private companion object {
        const val MEDIUM_SEEK_MILLIS = 10_000L
        const val PERCENT = 100
    }
}

/** Snapshots go to the user's Pictures folder (VLC-style) and fall back to the home directory. */
internal fun defaultSnapshotDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val pictures = File(home, "Pictures")
    return if (pictures.isDirectory) File(pictures, "Vimivo") else home
}
