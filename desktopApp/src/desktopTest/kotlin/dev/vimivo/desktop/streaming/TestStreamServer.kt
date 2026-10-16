@file:Suppress(
    "ArgumentListWrapping", "MagicNumber", "MaxLineLength", "MaximumLineLength", "TooGenericExceptionCaught",
)

package dev.vimivo.desktop.streaming

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic, in-process HTTP streaming server for testing network media playback,
 * HLS manifests, live playlists, range requests, redirects, and network fault conditions.
 */
class TestStreamServer : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val executor = Executors.newCachedThreadPool()
    private val liveSequence = AtomicInteger(1)

    val port: Int
        get() = server.address.port

    val baseUrl: String
        get() = "http://127.0.0.1:$port"

    init {
        server.executor = executor
        setupEndpoints()
    }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    override fun close() {
        stop()
    }

    private fun setupEndpoints() {
        // Direct Audio (supports 200 and 206 Range)
        server.createContext("/media/audio.mp3") { exchange ->
            handleMediaResponse(
                exchange = exchange,
                contentType = "audio/mpeg",
                data = MOCK_AUDIO_DATA,
            )
        }

        // Direct Video (supports 200 and 206 Range)
        server.createContext("/media/video.mp4") { exchange ->
            handleMediaResponse(
                exchange = exchange,
                contentType = "video/mp4",
                data = MOCK_VIDEO_DATA,
            )
        }

        // HLS VOD Playlist
        server.createContext("/hls/vod.m3u8") { exchange ->
            val manifest = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:2
                #EXT-X-MEDIA-SEQUENCE:0
                #EXTINF:2.0,
                segment0.ts
                #EXTINF:2.0,
                segment1.ts
                #EXT-X-ENDLIST
            """.trimIndent()
            sendTextResponse(exchange, "application/vnd.apple.mpegurl", manifest)
        }

        // HLS Live Playlist (sliding window, no ENDLIST)
        server.createContext("/hls/live.m3u8") { exchange ->
            val seq = liveSequence.getAndIncrement()
            val manifest = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:2
                #EXT-X-MEDIA-SEQUENCE:$seq
                #EXTINF:2.0,
                segment$seq.ts
                #EXTINF:2.0,
                segment${seq + 1}.ts
            """.trimIndent()
            sendTextResponse(exchange, "application/vnd.apple.mpegurl", manifest)
        }

        // HLS Media Segments
        server.createContext("/hls/") { exchange ->
            if (exchange.requestURI.path.endsWith(".ts")) {
                sendBinaryResponse(exchange, "video/MP2T", MOCK_TS_DATA)
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
        }

        // DASH Manifest
        server.createContext("/dash/manifest.mpd") { exchange ->
            val mpd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" minBufferTime="PT1.5S" type="static" mediaPresentationDuration="PT10S">
                  <Period id="0">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                      <Representation id="1" bandwidth="500000" width="640" height="360" codecs="avc1.4d401f"/>
                    </AdaptationSet>
                  </Period>
                </MPD>
            """.trimIndent()
            sendTextResponse(exchange, "application/dash+xml", mpd)
        }

        // HTTP Fault Simulation
        server.createContext("/fault/401") { it.sendResponseHeaders(401, -1) }
        server.createContext("/fault/403") { it.sendResponseHeaders(403, -1) }
        server.createContext("/fault/404") { it.sendResponseHeaders(404, -1) }
        server.createContext("/fault/408") { it.sendResponseHeaders(408, -1) }
        server.createContext("/fault/429") { it.sendResponseHeaders(429, -1) }
        server.createContext("/fault/500") { it.sendResponseHeaders(500, -1) }

        // Bounded Redirect
        server.createContext("/fault/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/media/audio.mp3")
            exchange.sendResponseHeaders(302, -1)
        }

        // Redirect Loop
        server.createContext("/fault/redirect-loop") { exchange ->
            exchange.responseHeaders.add("Location", "/fault/redirect-loop")
            exchange.sendResponseHeaders(302, -1)
        }
    }

    private fun handleMediaResponse(exchange: HttpExchange, contentType: String, data: ByteArray) {
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range.getOrNull(0)?.toIntOrNull() ?: 0
            val end = range.getOrNull(1)?.toIntOrNull() ?: (data.size - 1)
            val clampedEnd = end.coerceAtMost(data.size - 1)
            val length = (clampedEnd - start + 1).coerceAtLeast(0)

            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.responseHeaders.add("Content-Range", "bytes $start-$clampedEnd/${data.size}")
            exchange.sendResponseHeaders(206, length.toLong())
            exchange.responseBody.use { os ->
                os.write(data, start, length)
            }
        } else {
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { os ->
                os.write(data)
            }
        }
    }

    private fun sendTextResponse(exchange: HttpExchange, contentType: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendBinaryResponse(exchange: HttpExchange, contentType: String, data: ByteArray) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { it.write(data) }
    }

    companion object {
        // Minimal valid MP3 header and silence frames
        private val MOCK_AUDIO_DATA = ByteArray(4096) { 0xFF.toByte() }

        // Minimal mock MP4 structure bytes
        private val MOCK_VIDEO_DATA = ByteArray(8192) { 0x00.toByte() }

        // Mock TS packet sync byte (0x47)
        private val MOCK_TS_DATA = ByteArray(188 * 10) { if (it % 188 == 0) 0x47.toByte() else 0x00.toByte() }
    }
}
