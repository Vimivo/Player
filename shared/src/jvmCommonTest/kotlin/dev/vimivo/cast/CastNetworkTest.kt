package dev.vimivo.cast

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CastNetworkTest {
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val cleanup = mutableListOf<() -> Unit>()

    @AfterTest
    fun tearDown() = cleanup.forEach { it() }

    private fun mediaFile(size: Int = 1000) = File.createTempFile("vimivo-cast", ".mp4").also {
        it.writeBytes(ByteArray(size) { i -> (i % 251).toByte() })
        cleanup += { it.delete() }
    }

    private fun get(url: String, range: String? = null, method: String = "GET"): Triple<Int, ByteArray, Map<String, List<String>>> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
        return Triple(code, body, c.headerFields).also { c.disconnect() }
    }

    @Test
    fun servesTheWholeFileAndByteRanges() {
        val file = mediaFile()
        val server = LocalMediaServer(file, "video/mp4", allowedClient = null, bindAddress = loopback)
        cleanup += server::stop
        val url = server.start().getOrThrow()
        val bytes = file.readBytes()

        val full = get(url)
        assertEquals(200, full.first)
        assertContentEquals(bytes, full.second)

        val part = get(url, range = "bytes=10-19")
        assertEquals(206, part.first)
        assertContentEquals(bytes.copyOfRange(10, 20), part.second)
        assertContains(part.third["Content-Range"]!!.single(), "bytes 10-19/1000")

        val tail = get(url, range = "bytes=990-")
        assertContentEquals(bytes.copyOfRange(990, 1000), tail.second)
        assertEquals(416, get(url, range = "bytes=5000-6000").first)
        assertEquals(0, get(url, method = "HEAD").second.size)
    }

    @Test
    fun refusesWrongTokensAndOtherClients() {
        val file = mediaFile()
        val server = LocalMediaServer(file, "video/mp4", allowedClient = null, bindAddress = loopback)
        cleanup += server::stop
        val url = server.start().getOrThrow()
        assertEquals(404, get(url.replace("/m/", "/m/x")).first)
        assertEquals(404, get(url.substringBefore("/m/") + "/other").first)

        val strict = LocalMediaServer(file, "video/mp4", allowedClient = InetAddress.getByName("10.255.255.1"), bindAddress = loopback)
        cleanup += strict::stop
        assertEquals(403, get(strict.start().getOrThrow()).first)
    }

    @Test
    fun rangeParsing() {
        assertEquals(0L..99L, LocalMediaServer.parseRange("bytes=0-99", 1000))
        assertEquals(900L..999L, LocalMediaServer.parseRange("bytes=-100", 1000))
        assertEquals(500L..999L, LocalMediaServer.parseRange("bytes=500-", 1000))
        assertEquals(0L..999L, LocalMediaServer.parseRange("bytes=0-5000", 1000))
        assertNull(LocalMediaServer.parseRange("bytes=2000-3000", 1000))
        assertNull(LocalMediaServer.parseRange("bytes=5-1", 1000))
        assertNull(LocalMediaServer.parseRange("bytes=0-1,5-6", 1000))
        assertNull(LocalMediaServer.parseRange("items=0-1", 1000))
        assertNull(LocalMediaServer.parseRange(null, 1000))
    }

    @Test
    fun refusesToStartWithoutNetworkOrFile() {
        assertTrue(LocalMediaServer(mediaFile(), "video/mp4", null, bindAddress = null).start().isFailure)
        assertTrue(LocalMediaServer(File("/nonexistent/x.mp4"), "video/mp4", null, bindAddress = loopback).start().isFailure)
    }

    @Test
    fun discoversARendererAndControlsIt() {
        val soapActions = java.util.concurrent.CopyOnWriteArrayList<String>()
        val http = HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            createContext("/desc.xml") { ex ->
                val xml = """<root><device><friendlyName>Test TV</friendlyName><UDN>uuid:tv-1</UDN><serviceList><service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/ctl</controlURL>
                    </service></serviceList></device></root>""".toByteArray()
                ex.sendResponseHeaders(200, xml.size.toLong()); ex.responseBody.use { it.write(xml) }
            }
            createContext("/ctl") { ex ->
                soapActions += ex.requestHeaders.getFirst("SOAPACTION")
                ex.requestBody.readBytes()
                ex.sendResponseHeaders(200, -1); ex.close()
            }
            start()
        }
        cleanup += { http.stop(0) }

        val ssdp = DatagramSocket(InetSocketAddress(loopback, 0)).also { cleanup += it::close }
        thread(isDaemon = true) {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            runCatching {
                ssdp.receive(packet)
                val reply = "HTTP/1.1 200 OK\r\nLOCATION: http://127.0.0.1:${http.address.port}/desc.xml\r\nST: x\r\nUSN: y\r\n\r\n".toByteArray()
                ssdp.send(DatagramPacket(reply, reply.size, packet.socketAddress))
            }
        }

        val found = DlnaDiscovery.discover(
            timeoutMillis = 1500,
            localAddresses = listOf(loopback),
            searchAddress = InetSocketAddress(loopback, ssdp.localPort),
        )
        assertEquals(listOf("Test TV"), found.map { it.name })

        val client = DlnaClient(found.single())
        assertTrue(client.load("http://10.0.0.2/m.mp4", "Movie", "video/mp4").isSuccess)
        assertTrue(client.play().isSuccess)
        assertTrue(client.seek(5_000).isSuccess)
        assertEquals(3, soapActions.size)
        assertContains(soapActions[0], "SetAVTransportURI")
        assertContains(soapActions[1], "#Play")
    }

    @Test
    fun clientReportsAnUnreachableRenderer() {
        val dead = DlnaRenderer("x", "Dead TV", "http://127.0.0.1:1/ctl")
        val result = DlnaClient(dead).play()
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message.orEmpty(), "Dead TV")
    }
}
