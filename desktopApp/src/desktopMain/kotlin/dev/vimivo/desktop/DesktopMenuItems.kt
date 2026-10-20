package dev.vimivo.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuScope
import dev.vimivo.desktop.platform.DesktopPlatform
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.playback.TrackInfo

/** A radio list of [tracks]; "Off" first when [allowOff]. Greyed out when the media has none. */
@Composable
internal fun MenuScope.trackMenu(
    title: String,
    tracks: List<TrackInfo>,
    allowOff: Boolean,
    onSelect: (TrackInfo?) -> Unit
) {
    Menu(title, enabled = tracks.isNotEmpty()) {
        if (allowOff) {
            RadioButtonItem(
                "Off",
                selected = tracks.none(TrackInfo::isSelected),
                onClick = { onSelect(null) }
            )
        }
        tracks.forEach { track ->
            val label = listOfNotNull(track.label, track.language?.takeIf { it != track.label }).joinToString(" · ")
            RadioButtonItem(
                label.ifBlank { "Track ${track.id}" },
                selected = track.isSelected,
                onClick = { onSelect(track) }
            )
        }
    }
}

/** Menu items for window-handled single-key commands; the key shows in the label. */
@Composable
internal fun MenuScope.commandItems(
    run: (DesktopPlayerCommand) -> Unit,
    enabled: Boolean,
    vararg items: Pair<DesktopPlayerCommand, String>,
) {
    items.forEach { (command, key) -> Item("${command.label}  ($key)", enabled = enabled, onClick = { run(command) }) }
}

/** Menu accelerators use Cmd on macOS and Ctrl elsewhere, as documented in KEYBOARD_SHORTCUTS.md. */
internal fun primaryShortcut(key: Key, shift: Boolean = false) =
    if (DesktopPlatform.detect() == DesktopPlatform.MACOS) {
        KeyShortcut(key, meta = true, shift = shift)
    } else {
        KeyShortcut(key, ctrl = true, shift = shift)
    }

internal fun copyToClipboard(text: String) {
    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
}
