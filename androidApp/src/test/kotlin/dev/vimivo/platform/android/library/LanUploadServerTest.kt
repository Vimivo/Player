package dev.vimivo.platform.android.library

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import org.junit.Test

class LanUploadServerTest {
    private val saved = mutableMapOf<String, ByteArray>()
    private var server: LanUploadServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun start(maxBytes: Long = 1024, failing: Boolean = false): Pair<String, String> {
        val s = LanUploadServer(
            sink = { name, _, _, body -> if (failing) null else name.also { synchronized(saved) { saved[name] = body.readBytes() } } },
            bindAddress = InetAddress.getLoopbackAddress(),
            preferredPort = 0,
            maxBytes = maxBytes,
        )
        s.start()
        server = s
        val url = checkNotNull(s.url)
        return url.substringBefore("/?t=") to url.substringAfter("?t=")
    }

    private fun post(base: String, token: String?, name: String, data: ByteArray): Int {
        val conn = URI("$base/upload?name=${URLEncoder.encode(name, Charsets.UTF_8)}").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(data.size)
        if (token != null) conn.setRequestProperty("X-Vimivo-Token", token)
        conn.outputStream.use { it.write(data) }
        return conn.responseCode.also { conn.disconnect() }
    }

    private fun get(base: String, path: String): Int {
        val conn = URI("$base$path").toURL().openConnection() as HttpURLConnection
        return conn.responseCode.also { conn.disconnect() }
    }

    @Test
    fun uploadNeedsTheSessionToken() {
        val (base, token) = start()
        assertEquals(403, post(base, null, "a.mp3", byteArrayOf(1)))
        assertEquals(403, post(base, "wrong", "a.mp3", byteArrayOf(1)))
        assertEquals(403, get(base, "/"))
        assertEquals(200, get(base, "/?t=$token"))
        assertTrue(saved.isEmpty())
    }

    @Test
    fun savesExactlyTheSentBytes() {
        val (base, token) = start()
        val data = ByteArray(700) { (it * 7).toByte() }
        assertEquals(200, post(base, token, "clip.mp4", data))
        assertArrayEquals(data, saved["clip.mp4"])
    }

    @Test
    fun rejectsOversizeAndNonMediaFiles() {
        val (base, token) = start(maxBytes = 10)
        assertEquals(413, post(base, token, "big.mp4", ByteArray(11)))
        assertEquals(415, post(base, token, "run.apk", ByteArray(2)))
        assertEquals(415, post(base, token, "readme.txt", ByteArray(2)))
        assertTrue(saved.isEmpty())
    }

    @Test
    fun reportsSinkFailureAsServerError() {
        val (base, token) = start(failing = true)
        assertEquals(500, post(base, token, "a.mp3", byteArrayOf(1, 2)))
    }

    @Test
    fun noLanAddressGivesActionableError() {
        val s = LanUploadServer(sink = { _, _, _, _ -> null }, bindAddress = null)
        s.start()
        assertNull(s.url)
        assertTrue(checkNotNull(s.startError).contains("Wi-Fi"))
    }

    @Test
    fun parserReadsRequestLineAndHeaders() {
        val raw = "POST /upload?name=a%20b.mp3&t=k HTTP/1.1\r\nHost: x\r\nContent-Length: 3\r\n\r\nabc"
        val stream = ByteArrayInputStream(raw.toByteArray())
        val req = checkNotNull(LanUploadServer.HttpRequest.read(stream))
        assertEquals("POST", req.method)
        assertEquals("/upload", req.path)
        assertEquals("a b.mp3", req.query["name"])
        assertEquals("3", req.headers["content-length"])
        assertEquals("abc", stream.readBytes().decodeToString())
    }

    @Test
    fun parserRejectsMalformedRequests() {
        for (raw in listOf("GARBAGE\r\n\r\n", "GET / FTP/1.0\r\n\r\n", "GET / HTTP/1.1\r\nbadheader\r\n\r\n", "GET / HTTP/1.1\r\n")) {
            assertNull(raw, LanUploadServer.HttpRequest.read(ByteArrayInputStream(raw.toByteArray())))
        }
    }

    @Test
    fun fileNamesCannotEscapeAndTypesAreAllowListed() {
        assertEquals("x.mp3", LanUploadServer.sanitizeFileName("../../etc/x.mp3"))
        assertEquals("x.mkv", LanUploadServer.sanitizeFileName("C:\\a\\x.mkv"))
        assertEquals("hid.mp4", LanUploadServer.sanitizeFileName("..hid.mp4"))
        assertNull(LanUploadServer.sanitizeFileName("a.exe"))
        assertNull(LanUploadServer.sanitizeFileName(""))
    }
}
