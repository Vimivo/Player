package dev.vimivo.desktop.library

import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/** JVM-native directory chooser for direct macOS distribution; sandbox bookmarks are intentionally not claimed. */
internal object DesktopFolderPicker {
    fun chooseDirectory(): Result<File?> = runCatching {
        require(!GraphicsEnvironment.isHeadless()) { "A graphical folder picker is unavailable in this environment" }
        var result: File? = null
        val chooser: () -> Unit = {
            JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isMultiSelectionEnabled = false
                dialogTitle = "Add Vimivo Library Folder"
                if (showOpenDialog(null) == JFileChooser.APPROVE_OPTION) result = selectedFile
            }
        }
        if (SwingUtilities.isEventDispatchThread()) chooser() else SwingUtilities.invokeAndWait(chooser)
        result
    }
}
