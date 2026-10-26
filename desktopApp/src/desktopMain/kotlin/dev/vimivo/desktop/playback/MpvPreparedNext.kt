package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind

/**
 * The file appended to libmpv's playlist to follow the current one (gapless audio, `PlayerEngine.prepareNext`).
 * With `gapless-audio=yes` libmpv starts it as soon as the current file ends, without closing the audio output.
 * Audio only: video would need a new renderer set-up per file.
 */
internal class MpvPreparedNext(
    /** Empties libmpv's playlist except for the file that is playing. */
    private val clearPlaylist: () -> Unit,
    /** Appends a file (a libmpv target) to the playlist. */
    private val append: (String) -> Unit,
) {
    @Volatile
    var pending: MediaItem? = null
        private set

    /** Replaces the prepared file by [next] (null clears it); [current] is the file now playing. */
    fun prepare(current: MediaItem?, next: MediaItem?) {
        if (next != null) {
            require(current?.kind == MediaKind.AUDIO && next.kind == MediaKind.AUDIO) { "Gapless is for audio" }
        }
        clearPlaylist()
        pending = null
        if (next != null) {
            append(MpvPlayerEngine.mpvTarget(next.uri))
            pending = next
        }
    }

    /** The prepared file once libmpv has moved on to it: it is removed from the list and no longer pending. */
    fun take(): MediaItem? {
        val next = pending ?: return null
        pending = null
        return next
    }

    fun clear() {
        pending = null
    }
}
