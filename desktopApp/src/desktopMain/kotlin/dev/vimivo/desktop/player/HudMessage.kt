package dev.vimivo.desktop.player

import androidx.compose.runtime.Composable
import dev.vimivo.desktop.resources.Res
import dev.vimivo.desktop.resources.hud_added_to_queue
import dev.vimivo.desktop.resources.hud_aspect_ratio
import dev.vimivo.desktop.resources.hud_audio_delay
import dev.vimivo.desktop.resources.hud_audio_track
import dev.vimivo.desktop.resources.hud_clipboard_not_url
import dev.vimivo.desktop.resources.hud_command_failed
import dev.vimivo.desktop.resources.hud_deinterlace_off
import dev.vimivo.desktop.resources.hud_deinterlace_on
import dev.vimivo.desktop.resources.hud_loop_off
import dev.vimivo.desktop.resources.hud_loop_on
import dev.vimivo.desktop.resources.hud_loop_point_a
import dev.vimivo.desktop.resources.hud_muted
import dev.vimivo.desktop.resources.hud_no_audio_tracks
import dev.vimivo.desktop.resources.hud_no_subtitle_tracks
import dev.vimivo.desktop.resources.hud_repeat_changed
import dev.vimivo.desktop.resources.hud_shuffle_toggled
import dev.vimivo.desktop.resources.hud_snapshot_saved
import dev.vimivo.desktop.resources.hud_speed
import dev.vimivo.desktop.resources.hud_stream_opened
import dev.vimivo.desktop.resources.hud_subtitle_delay
import dev.vimivo.desktop.resources.hud_subtitle_failed
import dev.vimivo.desktop.resources.hud_subtitle_loaded
import dev.vimivo.desktop.resources.hud_subtitle_track
import dev.vimivo.desktop.resources.hud_subtitles_off
import dev.vimivo.desktop.resources.hud_unavailable
import dev.vimivo.desktop.resources.hud_volume
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Short on-screen feedback for a player action. It carries data, not text, so the ViewModel stays
 * language-neutral and the UI renders it from string resources (English and Bangla).
 */
internal sealed interface HudMessage {
    data class Volume(val percent: Int) : HudMessage
    data object Muted : HudMessage
    data class Speed(val speed: Float) : HudMessage
    data class SubtitleDelay(val millis: Long) : HudMessage
    data class AudioDelay(val millis: Long) : HudMessage
    data class AudioTrack(val label: String) : HudMessage
    data class SubtitleTrack(val label: String) : HudMessage
    data object SubtitlesOff : HudMessage
    data object NoAudioTracks : HudMessage
    data object NoSubtitleTracks : HudMessage
    data object ShuffleToggled : HudMessage
    data object RepeatChanged : HudMessage
    data object LoopPointA : HudMessage
    data object LoopOn : HudMessage
    data object LoopOff : HudMessage
    data class SnapshotSaved(val fileName: String) : HudMessage
    data class AspectRatio(val label: String) : HudMessage
    data class Deinterlace(val enabled: Boolean) : HudMessage
    data class SubtitleLoaded(val label: String) : HudMessage
    data class SubtitleFailed(val label: String) : HudMessage
    data class AddedToQueue(val count: Int) : HudMessage
    data object StreamOpened : HudMessage
    data object ClipboardNotUrl : HudMessage

    /** A command failed; [action] is the command's label and [reason] the engine's message, if any. */
    data class Failed(val action: String, val reason: String?) : HudMessage
}

@Suppress("SpreadOperator") // At most two format arguments.
@Composable
internal fun HudMessage.text(): String {
    fixedText[this]?.let { return stringResource(it) }
    if (this is HudMessage.Failed && reason == null) {
        return stringResource(Res.string.hud_command_failed, action, stringResource(Res.string.hud_unavailable))
    }
    val (resource, args) = formatted()
    return stringResource(resource, *args)
}

/** The resource and arguments for messages that carry data; fixed messages live in [fixedText]. */
@Suppress("CyclomaticComplexMethod") // A flat lookup table: one branch per message type.
private fun HudMessage.formatted(): Pair<StringResource, Array<Any>> = when (this) {
    is HudMessage.Volume -> Res.string.hud_volume to arrayOf("$percent%")
    is HudMessage.Speed -> Res.string.hud_speed to arrayOf(formatSpeed(speed))
    is HudMessage.SubtitleDelay -> Res.string.hud_subtitle_delay to arrayOf(signed(millis))
    is HudMessage.AudioDelay -> Res.string.hud_audio_delay to arrayOf(signed(millis))
    is HudMessage.AudioTrack -> Res.string.hud_audio_track to arrayOf(label)
    is HudMessage.SubtitleTrack -> Res.string.hud_subtitle_track to arrayOf(label)
    is HudMessage.SnapshotSaved -> Res.string.hud_snapshot_saved to arrayOf(fileName)
    is HudMessage.AspectRatio -> Res.string.hud_aspect_ratio to arrayOf(label)
    is HudMessage.SubtitleLoaded -> Res.string.hud_subtitle_loaded to arrayOf(label)
    is HudMessage.SubtitleFailed -> Res.string.hud_subtitle_failed to arrayOf(label)
    is HudMessage.AddedToQueue -> Res.string.hud_added_to_queue to arrayOf(count)
    is HudMessage.Deinterlace ->
        (if (enabled) Res.string.hud_deinterlace_on else Res.string.hud_deinterlace_off) to emptyArray()
    is HudMessage.Failed -> Res.string.hud_command_failed to arrayOf(action, reason.orEmpty())
    else -> Res.string.hud_muted to emptyArray()
}

private val fixedText: Map<HudMessage, StringResource> = mapOf(
    HudMessage.Muted to Res.string.hud_muted,
    HudMessage.SubtitlesOff to Res.string.hud_subtitles_off,
    HudMessage.NoAudioTracks to Res.string.hud_no_audio_tracks,
    HudMessage.NoSubtitleTracks to Res.string.hud_no_subtitle_tracks,
    HudMessage.ShuffleToggled to Res.string.hud_shuffle_toggled,
    HudMessage.RepeatChanged to Res.string.hud_repeat_changed,
    HudMessage.LoopPointA to Res.string.hud_loop_point_a,
    HudMessage.LoopOn to Res.string.hud_loop_on,
    HudMessage.LoopOff to Res.string.hud_loop_off,
    HudMessage.StreamOpened to Res.string.hud_stream_opened,
    HudMessage.ClipboardNotUrl to Res.string.hud_clipboard_not_url,
)

/** "1×", "1.25×": no trailing zeros, matching the speed pickers. */
internal fun formatSpeed(speed: Float): String = speed.toBigDecimal().stripTrailingZeros().toPlainString()

private fun signed(millis: Long): String = if (millis > 0) "+$millis" else millis.toString()
