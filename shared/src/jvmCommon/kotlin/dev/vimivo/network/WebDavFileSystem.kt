package dev.vimivo.network

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder

/** A WebDAV folder (NAS, Nextcloud, ...). https is required unless the server is on the local network. */
class WebDavFileSystem private constructor(
    private val base: URI,
    private val user: String,
    private val password: CharArray,
) : RemoteFileSystem {
    private val rootPath = base.rawPath.trimEnd('/').ifEmpty { "" }

    override fun list(path: String): List<RemoteEntry> {
        val url = urlFor(path)
        MiniHttp.request(
            "PROPFIND", url,
            mapOf("Depth" to "1", "Content-Type" to "text/xml; charset=utf-8"),
            PROPFIND_BODY, user, password,
        ).use { response ->
            when (response.status) {
                207 -> {
                    val xml = response.body.readNBytes(MAX_LISTING_BYTES).toString(Charsets.UTF_8)
                    return WebDavMultistatus.parse(xml, URI(url).path, URI(base.toString()).path.trimEnd('/'))
                }
                401, 403 -> throw ShareException("The server denied access. Check the user name and password")
                404 -> throw ShareException("That folder does not exist")
                else -> throw ShareException("The server answered HTTP ${response.status}")
            }
        }
    }

    override fun length(path: String): Long {
        MiniHttp.request("HEAD", urlFor(path), user = user, password = password).use { response ->
            if (response.status !in 200..299) throw IOException("The server answered HTTP ${response.status}")
            return response.headers["content-length"]?.toLongOrNull() ?: throw IOException("The server did not report the size")
        }
    }

    override fun open(path: String, offset: Long): InputStream {
        val response = MiniHttp.request(
            "GET", urlFor(path),
            if (offset > 0) mapOf("Range" to "bytes=$offset-") else emptyMap(),
            null, user, password,
        )
        if (response.status != 200 && response.status != 206) {
            response.close()
            throw IOException("The server answered HTTP ${response.status}")
        }
        if (offset > 0 && response.status == 200) {
            // The server ignored the range and is sending the whole file; skip to where we were asked to start.
            var toSkip = offset
            while (toSkip > 0) {
                val skipped = response.body.skip(toSkip)
                if (skipped <= 0) break
                toSkip -= skipped
            }
        }
        return object : InputStream() {
            override fun read(): Int = response.body.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = response.body.read(b, off, len)
            override fun close() = response.close()
        }
    }

    override fun close() = password.fill('\u0000')

    private fun urlFor(path: String): String {
        val encoded = path.split('/').filter { it.isNotEmpty() }.joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20")
        }
        val root = base.toString().trimEnd('/')
        return if (encoded.isEmpty()) "$root/" else "$root/$encoded"
    }

    companion object {
        private const val MAX_LISTING_BYTES = 8 * 1024 * 1024
        private val PROPFIND_BODY =
            """<?xml version="1.0"?><propfind xmlns="DAV:"><prop><resourcetype/><getcontentlength/></prop></propfind>""".toByteArray()

        fun connect(url: String, user: String, password: CharArray): Result<WebDavFileSystem> = try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            when {
                uri.host.isNullOrBlank() || scheme !in setOf("http", "https") -> Result.failure(ShareException("Enter an address like https://nas.local/dav"))
                scheme == "http" && !RemotePaths.isPrivateHost(uri.host) -> Result.failure(ShareException("Use https for servers outside your home network"))
                else -> {
                    val fs = WebDavFileSystem(uri, user, password)
                    // Listing the root proves the address and the login before the user sees a browser.
                    runCatching { fs.list("") }.fold(
                        onSuccess = { Result.success(fs) },
                        onFailure = { error ->
                            fs.close()
                            Result.failure(if (error is ShareException) error else ShareException("Could not reach the server: ${error.message}", error))
                        },
                    )
                }
            }
        } catch (e: java.net.URISyntaxException) {
            Result.failure(ShareException("That address is not valid", e))
        }
    }
}
