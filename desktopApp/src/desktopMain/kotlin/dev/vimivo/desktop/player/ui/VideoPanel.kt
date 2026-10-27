@file:Suppress("LongParameterList") // Composables take their state, callbacks and styling explicitly.

package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.playback.AutoCrop
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.playback.defaultSnapshotDirectory
import dev.vimivo.desktop.player.HudMessage
import dev.vimivo.playback.CropPreset
import dev.vimivo.playback.CropRect
import dev.vimivo.playback.DeinterlaceMode
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.SnapshotFormat
import dev.vimivo.playback.SnapshotOptions
import dev.vimivo.playback.VideoAdjustments
import dev.vimivo.playback.VideoAspectRatio
import dev.vimivo.playback.VideoCropMath
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

@Composable
internal fun VideoTab(
    playback: PlaybackState,
    snapshot: SnapshotOptions,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    onSnapshotOptions: (SnapshotOptions) -> Unit,
) {
    val adjustments = playback.videoAdjustments
    fun adjust(label: String, next: VideoAdjustments) {
        commands.perform(label) { engine.applyVideoAdjustments(next) }
    }
    PictureSection(playback, engine, commands, ::adjust)
    PanelSection("Zoom and position")
    SliderRow("Zoom", adjustments.zoom, ZOOM_RANGE, "%+.2f".format(adjustments.zoom), reset = 0f) {
        adjust("Zoom", adjustments.copy(zoom = it))
    }
    SliderRow("Pan horizontal", adjustments.panX, PAN_RANGE, "%+.2f".format(adjustments.panX), reset = 0f) {
        adjust("Pan", adjustments.copy(panX = it))
    }
    SliderRow("Pan vertical", adjustments.panY, PAN_RANGE, "%+.2f".format(adjustments.panY), reset = 0f) {
        adjust("Pan", adjustments.copy(panY = it))
    }
    PanelRow("360° Pan") {
        PanelButton("◀ Left", Modifier.weight(1f)) {
            adjust("Pan Left", adjustments.copy(panX = (adjustments.panX - 0.1f).coerceIn(-1f, 1f)))
        }
        Spacer(Modifier.width(4.dp))
        PanelButton("▲ Up", Modifier.weight(1f)) {
            adjust("Pan Up", adjustments.copy(panY = (adjustments.panY - 0.1f).coerceIn(-1f, 1f)))
        }
        Spacer(Modifier.width(4.dp))
        PanelButton("▼ Down", Modifier.weight(1f)) {
            adjust("Pan Down", adjustments.copy(panY = (adjustments.panY + 0.1f).coerceIn(-1f, 1f)))
        }
        Spacer(Modifier.width(4.dp))
        PanelButton("Right ▶", Modifier.weight(1f)) {
            adjust("Pan Right", adjustments.copy(panX = (adjustments.panX + 0.1f).coerceIn(-1f, 1f)))
        }
        Spacer(Modifier.width(4.dp))
        PanelButton("↺ Reset", Modifier.weight(1f)) {
            adjust("Reset Pan", adjustments.copy(panX = 0f, panY = 0f, zoom = 0f))
        }
    }
    PanelRow("Rotate") {
        PanelButton("↺ 90° CCW", Modifier.weight(1f)) {
            adjust("Rotate CCW", adjustments.copy(rotationDegrees = (adjustments.rotationDegrees - 90 + 360) % 360))
        }
        Spacer(Modifier.width(6.dp))
        PanelButton("↻ 90° CW", Modifier.weight(1f)) {
            adjust("Rotate CW", adjustments.copy(rotationDegrees = (adjustments.rotationDegrees + 90) % 360))
        }
    }
    PanelSection("Colour")
    ColourSlider("Brightness", adjustments.brightness) { adjust("Brightness", adjustments.copy(brightness = it)) }
    ColourSlider("Contrast", adjustments.contrast) { adjust("Contrast", adjustments.copy(contrast = it)) }
    ColourSlider("Saturation", adjustments.saturation) { adjust("Saturation", adjustments.copy(saturation = it)) }
    ColourSlider("Hue", adjustments.hue) { adjust("Hue", adjustments.copy(hue = it)) }
    ColourSlider("Gamma", adjustments.gamma) { adjust("Gamma", adjustments.copy(gamma = it)) }
    EffectsSection(adjustments, ::adjust)
    PanelSection("Frames")
    PanelRow("Step") {
        PanelButton("◀ Previous", Modifier.weight(1f)) { commands.execute(DesktopPlayerCommand.FRAME_STEP_BACKWARD) }
        Spacer(Modifier.width(6.dp))
        PanelButton("Next ▶", Modifier.weight(1f)) { commands.execute(DesktopPlayerCommand.FRAME_STEP_FORWARD) }
    }
    SnapshotSection(snapshot, commands, onSnapshotOptions)
}

@Composable
private fun EffectsSection(
    adjustments: VideoAdjustments,
    adjust: (String, VideoAdjustments) -> Unit,
) {
    PanelSection("Effects")
    PanelRow("Sharpen") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.sharpen,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Sharpen", adjustments.copy(sharpen = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Blur") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.blur,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Blur", adjustments.copy(blur = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Mirror / Flip") {
        PanelButton(if (adjustments.hflip) "H-Flip (On)" else "H-Flip", Modifier.weight(1f)) {
            adjust("Horizontal Flip", adjustments.copy(hflip = !adjustments.hflip))
        }
        Spacer(Modifier.width(6.dp))
        PanelButton(if (adjustments.vflip) "V-Flip (On)" else "V-Flip", Modifier.weight(1f)) {
            adjust("Vertical Flip", adjustments.copy(vflip = !adjustments.vflip))
        }
    }
    PanelRow("Denoise") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.denoise,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Denoise", adjustments.copy(denoise = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Film grain") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.grain,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Grain", adjustments.copy(grain = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Negate") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.negate,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Negate", adjustments.copy(negate = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Vignette") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.vignette,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Vignette", adjustments.copy(vignette = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Deband") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.deband,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Deband", adjustments.copy(deband = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Threshold") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = adjustments.colorThreshold,
            label = { if (it) "On" else "Off" },
            onSelect = { adjust("Threshold", adjustments.copy(colorThreshold = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("") {
        PanelButton("Reset effects", Modifier.fillMaxWidth()) {
            adjust(
                "Reset effects",
                adjustments.copy(
                    sharpen = false, blur = false, hflip = false, vflip = false,
                    grain = false, denoise = false, colorThreshold = false,
                    negate = false, vignette = false, deband = false,
                ),
            )
        }
    }
}

/** Snapshot format, whether subtitles are burned in, and the folder; the button saves the current frame. */
@Composable
private fun SnapshotSection(
    options: SnapshotOptions,
    commands: DesktopPlayerCommandExecutor,
    onChange: (SnapshotOptions) -> Unit,
) {
    PanelSection("Snapshot")
    PanelRow("Format") {
        SegmentedChoice(
            options = SnapshotFormat.entries,
            selected = options.format,
            label = SnapshotFormat::label,
            onSelect = { onChange(options.copy(format = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Subtitles") {
        SegmentedChoice(
            options = listOf(false, true),
            selected = options.includeSubtitles,
            label = { if (it) "Include" else "Leave out" },
            onSelect = { onChange(options.copy(includeSubtitles = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Folder") {
        PanelText(
            options.folder ?: defaultSnapshotDirectory().path,
            modifier = Modifier.weight(1f),
            color = PlayerPalette.contentMuted,
        )
        Spacer(Modifier.width(6.dp))
        PanelButton("Choose…") { chooseFolder(options.folder)?.let { onChange(options.copy(folder = it)) } }
        if (options.folder != null) {
            Spacer(Modifier.width(6.dp))
            PanelButton("Default") { onChange(options.copy(folder = null)) }
        }
    }
    PanelRow("") {
        PanelButton("Save frame", Modifier.fillMaxWidth()) { commands.execute(DesktopPlayerCommand.SNAPSHOT) }
    }
}

private fun chooseFolder(current: String?): String? {
    val chooser = JFileChooser(current?.let(::File) ?: defaultSnapshotDirectory()).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Snapshot folder"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}

/** Aspect ratio, crop, rotation and deinterlacing: how the picture is framed and cleaned up. */
@Composable
private fun PictureSection(
    playback: PlaybackState,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    adjust: (String, VideoAdjustments) -> Unit,
) {
    val adjustments = playback.videoAdjustments
    PanelSection("Picture")
    PanelRow("Aspect ratio") {
        PanelDropdown(
            options = VideoAspectRatio.entries,
            selected = adjustments.aspectRatio,
            label = VideoAspectRatio::label,
            placeholder = "Original",
            onSelect = { adjust("${it.label} aspect", adjustments.copy(aspectRatio = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CropRow(playback, commands, adjust)
    PanelRow("Rotation") {
        SegmentedChoice(
            options = listOf(0, 90, 180, 270),
            selected = adjustments.rotationDegrees,
            label = { "$it°" },
            onSelect = { adjust("Rotate", adjustments.copy(rotationDegrees = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Deinterlace") {
        PanelDropdown(
            options = DeinterlaceMode.entries,
            selected = playback.deinterlaceMode,
            label = DeinterlaceMode::label,
            placeholder = "Off",
            onSelect = { mode ->
                if (commands.perform("Deinterlace") { engine.setDeinterlaceMode(mode) }) {
                    commands.report(HudMessage.DeinterlaceModeChanged(mode.label))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Crop presets are computed from the picture size; "Auto" finds black bars in the file (bbox, sampled). */
@Composable
private fun CropRow(
    playback: PlaybackState,
    commands: DesktopPlayerCommandExecutor,
    adjust: (String, VideoAdjustments) -> Unit,
) {
    val adjustments = playback.videoAdjustments
    val scope = rememberCoroutineScope()
    val autoCrop = remember { AutoCrop() }
    val picture = playback.videoTracks.firstOrNull { it.isSelected }
    fun apply(preset: CropPreset, crop: CropRect?) {
        adjust("Crop", adjustments.copy(crop = crop, cropPreset = preset))
        commands.report(HudMessage.CropChanged(preset.label))
    }
    fun fail(action: String, reason: Throwable) = commands.perform(action) { Result.failure<Unit>(reason) }
    fun detect() = scope.launch {
        val media = playback.media ?: return@launch
        autoCrop.detect(media, playback.durationMillis).fold(
            onSuccess = { crop ->
                if (crop == null) commands.report(HudMessage.NoBlackBars) else apply(CropPreset.AUTO, crop)
            },
            onFailure = { error -> fail("Auto crop", error) },
        )
    }
    fun choose(preset: CropPreset) {
        val width = picture?.width
        val height = picture?.height
        val ratio = preset.ratio
        when {
            preset == CropPreset.NONE -> apply(preset, null)
            preset == CropPreset.AUTO -> detect()
            width == null || height == null || ratio == null ->
                fail("Crop", IllegalStateException(PICTURE_SIZE_UNKNOWN))
            else -> apply(preset, VideoCropMath.centered(width, height, ratio))
        }
    }
    PanelRow("Crop") {
        PanelDropdown(
            options = CropPreset.entries,
            selected = adjustments.cropPreset,
            label = CropPreset::label,
            placeholder = "None",
            onSelect = ::choose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PICTURE_SIZE_UNKNOWN = "The picture size is not known yet"

private val ZOOM_RANGE = -2f..2f
private val PAN_RANGE = -1f..1f
