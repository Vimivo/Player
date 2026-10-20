package dev.vimivo.desktop.platform

import java.io.File

/** What Vimivo does with a file type: play it as video or audio, load it as a playlist, or attach subtitles. */
enum class MediaFamily { VIDEO, AUDIO, PLAYLIST, SUBTITLE }

data class MediaType(
    val extension: String,
    val mimeType: String,
    val family: MediaFamily,
    val description: String,
    /** The macOS system UTI, or null when Vimivo declares an imported type for it. */
    val systemUti: String?,
)

/**
 * The file types Vimivo opens. `media-types.tsv` is the single source: Gradle reads the same file to
 * register OS file associations, so the packaged app and its runtime behaviour cannot drift apart.
 */
object DesktopMediaTypes {
    const val RESOURCE = "/dev/vimivo/desktop/media-types.tsv"

    val all: List<MediaType> by lazy {
        val text = checkNotNull(DesktopMediaTypes::class.java.getResourceAsStream(RESOURCE)) { "$RESOURCE missing" }
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        parse(text)
    }

    private val byExtension: Map<String, MediaType> by lazy { all.associateBy(MediaType::extension) }

    fun parse(text: String): List<MediaType> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val columns = line.split('\t')
            require(columns.size >= MIN_COLUMNS) { "Malformed media type line: $line" }
            MediaType(
                extension = columns[0].lowercase(),
                mimeType = columns[1],
                family = MediaFamily.valueOf(columns[2].uppercase()),
                description = columns[3],
                systemUti = columns.getOrNull(UTI_COLUMN)?.takeIf(String::isNotBlank),
            )
        }
        .toList()

    fun familyOf(file: File): MediaFamily? = byExtension[file.extension.lowercase()]?.family

    fun extensions(family: MediaFamily): Set<String> =
        all.filter { it.family == family }.mapTo(linkedSetOf(), MediaType::extension)

    private const val MIN_COLUMNS = 4
    private const val UTI_COLUMN = 4
}
