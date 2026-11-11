package dev.vimivo.desktop.sharing

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWifiTransferServerTest {
    private val dest: Path = Files.createTempDirectory("vimivo-wifi-test")
    private val received = mutableListOf<Path>()
    private var server: DesktopWifiTransferServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
        dest.toFile().deleteRecursively()
    }

    private fun start(maxBytes: Long = 1024): Pair<String, String> {
        val s = DesktopWifiTransferServer(
            destination = dest,
            onFileReceived = { synchronized(received) { received.add(it) } },
            preferredPort = 0,
            bindAddress = InetAddress.getLoopbackAddress(),
            maxBytes = maxBytes,
        )
        s.start()
        server = s
        val url = assertNotNull(s.serverUrl)
        return url.substringBefore("/?t=") to url.substringAfter("?t=")
    }

    private fun upload(base: String, token: String?, name: String, data: ByteArray): Int {
        val enc = URLEncoder.encode(name, Charsets.UTF_8)
        val conn = URI("$base/upload?name=$enc").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(data.size)
        if (token != null) conn.setRequestProperty("X-Vimivo-Token", token)
        conn.outputStream.use { it.write(data) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    @Test
    fun uploadRequiresToken() {
        val (base, _) = start()
        assertEquals(403, upload(base, null, "a.mp3", byteArrayOf(1, 2)))
        assertEquals(403, upload(base, "wrong", "a.mp3", byteArrayOf(1, 2)))
        assertTrue(Files.list(dest).use { it.count() } == 0L)
    }

    @Test
    fun savesFileAndDeduplicatesNames() {
        val (base, token) = start()
        val data = ByteArray(300) { it.toByte() }
        assertEquals(200, upload(base, token, "song.mp3", data))
        assertEquals(200, upload(base, token, "song.mp3", data))
        assertContentEquals(data, Files.readAllBytes(dest.resolve("song.mp3")))
        assertTrue(Files.exists(dest.resolve("song (1).mp3")))
        assertEquals(2, synchronized(received) { received.size })
        assertTrue(Files.list(dest).use { s -> s.noneMatch { it.fileName.toString().endsWith(".part") } })
    }

    @Test
    fun rejectsOversizedAndDisallowedFiles() {
        val (base, token) = start(maxBytes = 10)
        assertEquals(413, upload(base, token, "big.mp4", ByteArray(11)))
        assertEquals(415, upload(base, token, "evil.sh", ByteArray(2)))
        assertEquals(415, upload(base, token, "noext", ByteArray(2)))
        assertTrue(Files.list(dest).use { it.count() } == 0L)
    }

    @Test
    fun fileNamesCannotEscapeDestination() {
        assertEquals("passwd.mp3", DesktopWifiTransferServer.sanitizeFileName("../../etc/passwd.mp3"))
        assertEquals("x.mkv", DesktopWifiTransferServer.sanitizeFileName("C:\\Users\\me\\x.mkv"))
        assertEquals("hidden.mp4", DesktopWifiTransferServer.sanitizeFileName("...hidden.mp4"))
        assertNull(DesktopWifiTransferServer.sanitizeFileName(""))
        assertNull(DesktopWifiTransferServer.sanitizeFileName("a.exe"))
    }
}
