package dev.vimivo.desktop.player

import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.core.model.MediaItem
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import java.io.File

/** How files sent to an already running Vimivo are handled. */
internal enum class OpenMode { REPLACE, ADD_TO_QUEUE }

/** A just-resumed file: playback already continues from [positionMillis]; the user may start over instead. */
internal data class ResumePrompt(val title: String, val positionMillis: Long)

/** Everything the player UI renders, as one immutable value. */
internal data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
    val queue: PlaybackQueueState = PlaybackQueueState(),
    val hud: HudMessage? = null,
    val fullscreen: Boolean = false,
    val mediaInfoVisible: Boolean = false,
    val lyrics: LyricsDocument? = null,
    val maxVolume: Float = 1f,
    val presentation: PlayerPresentation = PlayerPresentation.LIBRARY,
    /** Title of the file being opened until its first frame (or first audio) plays; null otherwise. */
    val loadingTitle: String? = null,
    val resumePrompt: ResumePrompt? = null,
    /** Incremented whenever another app hands Vimivo a file, so the window can come to the front. */
    val focusRequest: Int = 0,
    val openMode: OpenMode = OpenMode.REPLACE,
    val folderSiblings: Boolean = true,
)

/** Every user action the player understands; the UI never calls the engine or queue directly for these. */
internal sealed interface PlayerIntent {
    data class Command(val command: DesktopPlayerCommand) : PlayerIntent
    data object TogglePlayback : PlayerIntent
    data object Next : PlayerIntent
    data object Previous : PlayerIntent
    data object Stop : PlayerIntent
    data class Seek(val positionMillis: Long) : PlayerIntent
    data class SetVolume(val volume: Float) : PlayerIntent
    data class SetSpeed(val speed: Float) : PlayerIntent

    /** Files from the OS (Finder, "Open with", a second launch), drag and drop, or the Open menu. */
    data class OpenFiles(
        val files: List<File>,
        val startPositionMillis: Long? = null,
        val fullscreen: Boolean = false,
        val fromRunningInstance: Boolean = false,
    ) : PlayerIntent

    /** Text pasted into the player; opens it when it is a playable link. */
    data class OpenLink(val text: String) : PlayerIntent

    /** A library item: resumes through the library's rules. */
    data class PlayLibraryItem(val media: MediaItem) : PlayerIntent
    data class SetFullscreen(val enabled: Boolean) : PlayerIntent
    data object DismissMediaInfo : PlayerIntent
    data object StartOver : PlayerIntent
    data object DismissResume : PlayerIntent
    data object ShowLibrary : PlayerIntent
    data class SetOpenMode(val mode: OpenMode) : PlayerIntent
    data class SetFolderSiblings(val enabled: Boolean) : PlayerIntent
}
