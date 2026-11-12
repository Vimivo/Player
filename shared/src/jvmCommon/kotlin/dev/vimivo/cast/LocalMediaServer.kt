package dev.vimivo.cast

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Serves ONE local file to a cast target, with byte ranges so the TV can seek. Only the chosen renderer
 * ([allowedClient]) may fetch it, on a random-token path, on a private LAN address; stop it when casting ends.
 */
class LocalMediaServer(
    private val file: File,
    private val mimeType: String,
    private val allowedClient: InetAddress?,
    private val bindAddress: InetAddress? = localPrivateIpv4Addresses().firstOrNull(),
) {
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes))
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null

    @Volatile
    var url: String? = null
        private set

    @Synchronized
    fun start(): Result<String> {
        server?.let { return Result.success(checkNotNull(url)) }
        val address = bindAddress ?: return Result.failure(IOException("No local network connection found"))
        if (!file.isFile) return Result.failure(IOException("The file is not available"))
        val socket = try {
            ServerSocket().apply { bind(InetSocketAddress(address, 0)) }
        } catch (e: IOException) {
            return Result.failure(e)
        }
        server = socket
        url = "http://${address.hostAddress}:${socket.localPort}/m/$token/${URLEncoder.encode(file.name, Charsets.UTF_8).replace("+", "%20")}"
        val executor = Executors.newFixedThreadPool(MAX_THREADS) { r -> Thread(r, "vimivo-cast-media").apply { isDaemon = true } }
        pool = executor
        executor.execute { acceptLoop(socket, executor) }
        return Result.success(checkNotNull(url))
    }

    @Synchronized
    fun stop() {
        url = null
        runCatching { server?.close() }
        server = null
        pool?.shutdownNow()
        pool = null
    }

    private fun acceptLoop(socket: ServerSocket, executor: ExecutorService) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                return
            }
            try {
                executor.execute { client.use(::serve) }
            } catch (_: RejectedExecutionException) {
                client.close()
            }
        }
    }

    private fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MILLIS
        val out = client.getOutputStream()
        try {
            if (allowedClient != null && client.inetAddress != allowedClient) return respond(out, 403, "Forbidden")
            val request = readRequest(client.getInputStream()) ?: return respond(out, 400, "Bad request")
            if (request.method != "GET" && request.method != "HEAD") return respond(out, 405, "Method not allowed")
            val segments = request.path.split('/')
            val tokenOk = segments.size >= 3 && segments[1] == "m" &&
                MessageDigest.isEqual(token.toByteArray(), segments[2].toByteArray())
            if (!tokenOk) return respond(out, 404, "Not found")
            sendFile(out, request)
        } catch (_: SocketTimeoutException) {
            // The renderer stalled; drop the connection.
        } catch (_: IOException) {
            // The renderer closed the connection (seeking does this constantly).
        }
    }

    private fun sendFile(out: OutputStream, request: Request) {
        val length = file.length()
        val range = parseRange(request.range, length)
        if (range == null && request.range != null) {
            out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$length\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (length - 1)
        val count = if (length == 0L) 0 else end - start + 1
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val head = StringBuilder("HTTP/1.1 $status\r\nContent-Type: $mimeType\r\nContent-Length: $count\r\nAccept-Ranges: bytes\r\n")
        if (range != null) head.append("Content-Range: bytes $start-$end/$length\r\n")
        head.append("transferMode.dlna.org: Streaming\r\ncontentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0\r\nConnection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        if (request.method == "HEAD" || count == 0L) return
        RandomAccessFile(file, "r").use { source ->
            source.seek(start)
            val buffer = ByteArray(COPY_BUFFER)
            var remaining = count
            while (remaining > 0) {
                val read = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        out.flush()
    }

    private fun respond(out: OutputStream, status: Int, text: String) {
        val body = text.toByteArray()
        out.write("HTTP/1.1 $status $text\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private class Request(val method: String, val path: String, val range: String?)

    private fun readRequest(input: InputStream): Request? {
        val head = StringBuilder()
        var matched = 0
        while (head.length < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            head.append(b.toChar())
            matched = if (b == "\r\n\r\n"[matched].code) matched + 1 else if (b == '\r'.code) 1 else 0
            if (matched == 4) break
        }
        if (matched != 4) return null
        val lines = head.toString().split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
        val range = lines.drop(1).firstOrNull { it.startsWith("range:", ignoreCase = true) }?.substringAfter(':')?.trim()
        return Request(parts[0], parts[1].substringBefore('?'), range)
    }

    companion object {
        private const val TOKEN_BYTES = 18
        private const val MAX_THREADS = 4
        private const val MAX_HEAD_BYTES = 16 * 1024
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val COPY_BUFFER = 64 * 1024

        /** `bytes=a-b`, `bytes=a-` or `bytes=-n` as an inclusive range inside a file of [length] bytes; null if unusable. */
        fun parseRange(header: String?, length: Long): LongRange? {
            if (header == null || length <= 0 || !header.startsWith("bytes=") || ',' in header) return null
            val (from, to) = header.removePrefix("bytes=").split('-', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
            val start: Long
            val end: Long
            if (from.isEmpty()) {
                val suffix = to.toLongOrNull()?.takeIf { it > 0 } ?: return null
                start = (length - suffix).coerceAtLeast(0)
                end = length - 1
            } else {
                start = from.toLongOrNull() ?: return null
                end = if (to.isEmpty()) length - 1 else (to.toLongOrNull() ?: return null).coerceAtMost(length - 1)
            }
            return if (start in 0..end && start < length) start..end else null
        }
    }
}
