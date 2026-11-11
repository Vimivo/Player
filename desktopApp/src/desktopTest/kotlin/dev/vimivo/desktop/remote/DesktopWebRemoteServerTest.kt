package dev.vimivo.desktop.remote

import dev.vimivo.desktop.player.PlayerIntent
import dev.vimivo.desktop.player.PlayerState
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWebRemoteServerTest {
    private val intents = mutableListOf<PlayerIntent>()
    private var server: DesktopWebRemoteServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    private fun startServer(): Pair<String, String> {
        val s = DesktopWebRemoteServer(
            preferredPort = 0,
            stateProvider = { PlayerState() },
            onIntent = { synchronized(intents) { intents.add(it) } },
            bindAddress = InetAddress.getLoopbackAddress(),
        )
        s.start()
        server = s
        val url = assertNotNull(s.serverUrl)
        val token = url.substringAfter("?t=")
        return url.substringBefore("/?t=") to token
    }

    private fun call(base: String, path: String, method: String = "GET", token: String? = null, headerToken: Boolean = false): Pair<Int, String> {
        val suffix = if (token != null && !headerToken) (if ('?' in path) "&t=$token" else "?t=$token") else ""
        val conn = URI("$base$path$suffix").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        if (token != null && headerToken) conn.setRequestProperty("X-Vimivo-Token", token)
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.readBytes()?.decodeToString().orEmpty()
        conn.disconnect()
        return code to text
    }

    @Test
    fun rejectsRequestsWithoutOrWithWrongToken() {
        val (base, token) = startServer()
        assertEquals(403, call(base, "/api/status").first)
        assertEquals(403, call(base, "/api/status", token = "nope").first)
        assertEquals(403, call(base, "/api/action?cmd=play_pause", method = "POST").first)
        assertEquals(200, call(base, "/api/status", token = token).first)
        assertEquals(200, call(base, "/api/status", token = token, headerToken = true).first)
        assertTrue(synchronized(intents) { intents.isEmpty() })
    }

    @Test
    fun statusIsValidJsonWithoutPermissiveCors() {
        val (base, token) = startServer()
        val (code, body) = call(base, "/api/status", token = token)
        assertEquals(200, code)
        assertTrue(body.startsWith("{") && body.contains("\"isPlaying\":false"))
    }

    @Test
    fun actionsRequirePostAndValidateInput() {
        val (base, token) = startServer()
        assertEquals(404, call(base, "/api/action?cmd=play_pause", method = "GET", token = token).first)
        assertEquals(200, call(base, "/api/action?cmd=play_pause", method = "POST", token = token).first)
        assertEquals(400, call(base, "/api/action?cmd=format_disk", method = "POST", token = token).first)
        assertEquals(400, call(base, "/api/action?cmd=seek&val=-5", method = "POST", token = token).first)
        assertEquals(400, call(base, "/api/action?cmd=seek&val=abc", method = "POST", token = token).first)
        assertEquals(200, call(base, "/api/action?cmd=volume&val=900", method = "POST", token = token).first)
        val seen = synchronized(intents) { intents.toList() }
        assertEquals(PlayerIntent.TogglePlayback, seen[0])
        assertEquals(PlayerIntent.SetVolume(1f), seen[1])
        assertEquals(2, seen.size)
    }

    @Test
    fun reportsActionableErrorWhenNoLanAddress() {
        val s = DesktopWebRemoteServer(
            stateProvider = { PlayerState() },
            onIntent = {},
            bindAddress = null,
        )
        s.start()
        assertNull(s.serverUrl)
        assertTrue(assertNotNull(s.startError).contains("network"))
    }
}
