package dev.vimivo.cast

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DlnaTest {
    private val description = """
        <?xml version="1.0"?><root><device>
        <friendlyName>Living Room &amp; TV</friendlyName><manufacturer>Acme</manufacturer><UDN>uuid:abc-123</UDN>
        <serviceList>
          <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/rc/control</controlURL></service>
          <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/AVT/control</controlURL></service>
        </serviceList></device></root>
    """.trimIndent()

    @Test
    fun searchRequestIsAValidMSearch() {
        val request = Ssdp.searchRequest()
        assertContains(request, "M-SEARCH * HTTP/1.1\r\n")
        assertContains(request, "MAN: \"ssdp:discover\"")
        assertContains(request, "ST: urn:schemas-upnp-org:device:MediaRenderer:1")
        assertEquals("\r\n\r\n", request.takeLast(4))
    }

    @Test
    fun parsesAnSsdpAnswerAndIgnoresJunk() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.20:49152/desc.xml\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nUSN: uuid:abc::urn:x\r\n\r\n"
        val parsed = assertNotNull(Ssdp.parseResponse(answer))
        assertEquals("http://192.168.1.20:49152/desc.xml", parsed.location)
        assertNull(Ssdp.parseResponse("NOTIFY * HTTP/1.1\r\nLOCATION: http://x/\r\n\r\n"))
        assertNull(Ssdp.parseResponse("HTTP/1.1 200 OK\r\nLOCATION: ftp://x/y\r\n\r\n"))
        assertNull(Ssdp.parseResponse(""))
    }

    @Test
    fun readsTheRendererFromItsDescription() {
        val renderer = assertNotNull(DlnaDescription.parse(description, "http://192.168.1.20:49152/desc.xml"))
        assertEquals("Living Room & TV", renderer.name)
        assertEquals("http://192.168.1.20:49152/AVT/control", renderer.avTransportControlUrl)
        assertEquals("uuid:abc-123", renderer.id)
        assertEquals("Acme", renderer.manufacturer)
    }

    @Test
    fun deviceWithoutAvTransportIsNotARenderer() {
        val speakerOnly = description.replace("AVTransport", "ConnectionManager")
        assertNull(DlnaDescription.parse(speakerOnly, "http://h/d.xml"))
        assertNull(DlnaDescription.parse("<root/>", "http://h/d.xml"))
    }

    @Test
    fun resolvesRelativeAndAbsoluteControlUrls() {
        assertEquals("http://h:1/x/y", DlnaDescription.resolve("http://h:1/a/d.xml", "http://h:1/x/y"))
        assertEquals("http://h:1/ctl", DlnaDescription.resolve("http://h:1/a/d.xml", "/ctl"))
        assertEquals("http://h:1/a/ctl", DlnaDescription.resolve("http://h:1/a/d.xml", "ctl"))
        assertEquals("http://h/ctl", DlnaDescription.resolve("http://h", "ctl"))
    }

    @Test
    fun setUriEscapesTheLinkAndTitleSoTheXmlStaysValid() {
        val call = AvTransport.setUri("http://10.0.0.2:8080/m?a=1&b=2", "Tom & <Jerry>", "video/mp4")
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", call.action)
        assertContains(call.body, "<CurrentURI>http://10.0.0.2:8080/m?a=1&amp;b=2</CurrentURI>")
        assertFalse(call.body.contains("Tom & <Jerry>"))
        assertContains(call.body, "Tom &amp;amp; &amp;lt;Jerry&amp;gt;")
        assertContains(call.body, "object.item.videoItem")
    }

    @Test
    fun audioIsDescribedAsAMusicTrack() {
        assertContains(AvTransport.didl("Song", "http://h/a.mp3", "audio/mpeg"), "object.item.audioItem.musicTrack")
    }

    @Test
    fun transportCommands() {
        assertContains(AvTransport.play().body, "<u:Play ")
        assertContains(AvTransport.play().body, "<Speed>1</Speed>")
        assertContains(AvTransport.pause().action, "#Pause")
        assertContains(AvTransport.stop().action, "#Stop")
        assertContains(AvTransport.seek(3_725_000).body, "<Target>1:02:05</Target>")
    }

    @Test
    fun timeFormatting() {
        assertEquals("0:00:00", AvTransport.formatTime(0))
        assertEquals("0:00:00", AvTransport.formatTime(-5))
        assertEquals("0:01:05", AvTransport.formatTime(65_900))
        assertEquals("10:00:00", AvTransport.formatTime(36_000_000))
    }
}
