@file:Suppress(
    "ArgumentListWrapping", "ComplexCondition", "CyclomaticComplexMethod",
    "MagicNumber", "MaximumLineLength", "MaxLineLength", "ReturnCount",
)

package dev.vimivo.core.streaming

import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import dev.vimivo.core.model.MediaSource
import dev.vimivo.core.util.SafeLogger

object StreamValidator {

    private const val MAX_URL_LENGTH = 4096

    private val WEBPAGE_DOMAINS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be",
        "vimeo.com", "www.vimeo.com",
        "tiktok.com", "www.tiktok.com",
        "facebook.com", "www.facebook.com", "fb.watch",
        "instagram.com", "www.instagram.com",
        "twitter.com", "x.com",
        "dailymotion.com", "www.dailymotion.com",
        "reddit.com", "www.reddit.com",
    )

    private val WEBPAGE_EXTENSIONS = setOf(
        "html", "htm", "php", "asp", "aspx", "jsp",
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "wma",
    )

    fun validate(rawUrl: String): StreamValidationResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "The URL cannot be blank.",
            )
        }

        if (trimmed.length > MAX_URL_LENGTH) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "The provided URL exceeds the maximum supported length of $MAX_URL_LENGTH characters.",
            )
        }

        if (trimmed.any { it.isISOControl() }) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "The URL contains forbidden control characters.",
            )
        }

        val schemeCandidate = trimmed.substringBefore("://").substringBefore(":").lowercase()
        val hasColon = trimmed.contains(':')
        if (!hasColon || schemeCandidate == trimmed || schemeCandidate.contains('/') || schemeCandidate.contains('.')) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "Invalid URL format. Please include protocol (e.g., https:// or rtsp://).",
            )
        }

        val scheme = schemeCandidate
        if (scheme in setOf("file", "content", "javascript", "data", "about", "ftp", "sftp")) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.UNSUPPORTED_PROTOCOL,
                message = "Protocol '$scheme' cannot be opened as a network media stream.",
            )
        }

        if (scheme !in setOf("http", "https", "rtsp", "rtsps")) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.UNSUPPORTED_PROTOCOL,
                message = "Protocol '$scheme' is not supported. Supported protocols: http, https, rtsp, rtsps.",
            )
        }

        val colonIndex = trimmed.indexOf("://")
        if (colonIndex <= 0) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "Invalid URL format. Expected '://' after protocol scheme.",
            )
        }

        val afterScheme = trimmed.substring(colonIndex + 3)
        val hostPart = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (hostPart.isBlank()) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "URL must contain a valid server host address.",
            )
        }

        if (hostPart.contains('@')) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.INVALID_URL,
                message = "URLs containing embedded credentials are not permitted for security reasons.",
            )
        }

        val hostWithoutPort = hostPart.substringBefore(':').lowercase()

        // Check for known webpage/social media domains
        if (hostWithoutPort in WEBPAGE_DOMAINS) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM,
                message = "This link points to a $hostWithoutPort webpage, not a direct playable stream. " +
                    "Vimivo requires a direct media URL (e.g. .mp4, .m3u8, .mpd, .mp3, or RTSP).",
            )
        }

        val pathWithQuery = "/" + afterScheme.substringAfter('/', "")
        val path = pathWithQuery.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('.', "").lowercase()

        if (extension in WEBPAGE_EXTENSIONS) {
            return StreamValidationResult.Invalid(
                reason = StreamFailureReason.WEBPAGE_NOT_DIRECT_STREAM,
                message = "The URL references an HTML/webpage file (.$extension), not a media stream.",
            )
        }

        val normalizedUrl = trimmed.substringBefore('#')
        val protocol = detectProtocol(scheme, path, hostPart)
        val isLive = detectIsLive(protocol, path)
        val type = if (isLive) StreamType.LIVE else StreamType.VOD
        val displayTitle = generateDisplayTitle(path, hostPart, protocol)

        return StreamValidationResult.Valid(
            protocol = protocol,
            type = type,
            normalizedUrl = normalizedUrl,
            isLive = isLive,
            displayTitle = displayTitle,
        )
    }

    private fun detectProtocol(scheme: String, path: String, hostPart: String): StreamProtocol {
        if (scheme == "rtsp" || scheme == "rtsps") return StreamProtocol.RTSP

        val lowerPath = path.lowercase()
        return when {
            lowerPath.endsWith(".m3u8") || lowerPath.contains(".m3u8") -> StreamProtocol.HLS
            lowerPath.endsWith(".mpd") || lowerPath.contains(".mpd") -> StreamProtocol.DASH
            isInternetRadio(path, hostPart) -> StreamProtocol.INTERNET_RADIO
            scheme == "https" -> StreamProtocol.HTTPS_PROGRESSIVE
            scheme == "http" -> StreamProtocol.HTTP_PROGRESSIVE
            else -> StreamProtocol.UNKNOWN
        }
    }

    private fun isInternetRadio(path: String, hostPart: String): Boolean {
        val lower = path.lowercase()
        val port = hostPart.substringAfter(':', "")
        val isRadioPort = port == "8000" || port == "8080" || port == "8002" || port == "8004"
        val isRadioPath = lower == "/stream" || lower == "/live" || lower.endsWith(";stream.mp3") ||
            lower.contains("icecast") || lower.contains("shoutcast")

        return isRadioPort || (isRadioPath && lower.substringAfterLast('.', "") in AUDIO_EXTENSIONS)
    }

    private fun detectIsLive(protocol: StreamProtocol, path: String): Boolean {
        if (protocol == StreamProtocol.RTSP || protocol == StreamProtocol.INTERNET_RADIO) return true
        val lower = path.lowercase()
        return lower.contains("/live") || lower.contains("live.m3u8") || lower.contains("live.mpd")
    }

    private fun generateDisplayTitle(path: String, hostPart: String, protocol: StreamProtocol): String {
        val lastSegment = path.trimEnd('/').substringAfterLast('/')
        val cleanedSegment = lastSegment.substringBefore('?').substringBefore('#')

        if (cleanedSegment.isNotBlank() && cleanedSegment.length >= 3) {
            val titleNoExt = cleanedSegment.substringBeforeLast('.')
            if (titleNoExt.isNotBlank()) {
                return SafeLogger.sanitize(titleNoExt.replace('_', ' ').replace('-', ' '))
            }
        }

        val hostClean = hostPart.substringBefore(':')
        return when (protocol) {
            StreamProtocol.HLS -> "HLS Live Stream ($hostClean)"
            StreamProtocol.DASH -> "DASH Stream ($hostClean)"
            StreamProtocol.RTSP -> "RTSP Stream ($hostClean)"
            StreamProtocol.INTERNET_RADIO -> "Internet Radio ($hostClean)"
            else -> "Network Stream ($hostClean)"
        }
    }

    fun toMediaItem(valid: StreamValidationResult.Valid): MediaItem {
        return MediaItem(
            id = MediaId(valid.normalizedUrl),
            uri = valid.normalizedUrl,
            title = valid.displayTitle,
            kind = MediaKind.STREAM,
            source = MediaSource.NETWORK,
            durationMillis = if (valid.isLive) null else 0L,
        )
    }

    fun toStreamSource(valid: StreamValidationResult.Valid): StreamSource = when (valid.protocol) {
        StreamProtocol.HLS -> StreamSource.HlsStream(valid.normalizedUrl, valid.isLive)
        StreamProtocol.DASH -> StreamSource.DashStream(valid.normalizedUrl, valid.isLive)
        StreamProtocol.RTSP -> StreamSource.RtspStream(valid.normalizedUrl)
        StreamProtocol.INTERNET_RADIO -> StreamSource.InternetRadio(valid.normalizedUrl, valid.displayTitle)
        StreamProtocol.HTTP_PROGRESSIVE, StreamProtocol.HTTPS_PROGRESSIVE ->
            StreamSource.DirectHttpMedia(valid.normalizedUrl, valid.protocol)
        StreamProtocol.UNKNOWN -> StreamSource.DirectHttpMedia(valid.normalizedUrl, valid.protocol)
    }

    fun classifyHttpStatus(statusCode: Int): StreamFailureReason = when (statusCode) {
        401, 403 -> StreamFailureReason.HTTP_FORBIDDEN
        404 -> StreamFailureReason.NOT_FOUND
        408 -> StreamFailureReason.TIMEOUT
        410 -> StreamFailureReason.EXPIRED_URL
        429 -> StreamFailureReason.CONNECTION_FAILED
        in 500..599 -> StreamFailureReason.CONNECTION_FAILED
        else -> StreamFailureReason.UNKNOWN
    }

    fun validateHeaders(headers: Map<String, String>): Result<Map<String, String>> = runCatching {
        headers.forEach { (name, value) ->
            require(name.isNotBlank()) { "Header name cannot be blank." }
            require(!name.any { it == '\r' || it == '\n' || it == ':' }) {
                "Header name contains illegal characters: $name"
            }
            require(!value.any { it == '\r' || it == '\n' }) {
                "Header value contains illegal newline characters: $name"
            }
        }
        headers
    }
}
