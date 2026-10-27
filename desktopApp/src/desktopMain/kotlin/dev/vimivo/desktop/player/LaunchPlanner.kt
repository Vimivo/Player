package dev.vimivo.desktop.player

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.playlist.PlaylistFormats
import dev.vimivo.desktop.platform.DesktopMediaTypes
import dev.vimivo.desktop.platform.MediaFamily
import dev.vimivo.playback.SiblingQueues
import java.io.File
import java.net.URI

/** Which player view a launch should show. */
internal enum class PlayerPresentation { LIBRARY, VIDEO, AUDIO }

/** What to play for a set of opened files: the queue, where to start, and which view to show. */
internal data class LaunchPlan(val items: List<MediaItem>, val startIndex: Int, val presentation: PlayerPresentation)

/**
 * Turns files handed to Vimivo into a playback queue. A single file brings its folder siblings of the same
 * kind as "Up Next" (naturally sorted, so "ep2" precedes "ep10"); several files keep the user's order;
 * playlists expand to their entries; `.cue` sheets go to libmpv, which plays their tracks as chapters.
 */
internal object LaunchPlanner {
    private val REMOTE_SCHEMES = setOf("http", "https", "rtsp", "rtsps")

    fun plan(files: List<File>, includeFolderSiblings: Boolean): LaunchPlan? {
        val opened = files.filter { DesktopMediaTypes.familyOf(it) != MediaFamily.SUBTITLE }
        val single = opened.singleOrNull()
        val playable = DesktopMediaTypes.familyOf(single ?: File("")) in setOf(MediaFamily.VIDEO, MediaFamily.AUDIO)
        val (items, start) = if (single != null && includeFolderSiblings && playable) {
            withSiblings(single)
        } else {
            opened.flatMap(::expand) to 0
        }
        if (items.isEmpty()) return null
        val audio = items[start].kind == MediaKind.AUDIO
        val presentation = if (audio) PlayerPresentation.AUDIO else PlayerPresentation.VIDEO
        return LaunchPlan(items, start, presentation)
    }

    /**
     * The window layout for files handed over at launch, from their extensions alone (no I/O), so the player
     * window can show in the first frame while the full [plan] (folder listing, resume position) is prepared.
     */
    fun initialPresentation(files: List<File>): PlayerPresentation? {
        val first = files.firstOrNull()
        if (first != null && first.isDirectory) return PlayerPresentation.VIDEO
        return when (files.map(DesktopMediaTypes::familyOf).firstOrNull { it != MediaFamily.SUBTITLE }) {
            MediaFamily.AUDIO, MediaFamily.PLAYLIST -> PlayerPresentation.AUDIO
            MediaFamily.VIDEO -> PlayerPresentation.VIDEO
            else -> null
        }
    }

    /** The opened file plus every playable file of the same kind beside it, in natural order. */
    fun withSiblings(file: File): Pair<List<MediaItem>, Int> {
        val family = DesktopMediaTypes.familyOf(file)
        val siblings = file.absoluteFile.parentFile?.listFiles()
            ?.filter { it.isFile && !it.isHidden && DesktopMediaTypes.familyOf(it) == family }
            .orEmpty()
        val sameFile = { a: File, b: File -> a.absoluteFile == b.absoluteFile }
        val queue = SiblingQueues.build(file, siblings, name = File::getName, sameFile = sameFile)
        return queue.items.map(::mediaItemFor) to queue.startIndex
    }

    private fun expand(file: File): List<MediaItem> = when {
        file.isDirectory -> {
            if (dev.vimivo.desktop.platform.DesktopDiscMedia.isDiscFolder(file)) {
                listOfNotNull(dev.vimivo.desktop.platform.DesktopDiscMedia.resolveDiscMedia(file))
            } else {
                file.listFiles()
                    ?.filter { it.isFile && !it.isHidden && DesktopMediaTypes.familyOf(it) in setOf(MediaFamily.VIDEO, MediaFamily.AUDIO) }
                    ?.sortedWith(compareBy(dev.vimivo.core.util.NaturalOrder, File::getName))
                    ?.map(::mediaItemFor)
                    .orEmpty()
            }
        }
        !file.isFile -> emptyList()
        file.extension.lowercase() in PlaylistFormats.PARSED_EXTENSIONS -> playlistEntries(file)
        else -> listOf(mediaItemFor(file))
    }

    private fun playlistEntries(playlist: File): List<MediaItem> {
        val parsed = runCatching { PlaylistFormats.parse(playlist.name, playlist.readText(Charsets.UTF_8)) }.getOrNull()
        return parsed?.entries.orEmpty().mapNotNull { entry ->
            val scheme = entry.uri.substringBefore("://", "").lowercase()
            when {
                scheme in REMOTE_SCHEMES ->
                    MediaItem(MediaId(entry.uri), entry.uri, entry.title ?: entry.uri, MediaKind.STREAM)
                else -> resolveLocal(playlist, entry.uri)?.takeIf(File::isFile)?.let { local ->
                    mediaItemFor(local).let { item -> entry.title?.let { item.copy(title = it) } ?: item }
                }
            }
        }
    }

    /** Playlist entries may be file URIs, absolute paths, or paths relative to the playlist's folder. */
    private fun resolveLocal(playlist: File, entry: String): File? = runCatching {
        when {
            entry.startsWith("file:", ignoreCase = true) -> File(URI(entry))
            File(entry).isAbsolute -> File(entry)
            else -> File(playlist.absoluteFile.parentFile, entry.replace('\\', File.separatorChar))
        }
    }.getOrNull()

    fun mediaItemFor(file: File): MediaItem {
        val uri = file.toPath().toUri().toString()
        val kind = when (DesktopMediaTypes.familyOf(file)) {
            MediaFamily.AUDIO, MediaFamily.PLAYLIST -> MediaKind.AUDIO
            else -> MediaKind.VIDEO
        }
        return MediaItem(
            id = MediaId(uri),
            uri = uri,
            title = file.nameWithoutExtension,
            kind = kind,
            fileName = file.name,
        )
    }
}
