package dev.vimivo.platform.android.playback

import android.content.ContentResolver
import android.net.Uri
import java.net.URI

/**
 * Turns a media URI into what libmpv opens: a path for file://, `fdclose://<fd>` for content:// (libmpv cannot read
 * content URIs; the descriptor comes from the ContentResolver and libmpv closes it), and plain http/rtsp URLs.
 * HTTPS needs TLS, which the Android media package does not have yet; those URIs stay with Media3.
 */
internal class LibmpvSources(private val openDescriptor: (String) -> Int) {
    constructor(resolver: ContentResolver) : this({ uri ->
        checkNotNull(resolver.openFileDescriptor(Uri.parse(uri), "r")) { "No file descriptor for $uri" }.detachFd()
    })

    fun resolve(uri: String): String = when (schemeOf(uri)) {
        "file" -> checkNotNull(URI(uri).path) { "file URI without a path: $uri" }
        "content" -> "fdclose://${openDescriptor(uri)}"
        "http", "rtsp" -> uri
        else -> throw IllegalArgumentException("libmpv cannot open this URI: $uri")
    }

    companion object {
        private val SUPPORTED = setOf("file", "content", "http", "rtsp")

        fun schemeOf(uri: String): String? =
            uri.substringBefore(':', missingDelimiterValue = "").lowercase().takeIf { it.isNotEmpty() && "/" !in it }

        fun isSupportedScheme(scheme: String?): Boolean = scheme?.lowercase() in SUPPORTED

        fun isSupported(uri: String): Boolean = isSupportedScheme(schemeOf(uri))
    }
}
