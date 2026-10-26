package dev.vimivo.core.lyrics

/**
 * Finds the lyrics file that sits beside a media file: "Song.lrc" for "Song.mp3", or a language variant such as
 * "Song.en.lrc". Each platform lists the folder; this decides which of the names is the one, the same way everywhere.
 */
object LyricsSidecar {
    private const val EXTENSION = "lrc"

    /**
     * The name in [candidateNames] to use for [mediaFileName], or null when none fits. An exact stem match beats a
     * language variant; among variants the alphabetically first wins, so the choice never changes between runs.
     */
    fun best(mediaFileName: String, candidateNames: Collection<String>): String? {
        val stem = mediaFileName.substringBeforeLast('.', mediaFileName)
        val lyrics = candidateNames.filter { it.substringAfterLast('.', "").equals(EXTENSION, ignoreCase = true) }
        fun lyricStem(name: String) = name.substringBeforeLast('.')
        return lyrics.filter { lyricStem(it).equals(stem, ignoreCase = true) }.minOrNull()
            ?: lyrics.filter { lyricStem(it).startsWith("$stem.", ignoreCase = true) }.minOrNull()
    }
}
