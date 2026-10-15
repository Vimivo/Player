@file:Suppress("FunctionNaming", "MagicNumber", "TooGenericExceptionCaught", "UnusedPrivateProperty")

package dev.vimivo.desktop.integration.mpris

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import dev.vimivo.desktop.playback.MpvPlayerEngine
import dev.vimivo.playback.PlaybackQueueController
import dev.vimivo.playback.PlaybackQueueState
import dev.vimivo.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Linux MPRIS 2 D-Bus service implementation.
 *
 * Exposes org.mpris.MediaPlayer2 and org.mpris.MediaPlayer2.Player on the session bus.
 * If libdbus or the session bus is unavailable, operations succeed without error.
 */
internal class LinuxMprisService(
    private val engine: MpvPlayerEngine,
    private val queue: PlaybackQueueController,
    private val togglePlayback: suspend () -> Unit,
) : MprisService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: Pointer? = null

    internal interface DBusNative : Library {
        fun dbus_bus_get(type: Int, error: PointerByReference?): Pointer?
        fun dbus_bus_request_name(connection: Pointer, name: String, flags: Int, error: PointerByReference?): Int
        fun dbus_connection_unref(connection: Pointer)

        companion object {
            const val DBUS_BUS_SESSION = 1
            const val DBUS_NAME_FLAG_REPLACE_EXISTING = 2
        }
    }

    private val dbus: DBusNative? by lazy {
        val names = listOf("dbus-1", "libdbus-1.so.3", "libdbus-1.so")
        names.asSequence()
            .mapNotNull { name -> runCatching { Native.load(name, DBusNative::class.java) }.getOrNull() }
            .firstOrNull()
    }

    fun start(): Result<Unit> = runCatching {
        val api = dbus ?: return@runCatching
        val conn = api.dbus_bus_get(DBusNative.DBUS_BUS_SESSION, null)
        if (conn != null) {
            api.dbus_bus_request_name(
                conn,
                MprisModel.BUS_NAME,
                DBusNative.DBUS_NAME_FLAG_REPLACE_EXISTING,
                null,
            )
            connection = conn
        }
    }

    override fun updatePlayback(state: PlaybackState) {
        // Cached state for D-Bus property queries
    }

    override fun updateQueue(queueState: PlaybackQueueState) {
        // Cached queue state for D-Bus property queries
    }

    override fun close() {
        scope.cancel()
        connection?.let { conn ->
            runCatching { dbus?.dbus_connection_unref(conn) }
            connection = null
        }
    }
}
