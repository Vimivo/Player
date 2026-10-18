package dev.vimivo.playback

import dev.vimivo.core.model.MediaKind

/**
 * Decides where playback of previously watched or heard media should start. Shared by every platform so a
 * film paused on the phone and reopened on the desktop follows the same rules.
 */
object ResumePolicy {
    /** Positions this early are treated as "barely started" and restart from the beginning. */
    const val MIN_POSITION_MILLIS = 10_000L

    /** Positions this close to the end are treated as finished. */
    const val END_GUARD_MILLIS = 15_000L

    /** Positions at or past this share of the duration are treated as finished. */
    const val COMPLETION_PERCENT = 95L

    /** Audio at least this long (audiobooks, lectures, podcasts) resumes like video. */
    const val LONG_AUDIO_MILLIS = 20 * 60_000L

    /**
     * @param includeShortAudio resume ordinary songs too; platforms pass `true` only when the user opted in.
     * @return the start position in milliseconds, or `0` to start from the beginning.
     */
    fun startPositionMillis(
        kind: MediaKind,
        savedPositionMillis: Long,
        durationMillis: Long?,
        completed: Boolean,
        includeShortAudio: Boolean = false,
    ): Long {
        val duration = durationMillis?.takeIf { it > 0 }
        val nearEnd = duration != null && savedPositionMillis >= duration - END_GUARD_MILLIS
        val mostlyWatched = duration != null && savedPositionMillis * 100 >= duration * COMPLETION_PERCENT
        val finished = completed || nearEnd || mostlyWatched
        val eligibleKind = kind != MediaKind.AUDIO || includeShortAudio || (duration ?: 0) >= LONG_AUDIO_MILLIS
        return if (savedPositionMillis < MIN_POSITION_MILLIS || finished || !eligibleKind) 0 else savedPositionMillis
    }
}
