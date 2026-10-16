@file:Suppress("ArgumentListWrapping", "MaximumLineLength", "MaxLineLength", "NestedBlockDepth")

package dev.vimivo.core.playlist

data class PlaylistItemEntry(
    val uri: String,
    val title: String? = null,
    val durationSeconds: Int? = null,
)

data class ParsedPlaylist(
    val name: String,
    val entries: List<PlaylistItemEntry>,
)

/**
 * Portable M3U / M3U8 playlist parser and generator.
 * Handles UTF-8 encoding, #EXTINF duration and title metadata,
 * ignores HLS streaming tags (#EXT-X-...), and preserves media order.
 */
object PlaylistM3uHandler {

    private val EXTINF_REGEX = Regex("""^#EXTINF:(-?\d+)\s*,\s*(.*)$""")

    fun parseM3u(content: String, defaultName: String = "Imported Playlist"): ParsedPlaylist {
        val entries = mutableListOf<PlaylistItemEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null
        var playlistName = defaultName

        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val match = EXTINF_REGEX.matchEntire(trimmed)
                    if (match != null) {
                        pendingDuration = match.groupValues[1].toIntOrNull()
                        pendingTitle = match.groupValues[2].trim().takeIf { it.isNotEmpty() }
                    }
                } else if (trimmed.startsWith("#PLAYLIST:", ignoreCase = true)) {
                    playlistName = trimmed.substringAfter(":").trim()
                }
                // Skip other comment or HLS tags
                return@forEach
            }

            // Normal URI or path line
            entries.add(
                PlaylistItemEntry(
                    uri = trimmed,
                    title = pendingTitle,
                    durationSeconds = pendingDuration,
                ),
            )
            pendingTitle = null
            pendingDuration = null
        }

        return ParsedPlaylist(
            name = playlistName,
            entries = entries,
        )
    }

    fun exportM3u8(playlistName: String, entries: List<PlaylistItemEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:$playlistName")
        entries.forEach { entry ->
            val duration = entry.durationSeconds ?: -1
            val title = entry.title ?: entry.uri.substringAfterLast('/').substringAfterLast('\\')
            sb.appendLine("#EXTINF:$duration,$title")
            sb.appendLine(entry.uri)
        }
        return sb.toString()
    }
}
