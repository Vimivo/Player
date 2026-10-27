package dev.vimivo.desktop

import dev.vimivo.desktop.ui.DesktopDestination
import java.io.File

/** What the menu bar does that depends on which windows are open. */
@Suppress("LongParameterList") // One callback per window-dependent menu action.
internal class AppMenuActions(
    val onOpenFiles: (List<File>) -> Unit,
    val onOpenStream: () -> Unit,
    val onAddFolder: () -> Unit,
    val onImportPlaylist: (File) -> Unit,
    val onClearRecent: () -> Unit,
    val onNavigate: (DesktopDestination) -> Unit,
    val onShowLibrary: () -> Unit,
    val onSetTheme: (dev.vimivo.settings.ThemeMode) -> Unit = {},
    val onOpenDisc: () -> Unit = {},
)
