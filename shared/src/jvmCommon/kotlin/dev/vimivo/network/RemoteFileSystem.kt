package dev.vimivo.network

import dev.vimivo.cast.CastSource
import java.io.InputStream

/** Read-only access to one remote share. Blocking calls: use them from a background thread. */
interface RemoteFileSystem : AutoCloseable {
    /** The folder's children. [path] is relative to the share root; "" is the root. */
    fun list(path: String): List<RemoteEntry>
    fun length(path: String): Long

    /** A stream positioned at [offset]; the caller closes it. */
    fun open(path: String, offset: Long): InputStream
}

/** Lets the media server read a share file in ranges. */
class RemoteCastSource(private val fs: RemoteFileSystem, private val path: String) : CastSource {
    override val name: String get() = RemotePaths.name(path)
    override fun isAvailable(): Boolean = runCatching { fs.length(path) >= 0 }.getOrDefault(false)
    override fun length(): Long = fs.length(path)
    override fun open(offset: Long): InputStream = fs.open(path, offset)
}

/** Why a connection could not be made, in words a person can act on. */
class ShareException(message: String, cause: Throwable? = null) : Exception(message, cause)
