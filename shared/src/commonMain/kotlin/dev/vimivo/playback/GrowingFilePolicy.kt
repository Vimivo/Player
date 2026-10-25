package dev.vimivo.playback

/** Size and last-modified time of a local file at one moment. */
data class FileSnapshot(val sizeBytes: Long, val modifiedAtMillis: Long)

/**
 * Decides whether a local file that playback just reached the end of is still being written (a download, a
 * recording), so the engine should reload it and continue instead of completing (V1 #3).
 */
object GrowingFilePolicy {
    /** A file modified this recently counts as still being written even if its size has not changed yet. */
    const val ACTIVE_WINDOW_MILLIS: Long = 5_000

    /** Shortest time between two reloads, so a stalled writer does not make playback spin. */
    const val RELOAD_INTERVAL_MILLIS: Long = 1_000

    /** True when [now] is larger than [opened], or was written to within [ACTIVE_WINDOW_MILLIS] of [nowMillis]. */
    fun isGrowing(opened: FileSnapshot, now: FileSnapshot, nowMillis: Long): Boolean =
        now.sizeBytes > opened.sizeBytes || nowMillis - now.modifiedAtMillis in 0 until ACTIVE_WINDOW_MILLIS

    /** Whether to reload now: the file is growing and the last reload is old enough (or there was none). */
    fun shouldReload(opened: FileSnapshot, now: FileSnapshot, nowMillis: Long, lastReloadMillis: Long?): Boolean =
        isGrowing(opened, now, nowMillis) &&
            (lastReloadMillis == null || nowMillis - lastReloadMillis >= RELOAD_INTERVAL_MILLIS)
}
