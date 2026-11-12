package dev.vimivo.cast

/**
 * DLNA / UPnP AV protocol pieces that need no network: building discovery requests and SOAP calls, and reading
 * the replies. Sockets live in the platform code. A "renderer" is a TV or speaker that can play a URL it is handed.
 */
data class DlnaRenderer(
    val id: String,
    val name: String,
    /** Absolute URL that SOAP calls to the AVTransport service are posted to. */
    val avTransportControlUrl: String,
    val manufacturer: String = "",
)

/** One answer to an SSDP search. */
data class SsdpResponse(val location: String, val usn: String, val searchTarget: String)

object Ssdp {
    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 1900
    const val MEDIA_RENDERER_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

    fun searchRequest(target: String = MEDIA_RENDERER_TARGET, maxWaitSeconds: Int = 2): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $MULTICAST_ADDRESS:$PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: $maxWaitSeconds\r\n" +
            "ST: $target\r\n\r\n"

    /** Reads an SSDP answer; null if it is not a successful reply or lacks a location. */
    fun parseResponse(text: String): SsdpResponse? {
        val lines = text.split("\r\n", "\n")
        if (lines.firstOrNull()?.contains(" 200 ") != true) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()
        val location = headers["location"]?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        return SsdpResponse(location, headers["usn"].orEmpty(), headers["st"].orEmpty())
    }
}

object DlnaDescription {
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

    /**
     * Reads a device description document. Returns null if the device has no AVTransport service, so it cannot be
     * handed a URL to play. [descriptionUrl] resolves a relative `controlURL`.
     */
    fun parse(xml: String, descriptionUrl: String): DlnaRenderer? {
        val name = firstTag(xml, "friendlyName") ?: return null
        val serviceBlock = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1] }
            .firstOrNull { firstTag(it, "serviceType")?.trim() == AV_TRANSPORT }
            ?: return null
        val control = firstTag(serviceBlock, "controlURL") ?: return null
        return DlnaRenderer(
            id = firstTag(xml, "UDN") ?: descriptionUrl,
            name = unescape(name),
            avTransportControlUrl = resolve(descriptionUrl, control.trim()),
            manufacturer = firstTag(xml, "manufacturer")?.let(::unescape).orEmpty(),
        )
    }

    internal fun resolve(base: String, reference: String): String {
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return reference
        val hostEnd = base.indexOf('/', schemeEnd + 3).let { if (it < 0) base.length else it }
        val origin = base.substring(0, hostEnd)
        if (reference.startsWith("/")) return origin + reference
        val directory = base.substring(0, base.lastIndexOf('/').coerceAtLeast(hostEnd))
        return "$directory/$reference"
    }

    private fun firstTag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/** SOAP requests for the AVTransport service. Each returns the headers' SOAPACTION value and the XML body. */
class SoapCall(val action: String, val body: String)

object AvTransport {
    private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"

    fun setUri(uri: String, title: String, mimeType: String): SoapCall = call(
        "SetAVTransportURI",
        "<CurrentURI>${escape(uri)}</CurrentURI><CurrentURIMetaData>${escape(didl(title, uri, mimeType))}</CurrentURIMetaData>",
    )

    fun play(): SoapCall = call("Play", "<Speed>1</Speed>")
    fun pause(): SoapCall = call("Pause", "")
    fun stop(): SoapCall = call("Stop", "")
    fun seek(positionMillis: Long): SoapCall =
        call("Seek", "<Unit>REL_TIME</Unit><Target>${formatTime(positionMillis)}</Target>")

    /** `H:MM:SS` as UPnP wants it. */
    fun formatTime(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return "${total / 3600}:${(total % 3600 / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
    }

    /** DIDL-Lite metadata that tells the renderer what it is being given. */
    fun didl(title: String, uri: String, mimeType: String): String {
        val kind = if (mimeType.startsWith("audio/")) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"0\" restricted=\"1\"><dc:title>${escape(title)}</dc:title>" +
            "<upnp:class>$kind</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${escape(mimeType)}:*\">${escape(uri)}</res></item></DIDL-Lite>"
    }

    private fun call(name: String, args: String): SoapCall = SoapCall(
        action = "\"$SERVICE#$name\"",
        body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$name xmlns:u=\"$SERVICE\"><InstanceID>0</InstanceID>$args</u:$name></s:Body></s:Envelope>",
    )
}

internal fun escape(text: String): String = buildString(text.length + 8) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

internal fun unescape(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

/** The media type a renderer needs in its metadata, from the file name. */
object CastMime {
    private val byExtension = mapOf(
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "avi" to "video/x-msvideo", "ts" to "video/mp2t", "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg", "3gp" to "video/3gpp", "wmv" to "video/x-ms-wmv",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac",
        "wav" to "audio/wav", "ogg" to "audio/ogg", "opus" to "audio/opus", "wma" to "audio/x-ms-wma",
    )

    fun forName(name: String): String =
        byExtension[name.substringAfterLast('.', "").lowercase()] ?: "video/mp4"
}
