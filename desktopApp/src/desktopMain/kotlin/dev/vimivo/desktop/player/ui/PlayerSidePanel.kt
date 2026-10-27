@file:Suppress("LongParameterList") // Composables take their state, callbacks and styling explicitly.

package dev.vimivo.desktop.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vimivo.desktop.DesktopBookmarksPanel
import dev.vimivo.desktop.playback.DesktopPlayerCommandExecutor
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.playback.MpvSubtitleFiles
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerPanel
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.EqualizerPreset
import dev.vimivo.playback.PlaybackControlPolicy
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.SubtitleStyle
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The player's settings panel (spec §2.4): 380 dp, full height beside the video, tabs as a segmented control,
 * content scrolling inside the panel. Every engine call goes through [DesktopPlayerCommandExecutor.perform], so a
 * failure shows on the HUD and playback continues.
 */
@Composable
internal fun PlayerSidePanel(
    state: PlayerState,
    panel: PlayerPanel,
    engine: MpvPlayerEngine,
    commands: DesktopPlayerCommandExecutor,
    onIntent: (PlayerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(PANEL_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(PlayerPalette.glass)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(
                options = PlayerPanel.entries,
                selected = panel,
                label = PlayerPanel::title,
                onSelect = { onIntent(PlayerIntent.ShowPanel(it)) },
                modifier = Modifier.weight(1f),
                weight = { it.title.length + TAB_PADDING_CHARS },
            )
            Spacer(Modifier.width(6.dp))
            PlayerIconButton(Icons.Outlined.Close, "Close panel") { onIntent(PlayerIntent.ShowPanel(null)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            val playback = state.playback
            when (panel) {
                PlayerPanel.VIDEO -> VideoTab(playback, state.snapshot, engine, commands) {
                    onIntent(PlayerIntent.SetSnapshotOptions(it))
                }
                PlayerPanel.AUDIO -> AudioTab(playback, engine, commands)
                PlayerPanel.SUBTITLES -> SubtitlesTab(playback, engine, commands)
                PlayerPanel.SYNC -> SyncTab(playback, engine, commands)
                PlayerPanel.BOOKMARKS -> {
                    Spacer(Modifier.height(12.dp))
                    DesktopBookmarksPanel(
                        media = playback.media,
                        positionMillis = playback.positionMillis,
                        bookmarks = state.bookmarks,
                        onAdd = { _, _, label -> onIntent(PlayerIntent.AddBookmark(label)) },
                        onSeek = { onIntent(PlayerIntent.Seek(it)) },
                        onRename = { id, label -> onIntent(PlayerIntent.RenameBookmark(id, label)) },
                        onDelete = { onIntent(PlayerIntent.DeleteBookmark(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTab(playback: PlaybackState, engine: MpvPlayerEngine, commands: DesktopPlayerCommandExecutor) {
    PanelSection("Track")
    PanelRow("Audio track") {
        TrackDropdown(playback.audioTracks, "Default") { track ->
            commands.perform("Audio track") { engine.selectAudioTrack(track?.id) }
        }
    }
    PanelSection("Speed")
    PanelRow("Playback speed") {
        PanelDropdown(
            options = PlaybackSpeeds.PRESETS,
            selected = PlaybackSpeeds.PRESETS.minByOrNull { abs(it - playback.speed) },
            label = { "$it×" },
            placeholder = "1.0×",
            onSelect = { speed -> commands.perform("Speed") { engine.setPlaybackSpeed(speed) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Fine") {
        SpeedStepper(playback.speed) { speed -> commands.perform("Speed") { engine.setPlaybackSpeed(speed) } }
    }

    val audioState by engine.audioEnhancementState.collectAsState()
    val audioSettings = audioState.settings

    PanelSection("Equalizer & Presets")
    PanelRow("Preset") {
        PanelDropdown(
            options = EqualizerPreset.entries,
            selected = audioSettings.preset,
            label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            placeholder = "Flat",
            onSelect = { preset ->
                val next = audioSettings.copy(enabled = true, preset = preset)
                engine.applyAudioEnhancements(next)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    PanelSection("DSP Enhancements")
    SliderRow(
        "Bass Boost",
        audioSettings.bassBoostStrength,
        0f..1f,
        "${(audioSettings.bassBoostStrength * 100).roundToInt()}%",
        reset = 0f,
    ) { strength ->
        engine.applyAudioEnhancements(audioSettings.copy(bassBoostStrength = strength))
    }

    SliderRow(
        "Virtualizer",
        audioSettings.virtualizerStrength,
        0f..1f,
        "${(audioSettings.virtualizerStrength * 100).roundToInt()}%",
        reset = 0f,
    ) { strength ->
        engine.applyAudioEnhancements(audioSettings.copy(virtualizerStrength = strength))
    }

    PanelRow("Clarity") {
        PanelButton(
            if (audioSettings.dialogueClarityEnabled) "Dialogue Clarity (On)" else "Dialogue Clarity (Off)",
            modifier = Modifier.fillMaxWidth(),
        ) {
            val next = !audioSettings.dialogueClarityEnabled
            engine.applyAudioEnhancements(audioSettings.copy(dialogueClarityEnabled = next))
        }
    }

    PanelRow("Norm.") {
        PanelButton(
            if (audioSettings.volumeNormalization) "Volume Normalization (On)" else "Volume Normalization (Off)",
            modifier = Modifier.fillMaxWidth(),
        ) {
            val next = !audioSettings.volumeNormalization
            engine.applyAudioEnhancements(audioSettings.copy(volumeNormalization = next))
        }
    }
}

/** Speed in ±0.05× steps (spec #4). */
@Composable
private fun RowScope.SpeedStepper(speed: Float, onSpeed: (Float) -> Unit) {
    fun next(delta: Float) =
        ((speed + delta) * SPEED_PRECISION).roundToInt().div(SPEED_PRECISION).coerceIn(PlaybackSpeeds.RANGE)
    PlayerIconButton(Icons.Outlined.Remove, "Slower 0.05×") { onSpeed(next(-SPEED_STEP)) }
    PanelText("%.2f×".format(speed), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
    PlayerIconButton(Icons.Outlined.Add, "Faster 0.05×") { onSpeed(next(SPEED_STEP)) }
    if (abs(speed - 1f) > SPEED_EPSILON) PlayerIconButton(Icons.Outlined.RestartAlt, "Normal speed") { onSpeed(1f) }
}

@Composable
private fun SubtitlesTab(playback: PlaybackState, engine: MpvPlayerEngine, commands: DesktopPlayerCommandExecutor) {
    var style by remember { mutableStateOf(SubtitleStyle()) }
    fun restyle(next: SubtitleStyle) {
        if (commands.perform("Subtitle style") { engine.applySubtitleStyle(next) }) style = next
    }
    PanelSection("Track")
    PanelRow("Subtitles") {
        TrackDropdown(playback.subtitleTracks, "Off", allowOff = true) { track ->
            commands.perform("Subtitle track") { engine.selectSubtitleTrack(track?.id) }
        }
    }
    PanelRow("Secondary") {
        TrackDropdown(playback.subtitleTracks, "Off", allowOff = true) { track ->
            restyle(style.copy(secondaryTrackId = track?.id))
        }
    }
    PanelRow("File") {
        PanelButton("Load subtitle file…", Modifier.fillMaxWidth()) {
            chooseSubtitleFile()?.let(MpvSubtitleFiles::forFile)?.let { subtitle ->
                commands.perform("Load subtitle") { engine.addExternalSubtitle(subtitle) }
            }
        }
    }
    PanelRow("Encoding") {
        PanelDropdown(
            options = listOf("auto", "utf-8", "windows-1252", "iso-8859-1", "gbk", "big5", "shift_jis", "euc-kr"),
            selected = style.encoding,
            label = { it.uppercase() },
            placeholder = "AUTO",
            onSelect = { restyle(style.copy(encoding = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelSection("Appearance")
    PanelRow("Font") {
        PanelDropdown(
            options = listOf("sans-serif", "serif", "monospace", "Arial", "Trebuchet MS", "Helvetica"),
            selected = style.fontName ?: "sans-serif",
            label = { it },
            placeholder = "sans-serif",
            onSelect = { restyle(style.copy(fontName = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SliderRow(
        "Size",
        style.fontSizeSp.toFloat(),
        SUBTITLE_SIZE_RANGE,
        "${style.fontSizeSp} pt",
        reset = DEFAULT_SUBTITLE_SIZE
    ) {
        restyle(style.copy(fontSizeSp = it.roundToInt()))
    }
    PanelRow("Colour") {
        SegmentedChoice(
            options = SUBTITLE_COLOURS.keys.toList(),
            selected = style.textColorHex,
            label = { SUBTITLE_COLOURS.getValue(it) },
            onSelect = { restyle(style.copy(textColorHex = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SliderRow(
        "Outline",
        style.outlineSize,
        0f..6f,
        "%.1f pt".format(style.outlineSize),
        reset = 2f,
    ) {
        restyle(style.copy(outlineSize = it))
    }
    SliderRow(
        "Shadow",
        style.shadowOffset,
        0f..6f,
        "%.1f pt".format(style.shadowOffset),
        reset = 1f,
    ) {
        restyle(style.copy(shadowOffset = it))
    }
    PanelRow("Background") {
        SegmentedChoice(
            options = listOf(null, "#80000000", "#FF000000"),
            selected = style.backgroundColorHex,
            label = { when (it) { null -> "None"; "#80000000" -> "Translucent"; else -> "Solid" } },
            onSelect = { restyle(style.copy(backgroundColorHex = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("ASS override") {
        SegmentedChoice(
            options = listOf("no", "yes", "force", "strip"),
            selected = style.assOverride,
            label = { it.replaceFirstChar(Char::titlecase) },
            onSelect = { restyle(style.copy(assOverride = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SliderRow(
        "Position",
        style.bottomMarginPercent.toFloat(),
        SUBTITLE_MARGIN_RANGE,
        "${style.bottomMarginPercent} %",
        reset = DEFAULT_SUBTITLE_MARGIN
    ) {
        restyle(style.copy(bottomMarginPercent = it.roundToInt()))
    }
}

@Composable
private fun SyncTab(playback: PlaybackState, engine: MpvPlayerEngine, commands: DesktopPlayerCommandExecutor) {
    PanelSection("Delays")
    val audioStep = PlaybackControlPolicy.AUDIO_DELAY_STEP_MILLIS
    val subtitleStep = PlaybackControlPolicy.SUBTITLE_DELAY_STEP_MILLIS
    PanelRow("Audio delay") {
        DelayStepper(
            "Audio delay",
            playback.audioDelayMillis,
            audioStep,
            onStep = { delta ->
                val next = PlaybackControlPolicy.stepDelay(playback.audioDelayMillis, delta)
                commands.perform("Audio delay") { engine.setAudioDelay(next) }
            },
            onReset = { commands.perform("Reset audio delay") { engine.setAudioDelay(0) } },
        )
    }
    PanelRow("Subtitle delay") {
        DelayStepper(
            "Subtitle delay",
            playback.subtitleDelayMillis,
            subtitleStep,
            onStep = { delta ->
                val next = PlaybackControlPolicy.stepDelay(playback.subtitleDelayMillis, delta)
                commands.perform("Subtitle delay") { engine.setSubtitleDelay(next) }
            },
            onReset = { commands.perform("Reset subtitle delay") { engine.setSubtitleDelay(0) } },
        )
    }
    var secondaryDelay by remember { mutableStateOf(0L) }
    PanelRow("Secondary delay") {
        DelayStepper(
            "Secondary delay",
            secondaryDelay,
            subtitleStep,
            onStep = { delta ->
                val next = PlaybackControlPolicy.stepDelay(secondaryDelay, delta)
                if (commands.perform("Secondary sub delay") { engine.setSecondarySubtitleDelay(next) }) {
                    secondaryDelay = next
                }
            },
            onReset = {
                if (commands.perform("Reset secondary delay") { engine.setSecondarySubtitleDelay(0) }) {
                    secondaryDelay = 0L
                }
            },
        )
    }
    PanelSection("Timing factor")
    var subSpeed by remember { mutableStateOf(1.0f) }
    SliderRow(
        "Sub speed",
        subSpeed,
        0.5f..2.0f,
        "%.2f×".format(subSpeed),
        reset = 1.0f,
    ) {
        val s = ((it * 100f).roundToInt() / 100f).coerceIn(0.5f, 2.0f)
        if (commands.perform("Subtitle speed") { engine.setSubtitleSpeed(s) }) {
            subSpeed = s
        }
    }
    var subFps by remember { mutableStateOf(0f) }
    PanelRow("Sub FPS") {
        PanelDropdown(
            options = listOf(0f, 23.976f, 24f, 25f, 29.97f, 30f),
            selected = subFps,
            label = { if (it == 0f) "Original" else "%.3f fps".format(it) },
            placeholder = "Original",
            onSelect = { fps ->
                if (commands.perform("Subtitle FPS") { engine.setSubtitleFps(fps) }) {
                    subFps = fps
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PanelRow("Sync") {
        PanelButton("Sync to now", Modifier.fillMaxWidth()) {
            commands.perform("Sync to now") { engine.syncSubtitleToNow() }
        }
    }
    PanelSection("A–B repeat")
    val ab = playback.abRepeat
    PanelRow("Loop") {
        PanelButton(ab.pointA?.let { "A ${playerClock(it)}" } ?: "Set A", Modifier.weight(1f)) {
            commands.perform("Set point A") { engine.setABRepeat(ABRepeatRange(pointA = playback.positionMillis)) }
        }
        Spacer(Modifier.width(6.dp))
        PanelButton(
            ab.pointB?.let { "B ${playerClock(it)}" } ?: "Set B",
            Modifier.weight(1f),
            enabled = ab.pointA != null
        ) {
            val start = ab.pointA ?: return@PanelButton
            if (playback.positionMillis > start) {
                commands.perform(
                    "Set point B"
                ) { engine.setABRepeat(ABRepeatRange(start, playback.positionMillis, enabled = true)) }
            }
        }
        if (ab.pointA != null) {
            Spacer(Modifier.width(6.dp))
            PanelButton("Clear") { commands.perform("Clear loop") { engine.setABRepeat(ABRepeatRange()) } }
        }
    }
    PanelSection("Chapters")
    PanelRow("Chapter") {
        PanelButton("◀ Previous", Modifier.weight(1f), enabled = playback.chapters.isNotEmpty()) {
            commands.perform("Previous chapter") { engine.previousChapter() }
        }
        Spacer(Modifier.width(6.dp))
        PanelButton("Next ▶", Modifier.weight(1f), enabled = playback.chapters.isNotEmpty()) {
            commands.perform("Next chapter") { engine.nextChapter() }
        }
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    reset: Float,
    onChange: (Float) -> Unit,
) {
    PanelRow(label) {
        VimivoSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            label = label,
            valueText = valueText,
            resetValue = reset,
            modifier = Modifier.weight(1f),
        )
        PanelText(
            valueText,
            modifier = Modifier.width(VALUE_WIDTH).padding(start = 8.dp),
            color = PlayerPalette.contentMuted,
            style = PANEL_TEXT.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun ColourSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    SliderRow(label, value.toFloat(), COLOUR_RANGE, "%+d".format(value), reset = 0f) { onChange(it.roundToInt()) }
}

private fun chooseSubtitleFile(): File? {
    val dialog = FileDialog(null as Frame?, "Open Subtitle File", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> File(name).extension.lowercase() in MpvSubtitleFiles.EXTENSIONS }
    dialog.isVisible = true
    return dialog.files?.firstOrNull() ?: dialog.file?.let { File(dialog.directory, it) }
}

private val PANEL_WIDTH = 380.dp
private const val TAB_PADDING_CHARS = 2f
private val VALUE_WIDTH = 64.dp
private val COLOUR_RANGE = -50f..50f
private val SUBTITLE_SIZE_RANGE = 12f..64f
private val SUBTITLE_MARGIN_RANGE = 0f..30f
private const val DEFAULT_SUBTITLE_SIZE = 24f
private const val DEFAULT_SUBTITLE_MARGIN = 5f
private const val SPEED_STEP = 0.05f
private const val SPEED_PRECISION = 100f
private const val SPEED_EPSILON = 0.001f
private val SUBTITLE_COLOURS = linkedMapOf("#FFFFFF" to "White", "#FFFF00" to "Yellow", "#00FFFF" to "Cyan")
