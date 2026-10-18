package dev.vimivo.core.playlist

/**
 * PLS (Winamp/Shoutcast) playlist parser. Entries are `FileN=`, with optional `TitleN=` and `LengthN=`
 * (seconds, -1 for unknown); N orders the entries even when the keys appear out of order.
 */
object PlaylistPlsHandler {
    private val KEY = Regex("""^(File|Title|Length)(\d+)$""", RegexOption.IGNORE_CASE)

    fun isPls(fileName: String): Boolean = fileName.endsWith(".pls", ignoreCase = true)

    fun parse(content: String, defaultName: String = "Imported Playlist"): ParsedPlaylist {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        val lengths = mutableMapOf<Int, Int>()
        content.lineSequence().map(String::trim).filter { '=' in it }.forEach { line ->
            val match = KEY.matchEntire(line.substringBefore('=').trim()) ?: return@forEach
            val index = match.groupValues[2].toIntOrNull() ?: return@forEach
            val value = line.substringAfter('=').trim()
            when (match.groupValues[1].lowercase()) {
                "file" -> if (value.isNotEmpty()) files[index] = value
                "title" -> if (value.isNotEmpty()) titles[index] = value
                "length" -> value.toIntOrNull()?.takeIf { it >= 0 }?.let { lengths[index] = it }
            }
        }
        val entries = files.map { (index, uri) -> PlaylistItemEntry(uri, titles[index], lengths[index]) }
        return ParsedPlaylist(defaultName, entries)
    }
}

/** Picks the parser for a playlist file by extension; null for formats that are not playlists we parse. */
object PlaylistFormats {
    val PARSED_EXTENSIONS = setOf("m3u", "m3u8", "pls", "xspf")

    fun parse(fileName: String, content: String): ParsedPlaylist? {
        val name = fileName.substringBeforeLast('.')
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "m3u", "m3u8" -> PlaylistM3uHandler.parseM3u(content, name)
            "pls" -> PlaylistPlsHandler.parse(content, name)
            "xspf" -> PlaylistXspfHandler.parse(content, name)
            else -> null
        }
    }
}
