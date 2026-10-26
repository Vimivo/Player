package dev.vimivo.desktop.platform

import dev.vimivo.desktop.player.LaunchArguments
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps Vimivo to one running instance per user. The first instance holds a file lock and listens on a
 * Unix-domain socket in the per-user data folder; later launches forward their files there and exit.
 * Unix-domain sockets work on macOS, Linux and Windows 10+, so no platform-specific IPC is needed.
 */
internal class DesktopSingleInstance(private val directory: Path) {
    private val socketPath: Path = socketPathFor(directory)
    private val lockPath: Path = directory.resolve(LOCK_NAME)

    /** Sends [launch] to a running instance; true when it accepted the request. */
    fun forward(launch: LaunchArguments): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(socketPath))
            val writer = Channels.newWriter(channel, Charsets.UTF_8)
            writer.write(encode(launch) + "\n")
            writer.flush()
            val reply = ByteBuffer.allocate(ACK.length)
            while (reply.hasRemaining() && channel.read(reply) >= 0) Unit
            String(reply.array(), Charsets.UTF_8) == ACK
        }
    }.getOrDefault(false)

    /**
     * Becomes the primary instance and delivers forwarded launches to [onLaunch] (on a background thread).
     * Returns null when another process already holds the primary role.
     */
    fun becomePrimary(onLaunch: (LaunchArguments) -> Unit): AutoCloseable? {
        Files.createDirectories(directory)
        val lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock: FileLock = runCatching { lockChannel.tryLock() }.getOrNull() ?: run {
            lockChannel.close()
            return null
        }
        // Holding the lock proves any existing socket file is stale (a crashed instance).
        val server = runCatching {
            Files.deleteIfExists(socketPath)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(socketPath)) }
        }.getOrElse { error ->
            // Never keep the lock without a socket: later launches would find neither a primary nor the lock.
            lock.release()
            lockChannel.close()
            throw error
        }
        val worker = thread(name = "vimivo-single-instance", isDaemon = true) { serve(server, onLaunch) }
        return AutoCloseable {
            runCatching { server.close() }
            worker.interrupt()
            runCatching { Files.deleteIfExists(socketPath) }
            runCatching { lock.release() }
            runCatching { lockChannel.close() }
        }
    }

    private fun serve(server: ServerSocketChannel, onLaunch: (LaunchArguments) -> Unit) {
        while (server.isOpen) {
            val client = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            client.use { channel ->
                runCatching {
                    val line = Channels.newReader(channel, Charsets.UTF_8).buffered().readLine()
                    val launch = decode(line) ?: return@runCatching
                    channel.write(ByteBuffer.wrap(ACK.toByteArray(Charsets.UTF_8)))
                    onLaunch(launch)
                }
            }
        }
    }

    companion object {
        private const val SOCKET_NAME = "instance.sock"
        private const val LOCK_NAME = "instance.lock"
        private const val ACK = "OK"

        /** Unix-domain socket paths are limited to ~104 bytes (macOS) / 108 (Linux); stay well below. */
        private const val MAX_SOCKET_PATH_BYTES = 100

        /**
         * The socket lives beside the lock unless that path is too long (deep home or XDG directories); then it
         * moves to the per-user temporary directory under a name derived from the data directory.
         */
        fun socketPathFor(directory: Path): Path {
            val preferred = directory.resolve(SOCKET_NAME).toAbsolutePath()
            if (preferred.toString().toByteArray(Charsets.UTF_8).size <= MAX_SOCKET_PATH_BYTES) return preferred
            val tag = Integer.toHexString(directory.toAbsolutePath().toString().hashCode())
            return Path.of(System.getProperty("java.io.tmpdir"), "vimivo-$tag.sock").toAbsolutePath()
        }

        fun forCurrentUser(): DesktopSingleInstance = DesktopSingleInstance(DesktopDirectories.resolve().data)

        fun encode(launch: LaunchArguments): String = buildJsonObject {
            put("files", JsonArray(launch.files.map { JsonPrimitive(it.absolutePath) }))
            launch.startPositionMillis?.let { put("start", it) }
            launch.endPositionMillis?.let { put("end", it) }
            put("fullscreen", launch.fullscreen)
            launch.volume?.let { put("volume", it) }
            launch.speed?.let { put("speed", it) }
            launch.subtitleFile?.let { put("sub", it.absolutePath) }
            launch.audioTrack?.let { put("audioTrack", it) }
            put("loop", launch.loop)
            put("enqueue", launch.enqueue)
            put("audioOnly", launch.audioOnly)
        }.toString()

        fun decode(line: String?): LaunchArguments? = runCatching {
            val json = Json.parseToJsonElement(checkNotNull(line)).jsonObject
            val filesList = json["files"]?.jsonArray?.map { File(it.jsonPrimitive.content) }
                ?: json["path"]?.jsonPrimitive?.content?.let { listOf(File(it)) }
                ?: emptyList()
            LaunchArguments(
                files = filesList,
                startPositionMillis = json["start"]?.jsonPrimitive?.longOrNull,
                endPositionMillis = json["end"]?.jsonPrimitive?.longOrNull,
                fullscreen = json["fullscreen"]?.jsonPrimitive?.booleanOrNull ?: false,
                volume = json["volume"]?.jsonPrimitive?.content?.toFloatOrNull(),
                speed = json["speed"]?.jsonPrimitive?.content?.toFloatOrNull(),
                subtitleFile = json["sub"]?.jsonPrimitive?.content?.let(::File),
                audioTrack = json["audioTrack"]?.jsonPrimitive?.content,
                loop = json["loop"]?.jsonPrimitive?.booleanOrNull ?: false,
                enqueue = json["enqueue"]?.jsonPrimitive?.booleanOrNull ?: false,
                audioOnly = json["audioOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }.getOrNull()
    }
}
