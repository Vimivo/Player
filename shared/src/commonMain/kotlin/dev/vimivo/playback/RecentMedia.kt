package dev.vimivo.playback

/** A media source the user explicitly opened, kept only on-device for "Open Recent". */
data class RecentMediaEntry(
    val uri: String,
    val title: String,
    val isVideo: Boolean,
)

object RecentMediaList {
    const val DEFAULT_CAPACITY = 15

    /** Moves [entry] to the front, removing older copies of the same URI and trimming to [capacity]. */
    fun record(
        current: List<RecentMediaEntry>,
        entry: RecentMediaEntry,
        capacity: Int = DEFAULT_CAPACITY,
    ): List<RecentMediaEntry> {
        require(capacity > 0) { "Recent media capacity must be positive" }
        if (entry.uri.isBlank()) return current.take(capacity)
        return (listOf(entry) + current.filterNot { it.uri == entry.uri }).take(capacity)
    }
}
