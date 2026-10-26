package dev.vimivo.desktop.player

import dev.vimivo.core.lyrics.LyricsDocument
import dev.vimivo.core.model.Bookmark
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.desktop.playback.DesktopPlayerCommand
import dev.vimivo.playback.ABRepeatRange
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.SnapshotOptions
import java.io.File

/** How files sent to an already running Vimivo are handled. */
internal enum class OpenMode { REPLACE, ADD_TO_QUEUE }

/** The player's settings side panel; one tab at a time (spec §2.4). */
internal enum class PlayerPanel(val title: String) { VIDEO(
    "Video"
), AUDIO("Audio"), SUBTITLES("Subtitles"), SYNC("Sync"), BOOKMARKS("Bookmarks") }

/** Where the user dragged the fullscreen controller, as an offset from its default bottom-centre place. */
internal data class ControllerOffset(val x: Int, val y: Int)

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
    /** Incremented to ask for the library window (menu, "Library" button). */
    val libraryRequest: Int = 0,
    /** Bookmarks of the current media. */
    val bookmarks: List<Bookmark> = emptyList(),
    val openMode: OpenMode = OpenMode.REPLACE,
    val folderSiblings: Boolean = true,
    /** The open side-panel tab, or null when the panel is closed. */
    val panel: PlayerPanel? = null,
    /** The control bar shows "-remaining / total" instead of "elapsed / total". */
    val showRemainingTime: Boolean = false,
    val controllerOffset: ControllerOffset = ControllerOffset(0, 0),
    /** The player window floats above other apps' windows (View ▸ Always on Top). */
    val alwaysOnTop: Boolean = false,
    /** Windowed video hides the control bar after the pointer rests (View ▸ Auto-hide Controls; off by default). */
    val autoHideControls: Boolean = false,
    /** Snapshot format, subtitles and folder (Video panel ▸ Snapshot); remembered. */
    val snapshot: SnapshotOptions = SnapshotOptions(),
)

/** Every user action the player understands; the UI never calls the engine or queue directly for these. */
internal sealed interface PlayerIntent {
    data class Command(val command: DesktopPlayerCommand) : PlayerIntent
    data object TogglePlayback : PlayerIntent
    data object Next : PlayerIntent
    data object Previous : PlayerIntent
    data object Stop : PlayerIntent

    /** [exact] false is a fast keyframe seek, used while scrubbing. */
    data class Seek(val positionMillis: Long, val exact: Boolean = true) : PlayerIntent
    data class SetAbRepeat(val range: ABRepeatRange) : PlayerIntent
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

    /**
     * Starts [items] at [index] in the order shown on screen; the rest of the list continues automatically
     * and earlier items stay reachable with Previous.
     */
    data class PlayFrom(val items: List<MediaItem>, val index: Int, val label: String? = null) : PlayerIntent
    data class SetFullscreen(val enabled: Boolean) : PlayerIntent
    data object DismissMediaInfo : PlayerIntent
    data object StartOver : PlayerIntent
    data object DismissResume : PlayerIntent
    data object ShowLibrary : PlayerIntent

    /** Closes the player window: playback stops and the window hides (it is reused for the next file). */
    data object ClosePlayer : PlayerIntent
    data class AddBookmark(val label: String?) : PlayerIntent
    data class RenameBookmark(val id: String, val label: String) : PlayerIntent
    data class DeleteBookmark(val id: String) : PlayerIntent
    data class SetOpenMode(val mode: OpenMode) : PlayerIntent
    data class SetFolderSiblings(val enabled: Boolean) : PlayerIntent

    /** Opens the side panel on [panel], or closes it when null. */
    data class ShowPanel(val panel: PlayerPanel?) : PlayerIntent
    data class SetSnapshotOptions(val options: SnapshotOptions) : PlayerIntent
    data object ToggleRemainingTime : PlayerIntent
    data class MoveController(val offset: ControllerOffset) : PlayerIntent
    data object ToggleAlwaysOnTop : PlayerIntent
    data object ToggleAutoHideControls : PlayerIntent
}

/**
 * Which window layout plays [playing]: video always needs the player window's video surface; an audio item that
 * follows video keeps the player window in its audio layout; audio started from the library stays in the library.
 */
internal fun nextPresentation(current: PlayerPresentation, playing: MediaKind?): PlayerPresentation = when {
    playing == MediaKind.VIDEO || playing == MediaKind.STREAM -> PlayerPresentation.VIDEO
    playing == MediaKind.AUDIO && current == PlayerPresentation.VIDEO -> PlayerPresentation.AUDIO
    else -> current
}

/** The docked control bar shows outside fullscreen; audio keeps it in fullscreen too (there is no picture). */
internal fun PlayerState.showsControlBar(): Boolean = !fullscreen || presentation == PlayerPresentation.AUDIO

/** Auto-hide applies only to windowed video, and only when the user turned it on. */
internal fun PlayerState.autoHidesControls(showsVideo: Boolean): Boolean = autoHideControls && showsVideo && !fullscreen
