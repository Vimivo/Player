@file:Suppress("FunctionNaming", "FunctionParameterNaming", "TooManyFunctions")

package dev.vimivo.desktop.integration.mpris

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Minimal JNA binding of the documented libdbus-1 C API: connection, message, and message-iterator
 * functions only. Every call happens on the single MPRIS thread.
 */
@Suppress("TooManyFunctions")
internal interface LibDBus : Library {
    fun dbus_threads_init_default(): Int
    fun dbus_bus_get(type: Int, error: Pointer?): Pointer?
    fun dbus_bus_request_name(connection: Pointer, name: String, flags: Int, error: Pointer?): Int
    fun dbus_connection_read_write(connection: Pointer, timeoutMillis: Int): Int
    fun dbus_connection_pop_message(connection: Pointer): Pointer?
    fun dbus_connection_send(connection: Pointer, message: Pointer, serial: Pointer?): Int
    fun dbus_connection_flush(connection: Pointer)
    fun dbus_connection_unref(connection: Pointer)

    fun dbus_message_get_type(message: Pointer): Int
    fun dbus_message_get_path(message: Pointer): String?
    fun dbus_message_get_interface(message: Pointer): String?
    fun dbus_message_get_member(message: Pointer): String?
    fun dbus_message_new_method_return(call: Pointer): Pointer?
    fun dbus_message_new_error(call: Pointer, name: String, text: String): Pointer?
    fun dbus_message_new_signal(path: String, iface: String, name: String): Pointer?
    fun dbus_message_unref(message: Pointer)

    fun dbus_message_iter_init(message: Pointer, iter: Pointer): Int
    fun dbus_message_iter_get_arg_type(iter: Pointer): Int
    fun dbus_message_iter_get_basic(iter: Pointer, value: Pointer)
    fun dbus_message_iter_next(iter: Pointer): Int
    fun dbus_message_iter_recurse(iter: Pointer, sub: Pointer)
    fun dbus_message_iter_init_append(message: Pointer, iter: Pointer)
    fun dbus_message_iter_append_basic(iter: Pointer, type: Int, value: Pointer): Int
    fun dbus_message_iter_open_container(iter: Pointer, type: Int, signature: String?, sub: Pointer): Int
    fun dbus_message_iter_close_container(iter: Pointer, sub: Pointer): Int

    companion object {
        const val BUS_SESSION = 0
        const val NAME_FLAG_DO_NOT_QUEUE = 4
        const val REQUEST_NAME_PRIMARY_OWNER = 1
        const val MESSAGE_TYPE_METHOD_CALL = 1

        const val TYPE_STRING = 's'.code
        const val TYPE_OBJECT_PATH = 'o'.code
        const val TYPE_INT64 = 'x'.code
        const val TYPE_DOUBLE = 'd'.code
        const val TYPE_BOOLEAN = 'b'.code
        const val TYPE_ARRAY = 'a'.code
        const val TYPE_VARIANT = 'v'.code
        const val TYPE_DICT_ENTRY = 'e'.code

        fun load(): LibDBus? = listOf("dbus-1", "libdbus-1.so.3")
            .firstNotNullOfOrNull { name -> runCatching { Native.load(name, LibDBus::class.java) }.getOrNull() }
    }
}

/** Writes [MprisValue]s and reads basic arguments with libdbus message iterators. */
internal class DBusMarshaller(private val api: LibDBus) {
    /** Native memory kept alive until the message that references it has been sent. */
    private val arena = mutableListOf<Memory>()

    fun newIter(): Memory = Memory(ITER_BYTES).also {
        it.clear()
        arena += it
    }

    fun release() = arena.clear()

    fun appendString(iter: Pointer, value: String, type: Int = LibDBus.TYPE_STRING) {
        val text = Memory(value.toByteArray(Charsets.UTF_8).size + 1L).apply { setString(0, value, "UTF-8") }
        val holder = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, text) }
        arena += text
        arena += holder
        check(api.dbus_message_iter_append_basic(iter, type, holder) != 0) { "dbus append failed" }
    }

    fun appendStringArray(iter: Pointer, values: List<String>) {
        val sub = newIter()
        api.dbus_message_iter_open_container(iter, LibDBus.TYPE_ARRAY, "s", sub)
        values.forEach { appendString(sub, it) }
        api.dbus_message_iter_close_container(iter, sub)
    }

    fun appendDict(iter: Pointer, entries: Map<String, MprisValue>) {
        val array = newIter()
        api.dbus_message_iter_open_container(iter, LibDBus.TYPE_ARRAY, "{sv}", array)
        entries.forEach { (key, value) ->
            val entry = newIter()
            api.dbus_message_iter_open_container(array, LibDBus.TYPE_DICT_ENTRY, null, entry)
            appendString(entry, key)
            appendVariant(entry, value)
            api.dbus_message_iter_close_container(array, entry)
        }
        api.dbus_message_iter_close_container(iter, array)
    }

    fun appendVariant(iter: Pointer, value: MprisValue) {
        val variant = newIter()
        api.dbus_message_iter_open_container(iter, LibDBus.TYPE_VARIANT, value.signature, variant)
        appendValue(variant, value)
        api.dbus_message_iter_close_container(iter, variant)
    }

    fun appendValue(iter: Pointer, value: MprisValue) {
        when (value) {
            is MprisValue.Str -> appendString(iter, value.raw)
            is MprisValue.ObjectPath -> appendString(iter, value.raw, LibDBus.TYPE_OBJECT_PATH)
            is MprisValue.Int64 -> appendFixed(iter, LibDBus.TYPE_INT64) { it.setLong(0, value.raw) }
            is MprisValue.Dbl -> appendFixed(iter, LibDBus.TYPE_DOUBLE) { it.setDouble(0, value.raw) }
            // dbus_bool_t is a 32-bit integer on the wire and in memory.
            is MprisValue.Bool -> appendFixed(iter, LibDBus.TYPE_BOOLEAN) { it.setInt(0, if (value.raw) 1 else 0) }
            is MprisValue.StrList -> appendStringArray(iter, value.raw)
            is MprisValue.Dict -> appendDict(iter, value.raw)
        }
    }

    private fun appendFixed(iter: Pointer, type: Int, write: (Memory) -> Unit) {
        val slot = Memory(FIXED_BYTES).also(write)
        arena += slot
        check(api.dbus_message_iter_append_basic(iter, type, slot) != 0) { "dbus append failed" }
    }

    /** Reads top-level basic arguments (strings, object paths, int64, double, boolean, variants of those). */
    fun readArgs(message: Pointer): List<Any> {
        val iter = newIter()
        if (api.dbus_message_iter_init(message, iter) == 0) return emptyList()
        val args = mutableListOf<Any>()
        do {
            readBasic(iter)?.let(args::add)
        } while (api.dbus_message_iter_next(iter) != 0)
        return args
    }

    private fun readBasic(iter: Pointer): Any? {
        val slot = Memory(FIXED_BYTES).also { arena += it }
        return when (api.dbus_message_iter_get_arg_type(iter)) {
            LibDBus.TYPE_STRING, LibDBus.TYPE_OBJECT_PATH -> {
                api.dbus_message_iter_get_basic(iter, slot)
                slot.getPointer(0)?.getString(0, "UTF-8")
            }
            LibDBus.TYPE_INT64 -> slot.also { api.dbus_message_iter_get_basic(iter, it) }.getLong(0)
            LibDBus.TYPE_DOUBLE -> slot.also { api.dbus_message_iter_get_basic(iter, it) }.getDouble(0)
            LibDBus.TYPE_BOOLEAN -> slot.also { api.dbus_message_iter_get_basic(iter, it) }.getInt(0) != 0
            LibDBus.TYPE_VARIANT -> {
                val inner = newIter()
                api.dbus_message_iter_recurse(iter, inner)
                readBasic(inner)
            }
            else -> null
        }
    }

    private companion object {
        /** sizeof(DBusMessageIter) is 72 bytes on 64-bit libdbus; extra headroom keeps this ABI-safe. */
        const val ITER_BYTES = 128L
        const val FIXED_BYTES = 8L
    }
}
