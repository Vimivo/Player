package dev.vimivo.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

/**
 * A small HTTP/1.1 client. The JVM's HttpURLConnection rejects PROPFIND and Android has no java.net.http, but WebDAV
 * needs arbitrary methods and ranged reads. One request per connection, no redirects, Basic authentication only.
 */
object MiniHttp {
    class Response(val status: Int, val headers: Map<String, String>, val body: InputStream, private val socket: Socket) : AutoCloseable {
        override fun close() {
            runCatching { socket.close() }
        }
    }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        user: String = "",
        password: CharArray = CharArray(0),
        timeoutMillis: Int = 15_000,
    ): Response {
        val uri = URI(url)
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(uri.host, port), timeoutMillis)
            socket.soTimeout = timeoutMillis
            val connected = if (secure) {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, uri.host, port, true)
            } else {
                socket
            }
            val target = (uri.rawPath.ifEmpty { "/" }) + (uri.rawQuery?.let { "?$it" } ?: "")
            val head = StringBuilder("$method $target HTTP/1.1\r\nHost: ${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}\r\n")
            if (user.isNotEmpty()) {
                val token = Base64.getEncoder().encodeToString("$user:${String(password)}".toByteArray(Charsets.UTF_8))
                head.append("Authorization: Basic $token\r\n")
            }
            headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
            head.append("Connection: close\r\nContent-Length: ${body?.size ?: 0}\r\n\r\n")
            val out = connected.getOutputStream()
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            body?.let(out::write)
            out.flush()
            return readResponse(connected, socket)
        } catch (e: IOException) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun readResponse(connected: Socket, original: Socket): Response {
        val input = connected.getInputStream()
        val statusLine = readLine(input) ?: throw IOException("The server closed the connection")
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Not an HTTP reply")
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        val length = headers["content-length"]?.toLongOrNull()
        val body: InputStream = when {
            chunked -> ChunkedInput(input)
            length != null -> LimitedStream(input, length)
            else -> input
        }
        return Response(status, headers, body, original)
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() < MAX_LINE) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.ISO_8859_1)
            if (b == '\n'.code) return buffer.toString(Charsets.ISO_8859_1).trimEnd('\r')
            buffer.write(b)
        }
        throw IOException("Header line too long")
    }

    private const val MAX_LINE = 16 * 1024

    private class LimitedStream(private val source: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = source.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }

    /** Decodes `Transfer-Encoding: chunked`. */
    private class ChunkedInput(private val source: InputStream) : InputStream() {
        private var remaining = 0L
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            if (remaining == 0L) {
                val line = readLine(source) ?: return -1
                val size = line.substringBefore(';').trim().toLongOrNull(16) ?: throw IOException("Bad chunk size")
                if (size == 0L) {
                    finished = true
                    return -1
                }
                remaining = size
            }
            val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) {
                remaining -= n
                if (remaining == 0L) readLine(source) // the CRLF after the chunk
            }
            return n
        }
    }
}
