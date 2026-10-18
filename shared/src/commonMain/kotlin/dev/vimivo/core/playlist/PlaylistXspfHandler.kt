@file:Suppress("ArgumentListWrapping", "PropertyWrapping", "MaxLineLength", "MaximumLineLength", "Wrapping")

package dev.vimivo.core.playlist

/**
 * Minimal XSPF (XML Shareable Playlist Format, version 1) reader and writer.
 *
 * Only the playlist title and each track's `location`, `title`, and `duration` (milliseconds) are
 * read. DTDs and entity declarations are rejected rather than expanded, so untrusted playlists
 * cannot trigger entity-expansion or external-entity attacks.
 */
object PlaylistXspfHandler {
    private const val MILLIS_PER_SECOND = 1_000

    private val TRACK_REGEX = Regex("""<track\b[^>]*>(.*?)</track>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TRACK_LIST_REGEX = Regex("""<trackList\b[^>]*>(.*?)</trackList>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ENTITY_REGEX = Regex("""&(lt|gt|quot|apos|amp|#[xX][0-9a-fA-F]+|#[0-9]+);""")

    fun isXspf(fileName: String): Boolean = fileName.endsWith(".xspf", ignoreCase = true)

    fun parse(content: String, defaultName: String = "Imported Playlist"): ParsedPlaylist {
        require(!content.contains("<!DOCTYPE", ignoreCase = true) && !content.contains("<!ENTITY", ignoreCase = true)) {
            "XSPF playlists with DTD or entity declarations are not supported"
        }
        require(content.contains("<playlist", ignoreCase = true)) { "File is not an XSPF playlist" }
        val trackList = TRACK_LIST_REGEX.find(content)
        val header = trackList?.let { content.substring(0, it.range.first) } ?: content
        val name = element(header, "title")?.takeIf(String::isNotBlank) ?: defaultName
        val entries = TRACK_REGEX.findAll(trackList?.groupValues?.get(1).orEmpty()).mapNotNull { match ->
            val body = match.groupValues[1]
            val location = element(body, "location")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            PlaylistItemEntry(
                uri = location,
                title = element(body, "title")?.takeIf(String::isNotBlank),
                durationSeconds = element(body, "duration")?.toLongOrNull()?.takeIf { it >= 0 }?.let { (it / MILLIS_PER_SECOND).toInt() },
            )
        }.toList()
        return ParsedPlaylist(name, entries)
    }

    fun export(playlistName: String, entries: List<PlaylistItemEntry>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<playlist version="1" xmlns="http://xspf.org/ns/0/">""")
        appendLine("  <title>${escape(playlistName)}</title>")
        appendLine("  <trackList>")
        entries.forEach { entry ->
            appendLine("    <track>")
            appendLine("      <location>${escape(entry.uri)}</location>")
            entry.title?.takeIf(String::isNotBlank)?.let { appendLine("      <title>${escape(it)}</title>") }
            entry.durationSeconds?.takeIf { it >= 0 }?.let { appendLine("      <duration>${it.toLong() * MILLIS_PER_SECOND}</duration>") }
            appendLine("    </track>")
        }
        appendLine("  </trackList>")
        appendLine("</playlist>")
    }

    private fun element(xml: String, name: String): String? =
        Regex("""<$name\b[^>]*>(.*?)</$name>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?.let(::unwrapCdata)
            ?.trim()

    private fun unwrapCdata(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")) {
            trimmed.removePrefix("<![CDATA[").removeSuffix("]]>")
        } else {
            unescape(trimmed)
        }
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    /** Single pass, so "&amp;lt;" correctly decodes to the literal text "&lt;". */
    private fun unescape(value: String): String = ENTITY_REGEX.replace(value) { match ->
        when (val entity = match.groupValues[1]) {
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "amp" -> "&"
            else -> {
                val hex = entity.startsWith("#x", ignoreCase = true)
                entity.removePrefix("#").removePrefix("x").removePrefix("X")
                    .toIntOrNull(if (hex) 16 else 10)
                    ?.takeIf { it in 1..Char.MAX_VALUE.code }
                    ?.toChar()
                    ?.toString()
                    ?: match.value
            }
        }
    }
}
