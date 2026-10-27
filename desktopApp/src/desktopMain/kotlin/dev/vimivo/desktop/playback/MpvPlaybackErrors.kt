@file:Suppress("CyclomaticComplexMethod", "MaxLineLength", "MaximumLineLength")

package dev.vimivo.desktop.playback

import dev.vimivo.core.model.MediaItem
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackError
import dev.vimivo.playback.PlaybackErrorCode
import java.io.File
import java.net.URI

/** Maps libmpv failures to the shared [PlaybackError] codes the UI and the queue understand. */
internal object MpvPlaybackErrors {
    /** A file or stream that libmpv never managed to open: says whether it is missing, unreadable, or unplayable. */
    fun loadFailure(media: MediaItem?): PlaybackError {
        val localFile = media?.uri?.takeIf { it.startsWith("file:") }?.let { runCatching { File(URI(it)) }.getOrNull() }
        val code = when {
            localFile != null && !localFile.exists() -> PlaybackErrorCode.SOURCE_NOT_FOUND
            localFile != null && !localFile.canRead() -> PlaybackErrorCode.ACCESS_DENIED
            localFile == null -> PlaybackErrorCode.NETWORK
            else -> PlaybackErrorCode.UNSUPPORTED_CONTAINER
        }
        val message = when (code) {
            PlaybackErrorCode.SOURCE_NOT_FOUND -> "The file no longer exists"
            PlaybackErrorCode.ACCESS_DENIED -> "Vimivo does not have permission to read this file"
            PlaybackErrorCode.NETWORK -> "The stream could not be opened"
            else -> "This file could not be opened; it may be damaged or in an unsupported format"
        }
        return PlaybackError(code, message, engine = EngineId.MPV)
    }

    /**
     * A file libmpv opened but could not play at all, from its fatal message ("Error opening/initializing the selected
     * video_out", "No video or audio streams selected"): the user sees a reason instead of a silent standstill.
     */
    fun playbackFailure(fatalText: String): PlaybackError {
        val lower = fatalText.lowercase()
        return when {
            "video_out" in lower || "--vo" in lower -> PlaybackError(
                PlaybackErrorCode.ENGINE_INITIALIZATION,
                "The video output could not be started, so this file cannot be shown",
                engine = EngineId.MPV,
            )
            "no video or audio streams" in lower -> PlaybackError(
                PlaybackErrorCode.UNSUPPORTED_CODEC,
                "Nothing in this file could be decoded; its codec may be unsupported or the file damaged",
                engine = EngineId.MPV,
            )
            else -> PlaybackError(PlaybackErrorCode.DECODING_FAILED, fatalText, engine = EngineId.MPV)
        }
    }

    /** An exception from opening media, classified by type and libmpv's error text. */
    fun fromException(error: Throwable): PlaybackError {
        val message = error.message.orEmpty()
        val lower = message.lowercase()
        val code = when {
            error is java.io.FileNotFoundException || "not found" in lower || "no such file" in lower -> PlaybackErrorCode.SOURCE_NOT_FOUND
            error is java.lang.SecurityException || "permission denied" in lower || "access denied" in lower -> PlaybackErrorCode.ACCESS_DENIED
            error is java.net.SocketException || error is java.net.UnknownHostException || "timeout" in lower || "network" in lower || "connection" in lower -> PlaybackErrorCode.NETWORK
            "unsupported" in lower || "codec" in lower -> PlaybackErrorCode.UNSUPPORTED_CODEC
            "demux" in lower || "container" in lower || "corrupt" in lower -> PlaybackErrorCode.UNSUPPORTED_CONTAINER
            "decode" in lower || "decoder" in lower -> PlaybackErrorCode.DECODING_FAILED
            else -> PlaybackErrorCode.ENGINE_INITIALIZATION
        }
        return PlaybackError(code, message.ifBlank { "libmpv playback failed" }, engine = EngineId.MPV, cause = error)
    }
}
