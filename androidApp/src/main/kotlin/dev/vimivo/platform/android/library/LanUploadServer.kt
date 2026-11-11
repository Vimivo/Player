package dev.vimivo.platform.android.library

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Where [LanUploadServer] stores a received file. Returns the saved display name, or null if it could not be saved. */
fun interface UploadSink {
    fun save(name: String, mimeType: String, length: Long, body: InputStream): String?
}

/**
 * Receives audio and video files from a browser on the same local network.
 *
 * Reachable only with the per-session token in [url], bound to one private IPv4 address, and limited to
 * known media types and [maxBytes]. Android has no `com.sun.net.httpserver`, so this speaks the small subset of
 * HTTP/1.1 it needs: one request per connection, `Content-Length` bodies, no keep-alive.
 */
class LanUploadServer(
    private val sink: UploadSink,
    private val bindAddress: InetAddress? = privateIpv4(),
    private val preferredPort: Int = 8080,
    private val maxBytes: Long = MAX_UPLOAD_BYTES,
    private val logWarning: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val token: String = newToken()
    private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null

    @Volatile
    var url: String? = null
        private set

    @Volatile
    var startError: String? = null
        private set

    @Synchronized
    fun start() {
        if (serverSocket != null) return
        val address = bindAddress ?: run {
            startError = "No local network connection found. Connect to Wi-Fi and reopen this screen."
            return
        }
        val socket = bind(address, preferredPort) ?: bind(address, 0)
        if (socket == null) {
            startError = "Could not open a local port for sharing."
            return
        }
        serverSocket = socket
        url = "http://${address.hostAddress}:${socket.localPort}/?t=$token"
        val executor = Executors.newFixedThreadPool(MAX_THREADS) { r -> Thread(r, "vimivo-lan-upload").apply { isDaemon = true } }
        pool = executor
        executor.execute { acceptLoop(socket, executor) }
    }

    @Synchronized
    fun stop() {
        url = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun bind(address: InetAddress, port: Int): ServerSocket? =
        try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(address, port))
            }
        } catch (_: java.net.BindException) {
            null
        }

    private fun acceptLoop(socket: ServerSocket, executor: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                return // closed by stop()
            }
            try {
                executor.execute { client.use(::serve) }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                client.close()
            }
        }
    }

    private fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MILLIS
        val out = client.getOutputStream()
        try {
            val request = HttpRequest.read(client.getInputStream())
            if (request == null) {
                respond(out, 400, "text/plain; charset=utf-8", "Bad request")
                return
            }
            if (!tokenMatches(token, request.headers["x-vimivo-token"] ?: request.query["t"])) {
                respond(out, 403, "text/plain; charset=utf-8", "Forbidden")
                return
            }
            when {
                request.method == "GET" && request.path == "/" -> respond(out, 200, "text/html; charset=utf-8", UPLOAD_HTML)
                request.method == "POST" && request.path == "/upload" -> upload(request, client.getInputStream(), out)
                else -> respond(out, 404, "text/plain; charset=utf-8", "Not found")
            }
        } catch (_: SocketTimeoutException) {
            // Client stalled; dropping the connection is the right outcome.
        } catch (e: IOException) {
            logWarning("Upload connection failed", e)
        }
    }

    private fun upload(request: HttpRequest, body: InputStream, out: OutputStream) {
        val name = sanitizeFileName(request.query["name"].orEmpty())
        val mime = name?.let(::mimeTypeFor)
        val length = request.headers["content-length"]?.toLongOrNull()
        when {
            name == null || mime == null -> respond(out, 415, "text/plain; charset=utf-8", "Unsupported file type")
            length == null || length < 0 -> respond(out, 411, "text/plain; charset=utf-8", "Length required")
            length > maxBytes -> respond(out, 413, "text/plain; charset=utf-8", "File too large")
            else -> {
                val saved = sink.save(name, mime, length, LimitedInput(body, length))
                if (saved == null) respond(out, 500, "text/plain; charset=utf-8", "Could not save file")
                else respond(out, 200, "application/json", """{"ok":true}""")
            }
        }
    }

    private fun respond(out: OutputStream, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status ${REASONS[status] ?: "OK"}\r\nContent-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    /** Parsed request line and headers. The body is left on the stream. */
    class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>) {
        companion object {
            fun read(input: InputStream): HttpRequest? {
                val head = readHead(input) ?: return null
                val lines = head.split("\r\n")
                val parts = lines.first().split(' ')
                if (parts.size != REQUEST_LINE_PARTS || !parts[2].startsWith("HTTP/1.")) return null
                val target = parts[1]
                val rawPath = target.substringBefore('?')
                val query = parseQuery(target.substringAfter('?', ""))
                val headers = lines.drop(1).filter { it.isNotEmpty() }.associate {
                    val idx = it.indexOf(':')
                    if (idx <= 0) return null
                    it.substring(0, idx).trim().lowercase() to it.substring(idx + 1).trim()
                }
                return HttpRequest(parts[0], rawPath, query, headers)
            }

            private fun readHead(input: InputStream): String? {
                val buffer = ByteArrayOutputStream()
                var matched = 0
                while (buffer.size() < MAX_HEAD_BYTES) {
                    val b = input.read()
                    if (b < 0) return null
                    buffer.write(b)
                    matched = if (b == TERMINATOR[matched].code) matched + 1 else if (b == '\r'.code) 1 else 0
                    if (matched == TERMINATOR.length) {
                        return buffer.toString(Charsets.ISO_8859_1).removeSuffix(TERMINATOR)
                    }
                }
                return null
            }
        }
    }

    /** Reads at most [limit] bytes, so a sink can never consume the next request's bytes or hang past the body. */
    private class LimitedInput(private val source: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = source.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = source.read(buffer, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }

    companion object {
        const val MAX_UPLOAD_BYTES: Long = 16L * 1024 * 1024 * 1024
        private const val MAX_THREADS = 4
        private const val MAX_HEAD_BYTES = 16 * 1024
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val REQUEST_LINE_PARTS = 3
        private const val TOKEN_BYTES = 18
        private const val TERMINATOR = "\r\n\r\n"
        private val random = SecureRandom()
        private val REASONS = mapOf(
            200 to "OK", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 411 to "Length Required",
            413 to "Payload Too Large", 415 to "Unsupported Media Type", 500 to "Internal Server Error",
        )
        private val MIME_BY_EXTENSION = mapOf(
            "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
            "mov" to "video/quicktime", "avi" to "video/x-msvideo", "ts" to "video/mp2t", "mpg" to "video/mpeg",
            "mpeg" to "video/mpeg", "3gp" to "video/3gpp", "flv" to "video/x-flv", "wmv" to "video/x-ms-wmv",
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac",
            "wav" to "audio/wav", "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/opus",
            "wma" to "audio/x-ms-wma", "aiff" to "audio/aiff",
        )

        fun newToken(): String {
            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun tokenMatches(expected: String, actual: String?): Boolean =
            !actual.isNullOrEmpty() && MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())

        /** The first private (RFC 1918) IPv4 address of an up, non-virtual interface. Needs no Android permission. */
        fun privateIpv4(): Inet4Address? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
        }.getOrNull()

        fun parseQuery(raw: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            for (pair in raw.split('&').filter { it.isNotEmpty() }) {
                val idx = pair.indexOf('=')
                val key = decode(if (idx < 0) pair else pair.substring(0, idx))
                result.putIfAbsent(key, if (idx < 0) "" else decode(pair.substring(idx + 1)))
            }
            return result
        }

        private fun decode(value: String): String =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)

        /** A safe base file name, or null if it is not an accepted audio/video type. */
        fun sanitizeFileName(raw: String): String? {
            val base = raw.substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it != '\u007f' }
                .trim()
                .trimStart('.')
                .take(MAX_NAME_LENGTH)
            return base.takeIf { it.isNotEmpty() && mimeTypeFor(it) != null }
        }

        fun mimeTypeFor(name: String): String? = MIME_BY_EXTENSION[name.substringAfterLast('.', "").lowercase()]

        private const val MAX_NAME_LENGTH = 180

        private val UPLOAD_HTML = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1"><title>Vimivo Wi-Fi Share</title>
            <style>
              body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; background: #0f0f11; color: #f4f4f5; max-width: 560px; margin: 0 auto; padding: 24px 16px; }
              p { color: #a1a1aa; font-size: 14px; line-height: 1.5; }
              button { background: #6366f1; color: #fff; border: 0; border-radius: 12px; padding: 14px 22px; font-size: 16px; font-weight: 600; }
              ul { list-style: none; padding: 0; } li { background: rgba(255,255,255,.06); border-radius: 12px; padding: 12px 14px; margin-bottom: 8px; font-size: 14px; word-break: break-all; }
              .ok { color: #4ade80; } .err { color: #f87171; }
            </style></head><body>
            <h1>Send to Vimivo</h1><p>Pick videos or music. They are copied straight to the phone over your local network.</p>
            <input type="file" id="files" multiple accept="video/*,audio/*" hidden>
            <button onclick="document.getElementById('files').click()">Choose files</button><ul id="log"></ul>
            <script>
              const token = new URLSearchParams(location.search).get('t') || '';
              document.getElementById('files').addEventListener('change', async (e) => {
                for (const file of e.target.files) {
                  const li = document.createElement('li'); li.textContent = file.name + ' — sending…';
                  document.getElementById('log').prepend(li);
                  try {
                    const res = await fetch('/upload?name=' + encodeURIComponent(file.name),
                      { method: 'POST', headers: { 'X-Vimivo-Token': token }, body: file });
                    li.textContent = file.name + (res.ok ? ' — received' : ' — failed (' + (await res.text()) + ')');
                    li.className = res.ok ? 'ok' : 'err';
                  } catch (err) { li.textContent = file.name + ' — failed'; li.className = 'err'; }
                }
                e.target.value = '';
              });
            </script></body></html>
        """.trimIndent()
    }
}
