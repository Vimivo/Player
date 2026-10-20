package dev.vimivo.desktop.integration.mpris

import com.sun.jna.Pointer
import dev.vimivo.desktop.diagnostics.DesktopDiagnosticExporter
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import dev.vimivo.playback.PlaybackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Linux MPRIS 2 on the session bus (`org.mpris.MediaPlayer2.vimivo`): desktop media widgets, media keys
 * and `playerctl` see the current track, position and play state, and can control playback.
 *
 * A single daemon thread owns the libdbus connection: it dispatches incoming calls and emits
 * `PropertiesChanged`/`Seeked`, so the connection is never touched from two threads.
 */
internal class LinuxMprisService(
    private val engine: MpvPlayerEngine,
    private val queue: PlaybackQueueController,
    private val togglePlayback: suspend () -> Unit,
) : MprisService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var playback = PlaybackState()

    @Volatile private var latestQueue = PlaybackQueueState()

    @Volatile private var running = false
    private var worker: Thread? = null
    private var lastPublished: Map<String, MprisValue>? = null
    private var lastPositionMillis = 0L
    private var lastPositionAtNanos = 0L

    private val commands = MprisCommandHandler(
        engine = engine,
        queue = object : MprisQueueActions {
            override val queueState get() = latestQueue
            override suspend fun togglePlayback() = this@LinuxMprisService.togglePlayback()
            override suspend fun next() {
                queue.next()
            }
            override suspend fun previous() {
                queue.previous()
            }
            override fun toggleShuffle() = queue.toggleShuffle()
            override fun cycleRepeat() = queue.cycleRepeat()
        },
        launch = { block -> scope.launch { block() } },
    )

    /** Connects to the session bus and starts serving; fails when libdbus or the session bus is unavailable. */
    fun start() {
        val api = checkNotNull(LibDBus.load()) { "libdbus-1 is not installed" }
        api.dbus_threads_init_default()
        val connection = checkNotNull(api.dbus_bus_get(LibDBus.BUS_SESSION, null)) { "No D-Bus session bus" }
        val session = DBusSession(api, connection)
        val fallbackName = "${MprisModel.BUS_NAME}.instance${ProcessHandle.current().pid()}"
        session.claimName(listOf(MprisModel.BUS_NAME, fallbackName))
        running = true
        worker = thread(name = "vimivo-mpris", isDaemon = true) { serve(session) }
    }

    override fun updatePlayback(state: PlaybackState) {
        playback = state
    }

    override fun updateQueue(queueState: PlaybackQueueState) {
        latestQueue = queueState
    }

    private fun serve(session: DBusSession) {
        runCatching {
            while (running && session.readWrite(POLL_MILLIS)) {
                drain(session)
                publishChanges(session)
            }
        }.onFailure { error -> DesktopDiagnosticExporter.defaultBuffer.recordError("MPRIS", error) }
        session.close()
    }

    private fun drain(session: DBusSession) {
        while (running) {
            val message = session.popMessage() ?: return
            try {
                dispatch(session, message)
            } finally {
                session.api.dbus_message_unref(message)
            }
        }
    }

    private fun dispatch(session: DBusSession, message: Pointer) {
        val api = session.api
        if (api.dbus_message_get_type(message) != LibDBus.MESSAGE_TYPE_METHOD_CALL) return
        if (api.dbus_message_get_path(message) != MprisModel.OBJECT_PATH) {
            return session.replyError(message, UNKNOWN_OBJECT)
        }
        val member = api.dbus_message_get_member(message).orEmpty()
        val args = session.readArgs(message)
        when (api.dbus_message_get_interface(message)) {
            MprisModel.INTERFACE_INTROSPECTABLE -> session.reply(message) { m, iter ->
                m.appendString(iter, MprisModel.INTROSPECTION_XML)
            }
            MprisModel.INTERFACE_PROPERTIES -> handleProperties(session, message, member, args)
            MprisModel.INTERFACE_ROOT -> session.reply(message)
            MprisModel.INTERFACE_PLAYER -> {
                val command = MprisRequests.playerCommand(member, args)
                    ?: return session.replyError(message, UNKNOWN_METHOD)
                val seekedTo = commands.handle(command)
                session.reply(message)
                seekedTo?.let { emitSeeked(session, it) }
            }
            else -> session.replyError(message, UNKNOWN_METHOD)
        }
    }

    private fun handleProperties(session: DBusSession, message: Pointer, member: String, args: List<Any>) {
        val properties = when (args.firstOrNull()) {
            MprisModel.INTERFACE_ROOT -> MprisModel.rootProperties()
            MprisModel.INTERFACE_PLAYER -> currentPlayerProperties()
            else -> return session.replyError(message, UNKNOWN_INTERFACE)
        }
        when (member) {
            "Get" -> {
                val value = properties[args.getOrNull(1)] ?: return session.replyError(message, UNKNOWN_PROPERTY)
                session.reply(message) { m, iter -> m.appendVariant(iter, value) }
            }
            "GetAll" -> session.reply(message) { m, iter -> m.appendDict(iter, properties) }
            "Set" -> {
                val command = MprisRequests.propertyCommand(args.getOrNull(1) as? String ?: "", args.getOrNull(2))
                    ?: return session.replyError(message, READ_ONLY)
                commands.handle(command)
                session.reply(message)
            }
            else -> session.replyError(message, UNKNOWN_METHOD)
        }
    }

    private fun currentPlayerProperties() =
        MprisModel.playerProperties(playback, latestQueue, engine.capabilities.maxVolume)

    private fun publishChanges(session: DBusSession) {
        val current = currentPlayerProperties()
        detectInternalSeek(session)
        val changed = MprisModel.changedProperties(lastPublished, current)
        if (changed.isEmpty()) return
        lastPublished = current
        session.signal(MprisModel.INTERFACE_PROPERTIES, "PropertiesChanged") { m, iter ->
            m.appendString(iter, MprisModel.INTERFACE_PLAYER)
            m.appendDict(iter, changed)
            m.appendStringArray(iter, emptyList())
        }
    }

    /** Seeks made inside Vimivo (keys, seek bar) must also reach clients, which extrapolate position. */
    private fun detectInternalSeek(session: DBusSession) {
        val now = System.nanoTime()
        val position = playback.positionMillis
        val advancing = playback.status == PlaybackStatus.Playing
        val elapsedMillis = (now - lastPositionAtNanos) / NANOS_PER_MILLI
        val expected = lastPositionMillis + if (advancing) (elapsedMillis * playback.speed).toLong() else 0L
        val jumped = lastPositionAtNanos != 0L && abs(position - expected) > SEEK_TOLERANCE_MILLIS
        lastPositionMillis = position
        lastPositionAtNanos = now
        if (jumped) emitSeeked(session, position)
    }

    private fun emitSeeked(session: DBusSession, positionMillis: Long) {
        session.signal(MprisModel.INTERFACE_PLAYER, "Seeked") { m, iter ->
            m.appendValue(iter, MprisValue.Int64(MprisModel.millisToMicroseconds(positionMillis)))
        }
        lastPositionMillis = positionMillis
        lastPositionAtNanos = System.nanoTime()
    }

    override fun close() {
        running = false
        worker?.join(JOIN_TIMEOUT_MILLIS)
        worker = null
        scope.cancel()
    }

    private companion object {
        const val POLL_MILLIS = 100
        const val JOIN_TIMEOUT_MILLIS = 500L
        const val NANOS_PER_MILLI = 1_000_000L
        const val SEEK_TOLERANCE_MILLIS = 1_500L
        const val UNKNOWN_METHOD = "org.freedesktop.DBus.Error.UnknownMethod"
        const val UNKNOWN_OBJECT = "org.freedesktop.DBus.Error.UnknownObject"
        const val UNKNOWN_INTERFACE = "org.freedesktop.DBus.Error.UnknownInterface"
        const val UNKNOWN_PROPERTY = "org.freedesktop.DBus.Error.UnknownProperty"
        const val READ_ONLY = "org.freedesktop.DBus.Error.PropertyReadOnly"
    }
}

/** One libdbus connection plus the reply/signal plumbing; used only from the MPRIS thread. */
internal class DBusSession(val api: LibDBus, private val connection: Pointer) {
    fun claimName(candidates: List<String>): String = candidates.firstOrNull { name ->
        val result = api.dbus_bus_request_name(connection, name, LibDBus.NAME_FLAG_DO_NOT_QUEUE, null)
        result == LibDBus.REQUEST_NAME_PRIMARY_OWNER
    } ?: error("Could not own an MPRIS bus name")

    /** Returns false when the connection was closed by the bus. */
    fun readWrite(timeoutMillis: Int): Boolean = api.dbus_connection_read_write(connection, timeoutMillis) != 0

    fun popMessage(): Pointer? = api.dbus_connection_pop_message(connection)

    fun readArgs(message: Pointer): List<Any> {
        val marshaller = DBusMarshaller(api)
        return try {
            marshaller.readArgs(message)
        } finally {
            marshaller.release()
        }
    }

    fun reply(call: Pointer, body: ((DBusMarshaller, Pointer) -> Unit)? = null) {
        api.dbus_message_new_method_return(call)?.let { send(it, body) }
    }

    fun replyError(call: Pointer, name: String) {
        api.dbus_message_new_error(call, name, "Vimivo: $name")?.let { send(it, null) }
    }

    fun signal(iface: String, name: String, body: (DBusMarshaller, Pointer) -> Unit) {
        api.dbus_message_new_signal(MprisModel.OBJECT_PATH, iface, name)?.let { send(it, body) }
    }

    fun close() = api.dbus_connection_unref(connection)

    private fun send(message: Pointer, body: ((DBusMarshaller, Pointer) -> Unit)?) {
        val marshaller = DBusMarshaller(api)
        try {
            if (body != null) {
                val iter = marshaller.newIter()
                api.dbus_message_iter_init_append(message, iter)
                body(marshaller, iter)
            }
            api.dbus_connection_send(connection, message, null)
            api.dbus_connection_flush(connection)
        } finally {
            api.dbus_message_unref(message)
            marshaller.release()
        }
    }
}
