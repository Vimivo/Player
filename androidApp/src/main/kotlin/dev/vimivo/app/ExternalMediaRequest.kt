package dev.vimivo.app

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource

/**
 * A file another app asked Vimivo to open ("Open with", share targets, file managers). Only local
 * `content:` and `file:` URIs are accepted; network links go through the explicit Open Stream flow.
 */
data class ExternalMediaRequest(val media: MediaItem) {
    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        private val LOCAL_SCHEMES = setOf("content", "file")

        fun parse(action: String?, uri: String?, mimeType: String?, displayName: String?): ExternalMediaRequest? {
            val type = mimeType?.lowercase()
            val kind = when {
                type == null -> null
                type.startsWith("video/") -> MediaKind.VIDEO
                type.startsWith("audio/") -> MediaKind.AUDIO
                else -> null
            }
            val isLocalView = action == ACTION_VIEW && uri?.substringBefore(':')?.lowercase() in LOCAL_SCHEMES
            if (!isLocalView || uri.isNullOrBlank() || kind == null) return null
            val title = displayName?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
                ?: uri.substringAfterLast('/').substringBeforeLast('.').ifBlank { uri }
            return ExternalMediaRequest(
                MediaItem(
                    id = MediaId("external:$uri"),
                    uri = uri,
                    title = title,
                    kind = kind,
                    mimeType = type,
                    fileName = displayName,
                    source = MediaSource.SAF_FILE,
                ),
            )
        }
    }
}
