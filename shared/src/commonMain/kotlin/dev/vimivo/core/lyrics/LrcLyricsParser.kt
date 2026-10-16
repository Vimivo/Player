@file:Suppress("ArgumentListWrapping", "CyclomaticComplexMethod", "MaximumLineLength", "MaxLineLength", "NestedBlockDepth")

package dev.vimivo.core.lyrics

data class LyricLine(
    val timeMillis: Long?,
    val text: String,
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val synchronized: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val offsetMillis: Long = 0L,
    val sourceUri: String = "memory:",
) {
    /**
     * Binary search to find the active lyric line index for a given playback position in milliseconds.
     * Returns -1 if no line has started yet or if lyrics are not synchronized.
     */
    fun findActiveLineIndex(positionMillis: Long): Int {
        if (!synchronized || lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var candidate = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val lineTime = lines[mid].timeMillis
            if (lineTime != null && lineTime <= positionMillis) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return candidate
    }
}

/**
 * Robust, cross-platform LRC lyrics parser supporting metadata tags,
 * multi-timestamp lines, millisecond offsets, and full UTF-8 (including Bengali and CJK).
 */
object LrcLyricsParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val METADATA_TAG_REGEX = Regex("""^\[(ti|ar|al|by|offset|length|re|ve):(.*)]$""", RegexOption.IGNORE_CASE)

    fun parse(text: String, sourceUri: String = "memory:"): LyricsDocument {
        val metadata = mutableMapOf<String, String>()
        var offsetMillis = 0L

        // First pass: extract metadata headers (especially offset)
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val metaMatch = METADATA_TAG_REGEX.matchEntire(trimmed)
            if (metaMatch != null) {
                val key = metaMatch.groupValues[1].lowercase()
                val value = metaMatch.groupValues[2].trim()
                metadata[key] = value
                if (key == "offset") {
                    offsetMillis = value.toLongOrNull() ?: 0L
                }
            }
        }

        // Second pass: extract lyrics lines
        val parsedLines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // Skip metadata tag lines
            if (METADATA_TAG_REGEX.matches(trimmed)) return@forEach

            val timestampMatches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            val content = TIMESTAMP_REGEX.replace(trimmed, "").trim()

            if (timestampMatches.isEmpty()) {
                if (content.isNotEmpty() && !content.startsWith('[')) {
                    parsedLines.add(LyricLine(timeMillis = null, text = content))
                }
            } else if (content.isNotEmpty()) {
                timestampMatches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues[3].padEnd(3, '0').take(3)
                    val fraction = fractionStr.toLongOrNull() ?: 0L

                    val rawTimeMillis = (minutes * 60 + seconds) * 1_000L + fraction
                    val adjustedTimeMillis = (rawTimeMillis + offsetMillis).coerceAtLeast(0L)
                    parsedLines.add(LyricLine(timeMillis = adjustedTimeMillis, text = content))
                }
            }
        }

        val sortedLines = parsedLines.sortedWith(compareBy(nullsLast()) { it.timeMillis })
        val isSynchronized = sortedLines.any { it.timeMillis != null }

        return LyricsDocument(
            lines = sortedLines,
            synchronized = isSynchronized,
            metadata = metadata,
            offsetMillis = offsetMillis,
            sourceUri = sourceUri,
        )
    }
}
