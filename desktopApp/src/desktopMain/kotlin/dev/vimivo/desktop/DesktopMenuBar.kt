package dev.vimivo.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerPanel
import dev.vimivo.desktop.player.PlayerState
import dev.vimivo.desktop.player.PlayerViewModel
import dev.vimivo.desktop.ui.DesktopDestination
import dev.vimivo.playback.PlaybackSpeeds
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.playback.RecentMediaEntry
import dev.vimivo.playback.VideoAspectRatio
import java.awt.Frame
import java.io.File
import kotlin.math.abs

/**
 * The menu bar of the player and library windows (spec §2.3): File · Edit · View · Playback · Audio · Video ·
 * Subtitles · Window · Help. Every player feature is reachable here with its shortcut; features that are not
 * available yet, or not for the current media, are greyed out rather than hidden.
 *
 * Single-key shortcuts are handled by the window (so typing in text fields keeps working) and only shown in labels;
 * shortcuts with Cmd/Ctrl are real menu accelerators.
 */
@Composable
internal fun FrameWindowScope.VimivoMenuBar(
    state: PlayerState,
    viewModel: PlayerViewModel,
    engine: MpvPlayerEngine,
    recentEntries: List<RecentMediaEntry>,
    actions: AppMenuActions,
) {
    val context = MenuContext(state, viewModel, engine, recentEntries, actions, window)
    MenuBar {
        FileMenu(context)
        EditMenu(context)
        ViewMenu(context)
        PlaybackMenu(context)
        AudioMenu(context)
        VideoMenu(context)
        SubtitlesMenu(context)
        ToolsMenu(context)
        WindowMenu(context)
        HelpMenu(context)
    }
}

/** What every menu needs: the player state, the actions and the window they belong to. */
private class MenuContext(
    val state: PlayerState,
    val viewModel: PlayerViewModel,
    val engine: MpvPlayerEngine,
    val recentEntries: List<RecentMediaEntry>,
    val actions: AppMenuActions,
    val window: java.awt.Window,
) {
    val playback = state.playback
    val hasMedia = playback.media != null
    val run: (DesktopPlayerCommand) -> Unit = viewModel.commands::execute
    val send: (PlayerIntent) -> Unit = viewModel::dispatch
}

@Composable
private fun MenuBarScope.FileMenu(context: MenuContext) = with(context) {
    Menu("File") {
        Item("Open File…", shortcut = primaryShortcut(Key.O), onClick = { actions.onOpenFiles(chooseMediaFiles()) })
        Item("Open Network Stream…", shortcut = primaryShortcut(Key.U), onClick = actions.onOpenStream)
        Item("Open Capture Device…", onClick = { send(PlayerIntent.ToggleCaptureDevice) })
        Item("Connect to Network Share…", onClick = { send(PlayerIntent.ToggleNetworkShare) })
        Item("Open Disc…", enabled = false, onClick = {})
        Item("Add Folder…", shortcut = primaryShortcut(Key.O, shift = true), onClick = actions.onAddFolder)
        Item("Import Playlist…", onClick = { choosePlaylistFile()?.let(actions.onImportPlaylist) })
        Menu("Open Recent") {
            if (recentEntries.isEmpty()) Item("No Recent Media", enabled = false, onClick = {})
            recentEntries.forEach { entry ->
                Item(entry.title, onClick = {
                    runCatching { File(java.net.URI(entry.uri)) }.getOrNull()?.takeIf(File::isFile)
                        ?.let { actions.onOpenFiles(listOf(it)) }
                })
            }
            if (recentEntries.isNotEmpty()) {
                Separator()
                Item("Clear Recent Media", onClick = actions.onClearRecent)
            }
        }
        Separator()
        Item(
            "Media Information…",
            enabled = hasMedia,
            shortcut = primaryShortcut(Key.I),
            onClick = { run(DesktopPlayerCommand.MEDIA_INFO) },
        )
    }
}

@Composable
private fun MenuBarScope.EditMenu(context: MenuContext) = with(context) {
    Menu("Edit") {
        Item("Paste Link", shortcut = primaryShortcut(Key.V), onClick = {
            clipboardText()?.let { send(PlayerIntent.OpenLink(it)) }
        })
        Item("Copy Media Location", enabled = hasMedia, onClick = { playback.media?.uri?.let(::copyToClipboard) })
    }
}

@Composable
private fun MenuBarScope.ViewMenu(context: MenuContext) = with(context) {
    Menu("View") {
        Item("Library", shortcut = primaryShortcut(Key.L), onClick = actions.onShowLibrary)
        Item(
            if (state.fullscreen) "Exit Full Screen" else "Enter Full Screen",
            shortcut = primaryShortcut(Key.F, shift = true),
            onClick = { send(PlayerIntent.SetFullscreen(!state.fullscreen)) },
        )
        CheckboxItem(
            "Always on Top",
            checked = state.alwaysOnTop,
            onCheckedChange = { send(PlayerIntent.ToggleAlwaysOnTop) }
        )
        CheckboxItem(
            "Set as Wallpaper",
            checked = state.wallpaperMode,
            onCheckedChange = { send(PlayerIntent.ToggleWallpaperMode) }
        )
        CheckboxItem("Auto-hide Controls in Window", checked = state.autoHideControls, onCheckedChange = {
            send(PlayerIntent.ToggleAutoHideControls)
        })
        CheckboxItem("Show Remaining Time", checked = state.showRemainingTime, onCheckedChange = {
            send(PlayerIntent.ToggleRemainingTime)
        })
        Item("Command Palette…", shortcut = primaryShortcut(Key.K), onClick = { send(PlayerIntent.ToggleCommandPalette) })
        CheckboxItem(
            "Mini Player",
            checked = state.miniPlayer,
            onCheckedChange = { send(PlayerIntent.ToggleMiniPlayer) },
        )
        CheckboxItem(
            "Picture-in-Picture",
            checked = state.pipMode,
            onCheckedChange = { send(PlayerIntent.TogglePipMode) },
        )
        CheckboxItem(
            "Audio-Only Mode (Hide Video)",
            checked = state.audioOnlyMode,
            onCheckedChange = { send(PlayerIntent.ToggleAudioOnlyMode) },
        )
        Separator()
        Menu("Player Settings") {
            PlayerPanel.entries.forEach { panel ->
                RadioButtonItem(
                    panel.title,
                    selected = state.panel == panel,
                    onClick = { send(PlayerIntent.ShowPanel(panel)) }
                )
            }
            Separator()
            Item("Close Panel", enabled = state.panel != null, onClick = { send(PlayerIntent.ShowPanel(null)) })
        }
        Menu("Hotkey Preset") {
            dev.vimivo.desktop.playback.HotkeyPreset.entries.forEach { p ->
                RadioButtonItem(
                    p.displayName,
                    selected = dev.vimivo.desktop.playback.DesktopPlayerShortcuts.preset == p,
                    onClick = { dev.vimivo.desktop.playback.DesktopPlayerShortcuts.preset = p },
                )
            }
        }
        Separator()
        Item("Customize Controls…", onClick = { send(PlayerIntent.ToggleToolbarCustomize) })
        Menu("Theme") {
            dev.vimivo.settings.ThemeMode.entries.forEach { mode ->
                Item(mode.name.lowercase().replaceFirstChar { it.uppercase() }, onClick = { actions.onSetTheme(mode) })
            }
        }
    }
}

@Composable
private fun MenuBarScope.PlaybackMenu(context: MenuContext) = with(context) {
    Menu("Playback") {
        Item(
            if (playback.status == PlaybackStatus.Playing) "Pause  (Space)" else "Play  (Space)",
            enabled = hasMedia,
            onClick = { send(PlayerIntent.TogglePlayback) },
        )
        Item("Stop", enabled = hasMedia, onClick = { send(PlayerIntent.Stop) })
        Item("Next", shortcut = primaryShortcut(Key.DirectionRight), onClick = { send(PlayerIntent.Next) })
        Item("Previous", shortcut = primaryShortcut(Key.DirectionLeft), onClick = { send(PlayerIntent.Previous) })
        Separator()
        commandItems(
            run,
            hasMedia,
            DesktopPlayerCommand.SEEK_FORWARD_MEDIUM to "L",
            DesktopPlayerCommand.SEEK_BACK_MEDIUM to "J"
        )
        Item("Jump to Time…", enabled = false, onClick = {})
        Separator()
        Menu("Speed") {
            PlaybackSpeeds.PRESETS.forEach { speed ->
                RadioButtonItem(
                    "$speed×",
                    selected = abs(playback.speed - speed) < SPEED_EPSILON,
                    enabled = hasMedia,
                    onClick = { send(PlayerIntent.SetSpeed(speed)) },
                )
            }
            Separator()
            commandItems(
                run,
                hasMedia,
                DesktopPlayerCommand.SPEED_UP to "]",
                DesktopPlayerCommand.SPEED_DOWN to "[",
                DesktopPlayerCommand.SPEED_RESET to "⌫"
            )
        }
        commandItems(
            run,
            hasMedia,
            DesktopPlayerCommand.AB_LOOP to "Shift+L",
            DesktopPlayerCommand.FRAME_STEP_FORWARD to "E",
            DesktopPlayerCommand.FRAME_STEP_BACKWARD to "Shift+E",
        )
        Separator()
        commandItems(
            run,
            true,
            DesktopPlayerCommand.TOGGLE_SHUFFLE to "S",
            DesktopPlayerCommand.CYCLE_REPEAT to "R"
        )
    }
}

@Composable
private fun MenuBarScope.AudioMenu(context: MenuContext) = with(context) {
    Menu("Audio") {
        trackMenu("Audio Track", playback.audioTracks, allowOff = false) { track ->
            viewModel.commands.perform("Audio track") { engine.selectAudioTrack(track?.id) }
        }
        Separator()
        Item(
            "Volume Up",
            shortcut = primaryShortcut(Key.DirectionUp),
            onClick = { run(DesktopPlayerCommand.VOLUME_UP) }
        )
        Item(
            "Volume Down",
            shortcut = primaryShortcut(Key.DirectionDown),
            onClick = { run(DesktopPlayerCommand.VOLUME_DOWN) }
        )
        Item("Mute / Unmute  (M)", onClick = { run(DesktopPlayerCommand.TOGGLE_MUTE) })
        Separator()
        commandItems(
            run,
            hasMedia,
            DesktopPlayerCommand.AUDIO_DELAY_UP to "Shift+H",
            DesktopPlayerCommand.AUDIO_DELAY_DOWN to "Shift+G"
        )
        Item("Audio Settings…", enabled = hasMedia, onClick = { send(PlayerIntent.ShowPanel(PlayerPanel.AUDIO)) })
    }
}

@Composable
private fun MenuBarScope.VideoMenu(context: MenuContext) = with(context) {
    Menu("Video") {
        trackMenu("Video Track", playback.videoTracks, allowOff = true) { track ->
            viewModel.commands.perform("Video track") { engine.selectVideoTrack(track?.id) }
        }
        Menu("Aspect Ratio") {
            VideoAspectRatio.entries.forEach { ratio ->
                RadioButtonItem(
                    ratio.label,
                    selected = playback.videoAdjustments.aspectRatio == ratio,
                    enabled = hasMedia,
                    onClick = {
                        viewModel.commands.perform("${ratio.label} aspect") {
                            engine.applyVideoAdjustments(playback.videoAdjustments.copy(aspectRatio = ratio))
                        }
                    },
                )
            }
        }
        CheckboxItem(
            "Deinterlace  (D)",
            checked = playback.deinterlace,
            enabled = hasMedia,
            onCheckedChange = { run(DesktopPlayerCommand.TOGGLE_DEINTERLACE) },
        )
        commandItems(
            run,
            hasMedia,
            DesktopPlayerCommand.SNAPSHOT to "Shift+S",
            DesktopPlayerCommand.CYCLE_ASPECT_RATIO to "A"
        )
        Menu("360° Video Navigation") {
            Item("Pan Left", enabled = hasMedia, onClick = {
                val adj = playback.videoAdjustments
                engine.applyVideoAdjustments(adj.copy(panX = (adj.panX - 0.1f).coerceIn(-1f, 1f)))
            })
            Item("Pan Right", enabled = hasMedia, onClick = {
                val adj = playback.videoAdjustments
                engine.applyVideoAdjustments(adj.copy(panX = (adj.panX + 0.1f).coerceIn(-1f, 1f)))
            })
            Item("Pan Up", enabled = hasMedia, onClick = {
                val adj = playback.videoAdjustments
                engine.applyVideoAdjustments(adj.copy(panY = (adj.panY - 0.1f).coerceIn(-1f, 1f)))
            })
            Item("Pan Down", enabled = hasMedia, onClick = {
                val adj = playback.videoAdjustments
                engine.applyVideoAdjustments(adj.copy(panY = (adj.panY + 0.1f).coerceIn(-1f, 1f)))
            })
            Item("Reset Pan", enabled = hasMedia, onClick = {
                val adj = playback.videoAdjustments
                engine.applyVideoAdjustments(adj.copy(panX = 0f, panY = 0f, zoom = 0f))
            })
        }
        Item("Picture Settings…", enabled = hasMedia, onClick = { send(PlayerIntent.ShowPanel(PlayerPanel.VIDEO)) })
    }
}

@Composable
private fun MenuBarScope.SubtitlesMenu(context: MenuContext) = with(context) {
    Menu("Subtitles") {
        trackMenu("Subtitle Track", playback.subtitleTracks, allowOff = true) { track ->
            viewModel.commands.perform("Subtitle track") { engine.selectSubtitleTrack(track?.id) }
        }
        commandItems(
            run,
            hasMedia,
            DesktopPlayerCommand.CYCLE_SUBTITLE_TRACK to "V",
            DesktopPlayerCommand.SUBTITLE_DELAY_UP to "H",
            DesktopPlayerCommand.SUBTITLE_DELAY_DOWN to "G",
        )
        Item(
            "Subtitle Settings…",
            enabled = hasMedia,
            onClick = { send(PlayerIntent.ShowPanel(PlayerPanel.SUBTITLES)) }
        )
        Item("Search Online…", onClick = { send(PlayerIntent.ToggleSubtitleSearch) })
    }
}

@Composable
private fun MenuBarScope.ToolsMenu(context: MenuContext) = with(context) {
    Menu("Tools") {
        Item("Command Palette…", shortcut = primaryShortcut(Key.K), onClick = { send(PlayerIntent.ToggleCommandPalette) })
        Item("Open Network Stream…", shortcut = primaryShortcut(Key.N), onClick = actions.onOpenStream)
        Item("Cast to Screen / DLNA…", onClick = { send(PlayerIntent.ToggleCasting) })
        Item("Internet Radio & Podcasts…", onClick = { send(PlayerIntent.ToggleRadioPodcast) })
        Item("Web Remote Control…", onClick = { send(PlayerIntent.ToggleWebRemote) })
        Item("Wi-Fi File Sharing…", onClick = { send(PlayerIntent.ToggleWifiSharing) })
        Item("Convert / Transcode Media…", onClick = { send(PlayerIntent.ToggleTranscodeDialog) })
        Item("User Scripts Folder…", onClick = { send(PlayerIntent.ToggleScripts) })
        CheckboxItem(
            "Record Stream to File",
            checked = state.isRecording,
            enabled = hasMedia,
            onCheckedChange = { send(PlayerIntent.ToggleRecording) },
        )
        Separator()
        Item("Media Information…", enabled = hasMedia, onClick = { run(DesktopPlayerCommand.MEDIA_INFO) })
    }
}

@Composable
private fun MenuBarScope.WindowMenu(context: MenuContext) = with(context) {
    Menu("Window") {
        Item(
            "Minimize",
            shortcut = primaryShortcut(Key.M),
            onClick = { (window as? Frame)?.extendedState = Frame.ICONIFIED },
        )
        Item("Player", onClick = { window.toFront() })
        Item("Library", onClick = actions.onShowLibrary)
    }
}

@Composable
private fun MenuBarScope.HelpMenu(context: MenuContext) = with(context) {
    Menu("Help") {
        Item("Keyboard Shortcuts", onClick = { actions.onNavigate(DesktopDestination.Settings) })
        Item("About Vimivo", onClick = { actions.onNavigate(DesktopDestination.Settings) })
    }
}

private const val SPEED_EPSILON = 0.01f
