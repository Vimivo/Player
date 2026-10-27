package dev.vimivo.storage

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/** A [SqlDatabase] over JDBC SQLite, so the shared store and its migrations run in ordinary JVM tests. */
class JdbcSqlDatabase(url: String = "jdbc:sqlite::memory:") : SqlDatabase {
    private val connection: Connection = DriverManager.getConnection(url).also {
        it.createStatement().use { statement -> statement.execute("PRAGMA foreign_keys = ON") }
    }
    private var depth = 0

    override fun execute(sql: String, args: List<Any?>): Int = connection.prepareStatement(sql).use { statement ->
        bind(statement, args)
        statement.execute()
        statement.updateCount.coerceAtLeast(0)
    }

    override fun <T> query(sql: String, args: List<Any?>, read: (SqlRow) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeQuery().use { rows ->
                val row = Row(rows)
                buildList { while (rows.next()) add(read(row)) }
            }
        }

    override fun <T> transaction(block: () -> T): T {
        if (depth > 0) return block()
        connection.autoCommit = false
        depth++
        return try {
            block().also { connection.commit() }
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            depth--
            connection.autoCommit = true
        }
    }

    override fun close() = connection.close()

    private fun bind(statement: PreparedStatement, args: List<Any?>) {
        args.forEachIndexed { index, value ->
            when (value) {
                null -> statement.setObject(index + 1, null)
                is String -> statement.setString(index + 1, value)
                is Long -> statement.setLong(index + 1, value)
                is Int -> statement.setLong(index + 1, value.toLong())
                is Boolean -> statement.setLong(index + 1, if (value) 1L else 0L)
                is ByteArray -> statement.setBytes(index + 1, value)
                else -> error("Cannot bind ${value::class.simpleName}")
            }
        }
    }

    private class Row(private val rows: ResultSet) : SqlRow {
        override fun isNull(column: Int): Boolean = rows.getObject(column + 1) == null
        override fun string(column: Int): String? = rows.getString(column + 1)
        override fun long(column: Int): Long? = if (isNull(column)) null else rows.getLong(column + 1)
        override fun bytes(column: Int): ByteArray? = rows.getBytes(column + 1)
    }
}
