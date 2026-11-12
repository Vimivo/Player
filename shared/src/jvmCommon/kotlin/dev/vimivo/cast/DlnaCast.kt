package dev.vimivo.cast

import java.net.InetAddress
import java.net.URI

/** A running cast: what is playing where, and the one-file server feeding it if the source is local. */
class CastSession {
    @Volatile
    var renderer: DlnaRenderer? = null
        private set
    private var server: LocalMediaServer? = null

    /**
     * Plays [source] on [renderer]. A local source is served to that device only; [webUrl], when given, is handed over
     * as it is (the renderer fetches it itself). Any earlier cast is stopped first.
     */
    @Synchronized
    fun start(renderer: DlnaRenderer, title: String, mimeType: String, source: CastSource?, webUrl: String?): Result<Unit> {
        close()
        return runCatching {
            val link = when {
                webUrl != null -> webUrl
                source != null -> {
                    val host = InetAddress.getByName(URI(renderer.avTransportControlUrl).host)
                    LocalMediaServer(source, mimeType, allowedClient = host).also { server = it }.start().getOrThrow()
                }
                else -> error("This kind of source cannot be cast")
            }
            val client = DlnaClient(renderer)
            client.load(link, title, mimeType).getOrThrow()
            client.play().getOrThrow()
            this.renderer = renderer
        }.onFailure { close() }
    }

    fun pause(): Result<Unit> = renderer?.let { DlnaClient(it).pause() } ?: Result.success(Unit)
    fun resume(): Result<Unit> = renderer?.let { DlnaClient(it).play() } ?: Result.success(Unit)

    @Synchronized
    fun close() {
        renderer?.let { DlnaClient(it).stop() }
        server?.stop()
        server = null
        renderer = null
    }
}
