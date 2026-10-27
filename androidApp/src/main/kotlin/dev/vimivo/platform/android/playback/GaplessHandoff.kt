package dev.vimivo.platform.android.playback

import androidx.media3.common.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import dev.vimivo.core.model.MediaItem as VimivoMediaItem

/**
 * The queue side of gapless playback for the session adapter: it asks the engine to prepare the item that follows the
 * current one, and recognises when the engine started that item by itself so the queue can follow without opening it
 * again. Only audio followed by different audio is prepared.
 */
internal class GaplessHandoff(
    private val prepareNext: (VimivoMediaItem?) -> Result<Unit>,
    private val toVimivo: (MediaItem) -> VimivoMediaItem,
) {
    private var preparedIndex: Int? = null

    /** The (current, following) ids the engine was last asked about, so a refusal is not repeated on every tick. */
    private var preparedFor: Pair<String, String?>? = null

    /** Forget any preparation, for example when the engine is about to be replaced. */
    fun reset() {
        preparedIndex = null
        preparedFor = null
    }

    /** The playlist index the engine moved on to by itself; null while it is still on the queue's current item. */
    fun followedIndex(state: PlaybackState, playlist: List<MediaItem>, currentIndex: Int): Int? {
        val index = preparedIndex ?: return null
        val playing = state.media?.id?.value ?: return null
        val onCurrent = playlist.getOrNull(currentIndex)?.mediaId == playing
        val moved = !onCurrent && playlist.getOrNull(index)?.mediaId == playing
        if (moved) reset()
        return index.takeIf { moved }
    }

    /**
     * Prepares item [nextIndex] (null: nothing follows, or the queue must stop here, so any earlier preparation is
     * cleared) once the current item plays.
     */
    fun prepareFollowing(state: PlaybackState, playlist: List<MediaItem>, currentIndex: Int, nextIndex: Int?) {
        val current = playlist.getOrNull(currentIndex)
        val playing = state.status == PlaybackStatus.Playing || state.status == PlaybackStatus.Paused
        if (!playing || current == null || state.media?.id?.value != current.mediaId) return
        val next = nextIndex?.let(playlist::get)
        val key = current.mediaId to next?.mediaId
        if (key == preparedFor || (next == null && preparedIndex == null)) return
        preparedFor = key
        val following = next?.let(toVimivo)?.takeIf { audioPair(toVimivo(current), it) }
        preparedIndex = nextIndex.takeIf { prepareNext(following).isSuccess && following != null }
    }

    private fun audioPair(current: VimivoMediaItem, next: VimivoMediaItem) =
        current.id != next.id && current.kind == MediaKind.AUDIO && next.kind == MediaKind.AUDIO
}
