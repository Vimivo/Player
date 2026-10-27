@file:OptIn(ExperimentalForeignApi::class)

package dev.vimivo.storage

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import dev.vimivo.sqlite3.SQLITE_BLOB
import dev.vimivo.sqlite3.SQLITE_DONE
import dev.vimivo.sqlite3.SQLITE_NULL
import dev.vimivo.sqlite3.SQLITE_OK
import dev.vimivo.sqlite3.SQLITE_OPEN_CREATE
import dev.vimivo.sqlite3.SQLITE_OPEN_FULLMUTEX
import dev.vimivo.sqlite3.SQLITE_OPEN_READWRITE
import dev.vimivo.sqlite3.SQLITE_ROW
import dev.vimivo.sqlite3.sqlite3_bind_blob
import dev.vimivo.sqlite3.sqlite3_bind_int64
import dev.vimivo.sqlite3.sqlite3_bind_null
import dev.vimivo.sqlite3.sqlite3_bind_text
import dev.vimivo.sqlite3.sqlite3_changes
import dev.vimivo.sqlite3.sqlite3_close
import dev.vimivo.sqlite3.sqlite3_column_blob
import dev.vimivo.sqlite3.sqlite3_column_bytes
import dev.vimivo.sqlite3.sqlite3_column_int64
import dev.vimivo.sqlite3.sqlite3_column_text
import dev.vimivo.sqlite3.sqlite3_column_type
import dev.vimivo.sqlite3.sqlite3_errmsg
import dev.vimivo.sqlite3.sqlite3_finalize
import dev.vimivo.sqlite3.sqlite3_open_v2
import dev.vimivo.sqlite3.sqlite3_prepare_v2
import dev.vimivo.sqlite3.sqlite3_reset
import dev.vimivo.sqlite3.sqlite3_step
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

/** Thrown when the system SQLite refuses a statement; carries SQLite's own message. */
class SqliteException(message: String) : Exception(message)

/**
 * [SqlDatabase] over the system SQLite on iOS. [path] is a file (created if missing) or `:memory:` for a database that
 * lives only as long as this object. Foreign keys are on and file databases use write-ahead logging.
 */
class IosSqliteDatabase(path: String) : SqlDatabase {
    private val handle: CPointer<sqlite3> = memScoped {
        val out = alloc<CPointerVar<sqlite3>>()
        val flags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX
        val code = sqlite3_open_v2(path, out.ptr, flags, null)
        val opened = out.value
        if (code != SQLITE_OK || opened == null) {
            val message = opened?.let { sqlite3_errmsg(it)?.toKString() } ?: "code $code"
            opened?.let { sqlite3_close(it) }
            throw SqliteException("The database at $path could not be opened: $message")
        }
        opened
    }
    private var depth = 0

    init {
        execute("PRAGMA foreign_keys = ON")
        if (path != IN_MEMORY) query("PRAGMA journal_mode = WAL") { it.string(0) }
    }

    override fun execute(sql: String, args: List<Any?>): Int = withStatement(sql, args) { statement ->
        var code = sqlite3_step(statement)
        while (code == SQLITE_ROW) code = sqlite3_step(statement) // a PRAGMA may answer with a row
        if (code != SQLITE_DONE) fail(sql)
        sqlite3_changes(handle)
    }

    override fun <T> query(sql: String, args: List<Any?>, read: (SqlRow) -> T): List<T> =
        withStatement(sql, args) { statement ->
            val rows = mutableListOf<T>()
            val row = Row(statement)
            while (true) {
                val code = sqlite3_step(statement)
                if (code == SQLITE_ROW) {
                    rows += read(row)
                } else {
                    if (code != SQLITE_DONE) fail(sql)
                    break
                }
            }
            rows
        }

    override fun <T> transaction(block: () -> T): T {
        if (depth > 0) return block()
        execute("BEGIN IMMEDIATE")
        depth++
        return try {
            block().also { execute("COMMIT") }
        } catch (error: Throwable) {
            runCatching { execute("ROLLBACK") }
            throw error
        } finally {
            depth--
        }
    }

    override fun close() {
        sqlite3_close(handle)
    }

    private fun <T> withStatement(sql: String, args: List<Any?>, use: (CPointer<sqlite3_stmt>) -> T): T = memScoped {
        val out = alloc<CPointerVar<sqlite3_stmt>>()
        if (sqlite3_prepare_v2(handle, sql, -1, out.ptr, null) != SQLITE_OK) fail(sql)
        val statement = checkNotNull(out.value)
        try {
            args.forEachIndexed { index, value -> bind(statement, index + 1, value) }
            use(statement)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    private fun bind(statement: CPointer<sqlite3_stmt>, index: Int, value: Any?) {
        val code = when (value) {
            null -> sqlite3_bind_null(statement, index)
            is String -> sqlite3_bind_text(statement, index, value, -1, TRANSIENT)
            is Long -> sqlite3_bind_int64(statement, index, value)
            is Int -> sqlite3_bind_int64(statement, index, value.toLong())
            is Boolean -> sqlite3_bind_int64(statement, index, if (value) 1L else 0L)
            is ByteArray -> bindBlob(statement, index, value)
            else -> throw IllegalArgumentException("Cannot bind a ${value::class.simpleName} to a SQL statement")
        }
        if (code != SQLITE_OK) fail("bind $index")
    }

    private fun bindBlob(statement: CPointer<sqlite3_stmt>, index: Int, value: ByteArray): Int {
        if (value.isEmpty()) return sqlite3_bind_blob(statement, index, null, 0, TRANSIENT)
        return value.usePinned { sqlite3_bind_blob(statement, index, it.addressOf(0), value.size, TRANSIENT) }
    }

    private fun fail(what: String): Nothing =
        throw SqliteException("SQLite failed on \"${what.take(MAX_SQL_IN_MESSAGE)}\": ${sqlite3_errmsg(handle)?.toKString()}")

    private class Row(private val statement: CPointer<sqlite3_stmt>) : SqlRow {
        override fun isNull(column: Int) = sqlite3_column_type(statement, column) == SQLITE_NULL

        override fun string(column: Int): String? =
            if (isNull(column)) null else sqlite3_column_text(statement, column)?.reinterpret<ByteVar>()?.toKString()

        override fun long(column: Int): Long? = if (isNull(column)) null else sqlite3_column_int64(statement, column)

        override fun bytes(column: Int): ByteArray? {
            if (isNull(column) || sqlite3_column_type(statement, column) != SQLITE_BLOB) return null
            val size = sqlite3_column_bytes(statement, column)
            val blob = sqlite3_column_blob(statement, column) ?: return ByteArray(0)
            return blob.readBytes(size)
        }
    }

    companion object {
        const val IN_MEMORY = ":memory:"
        private const val MAX_SQL_IN_MESSAGE = 80

        /** SQLite's "copy this value now" destructor: the bound Kotlin strings and arrays may move afterwards. */
        private val TRANSIENT = (-1L).toCPointer<CFunction<(COpaquePointer?) -> Unit>>()
    }
}
