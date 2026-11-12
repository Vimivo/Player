package dev.vimivo.network

import dev.vimivo.cast.CastMime
import dev.vimivo.cast.LocalMediaServer
import java.net.InetAddress

/**
 * A connected share plus the loopback server that lets this device's own player read its files. The player is handed an
 * `http://127.0.0.1:port/...` link, so SMB, SFTP and WebDAV work with any engine and no password ever appears in a URL.
 */
class ShareSession(val fs: RemoteFileSystem, val label: String) : AutoCloseable {
    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val server = LocalMediaServer(
        resolve = { relative -> if (relative.isEmpty()) null else RemoteCastSource(fs, relative) },
        mimeOf = CastMime::forName,
        allowedClient = loopback,
        bindAddress = loopback,
    )

    /** The link the player opens for [path] (relative to the share root). Starts the proxy on first use. */
    @Synchronized
    fun playUrl(path: String): Result<String> =
        server.start().mapCatching { checkNotNull(server.urlFor(path)) { "The local player link is unavailable" } }

    override fun close() {
        server.stop()
        runCatching { fs.close() }
    }

    companion object {
        fun smb(host: String, share: String, user: String, password: CharArray, domain: String = ""): Result<ShareSession> =
            SmbFileSystem.connect(host.trim(), share.trim().trim('/'), user.trim(), password, domain.trim())
                .map { ShareSession(it, "$host/${share.trim().trim('/')}") }

        fun sftp(host: String, port: Int, user: String, password: CharArray, knownHosts: KnownHosts): Result<ShareSession> =
            SftpFileSystem.connect(host.trim(), port, user.trim(), password, knownHosts).map { ShareSession(it, host.trim()) }

        fun webDav(url: String, user: String, password: CharArray): Result<ShareSession> =
            WebDavFileSystem.connect(url, user.trim(), password).map { ShareSession(it, url.trim()) }
    }
}

/** Holds the share that is connected right now so it outlives the dialog (the player keeps reading from it). */
class ShareSessionHolder : AutoCloseable {
    @Volatile
    var current: ShareSession? = null
        private set

    @Synchronized
    fun replace(session: ShareSession?) {
        current?.close()
        current = session
    }

    override fun close() = replace(null)
}
