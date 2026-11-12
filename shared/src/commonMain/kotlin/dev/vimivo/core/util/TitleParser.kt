package dev.vimivo.core.util

/** A film or episode name read from a file name, without any network lookup. */
data class CleanTitle(val title: String, val year: Int? = null, val season: Int? = null, val episode: Int? = null) {
    /** "The Movie (2019)", "Show · S01E02", or just the cleaned title. */
    val display: String
        get() = when {
            season != null && episode != null -> "$title · S${pad(season)}E${pad(episode)}"
            year != null -> "$title ($year)"
            else -> title
        }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
}

/**
 * Turns "The.Great.Film.2019.1080p.BluRay.x264-GRP.mkv" into "The Great Film (2019)". Entirely offline: it only removes
 * the noise release groups add (resolution, source, codec tags, brackets) and reads a year or SxxEyy if one is there.
 * A name that looks like normal prose is returned as it was written.
 */
object TitleParser {
    private val extension = Regex("\\.[A-Za-z0-9]{2,4}$")
    private val brackets = Regex("[\\[({][^\\])}]*[\\])}]")
    private val episode = Regex("(?i)\\bS(\\d{1,2})[ ._-]?E(\\d{1,3})\\b")
    private val altEpisode = Regex("(?i)\\b(\\d{1,2})x(\\d{2,3})\\b")
    private val year = Regex("(?<![0-9])(19[2-9][0-9]|20[0-4][0-9])(?![0-9])")
    private val noise = Regex(
        "(?i)\\b(2160p|1440p|1080p|1080i|720p|576p|480p|4k|uhd|hdr10?|dolby ?vision|bluray|blu-ray|bdrip|brrip|webrip|web-dl|webdl|" +
            "hdrip|dvdrip|dvdscr|hdtv|remux|x264|x265|h ?264|h ?265|hevc|avc|xvid|divx|aac|ac3|eac3|dts|truehd|atmos|flac|10bit|8bit|" +
            "proper|repack|extended|unrated|remastered|imax|multi|dual[ -]?audio|subbed|dubbed|yify|yts|rarbg|ettv|eztv)\\b",
    )
    private val separators = Regex("[._]+")
    private val spaces = Regex("\\s+")

    private fun looksHandWritten(name: String): Boolean {
        val dotted = name.contains('.') || name.contains('_')
        return !dotted && !noise.containsMatchIn(name) && !episode.containsMatchIn(name)
    }

    fun clean(fileName: String): CleanTitle {
        var name = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (extension.containsMatchIn(name)) name = name.replace(extension, "")
        val raw = name
        // A name with real spaces and no release-group noise is already how the person wants it.
        if (looksHandWritten(raw)) return CleanTitle(raw.trim())
        val episodeMatch = episode.find(name) ?: altEpisode.find(name)
        name = name.replace(brackets, " ").replace(separators, " ")
        val cut = listOfNotNull(
            noise.find(name)?.range?.first,
            episodeMatch?.let { episode.find(name)?.range?.first ?: altEpisode.find(name)?.range?.first },
        ).minOrNull()
        val yearMatch = year.findAll(name).lastOrNull { cut == null || it.range.first < cut }
        val end = listOfNotNull(cut, yearMatch?.range?.first).minOrNull() ?: name.length
        val title = name.substring(0, end).replace(spaces, " ").trim(' ', '-', '.')
        if (title.isEmpty()) return CleanTitle(raw.replace(separators, " ").trim())
        return CleanTitle(
            title = title,
            year = yearMatch?.value?.toInt()?.takeIf { episodeMatch == null },
            season = episodeMatch?.groupValues?.get(1)?.toIntOrNull(),
            episode = episodeMatch?.groupValues?.get(2)?.toIntOrNull(),
        )
    }
}
