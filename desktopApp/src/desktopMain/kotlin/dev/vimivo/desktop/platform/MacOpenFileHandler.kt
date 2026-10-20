package dev.vimivo.desktop.platform

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.awt.Desktop
import java.io.File

/** Buffers Finder open-file events until the Compose playback collector is ready. */
internal class MacOpenFileHandler : AutoCloseable {
    private val pendingFiles = Channel<List<File>>(Channel.UNLIMITED)
    val files: Flow<List<File>> = pendingFiles.receiveAsFlow()

    fun install(): Result<Unit> = runCatching {
        check(Desktop.isDesktopSupported()) { "Desktop integration is unavailable" }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) { "Finder open-file events are unavailable" }
        desktop.setOpenFileHandler { event -> submit(event.files) }
    }

    /** Whether any file has arrived; a launch that received one opens the player, not the library. */
    @Volatile var received: Boolean = false
        private set

    fun submit(files: List<File>) {
        val playable = files.filter(File::isFile)
        if (playable.isNotEmpty()) {
            received = true
            pendingFiles.trySend(playable)
        }
    }

    override fun close() {
        pendingFiles.close()
    }
}
