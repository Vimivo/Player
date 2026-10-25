package dev.vimivo.playback

/** Image formats a snapshot can be saved in (V1 #16). */
enum class SnapshotFormat(val extension: String, val label: String) {
    PNG("png", "PNG"),
    JPEG("jpg", "JPEG"),
}

/** How snapshots are taken and where they go. [folder] null means the platform's default pictures location. */
data class SnapshotOptions(
    val format: SnapshotFormat = SnapshotFormat.PNG,
    /** Burn the visible subtitles into the image. */
    val includeSubtitles: Boolean = false,
    val folder: String? = null,
)

object SnapshotNaming {
    private const val MAX_TITLE_LENGTH = 60
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * A file name for a snapshot: `<title>-<h>h<mm>m<ss>s-<timestamp>.<ext>`. The title is cleaned of characters that
     * file systems reject, collapsed and shortened; the position and the time of the snapshot keep names unique.
     */
    fun fileName(title: String?, positionMillis: Long, timestampMillis: Long, format: SnapshotFormat): String {
        val clean = (title ?: "")
            .replace(UNSAFE, " ")
            .replace(WHITESPACE, " ")
            .trim(' ', '.')
            .take(MAX_TITLE_LENGTH)
            .trim(' ', '.')
            .ifEmpty { "vimivo" }
        val totalSeconds = (positionMillis.coerceAtLeast(0) / MILLIS_PER_SECOND)
        val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
        val minutes = totalSeconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val position = "${hours}h${minutes.toString().padStart(2, '0')}m${seconds.toString().padStart(2, '0')}s"
        return "$clean-$position-$timestampMillis.${format.extension}"
    }
}
