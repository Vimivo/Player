package dev.vimivo.storage

/**
 * The few SQL operations the shared stores need. Each platform supplies a driver (iOS wraps the system SQLite, tests
 * use JDBC); the schema, migrations and queries are written once in common code. Arguments may be `String`, `Long`,
 * `Int`, `Boolean`, `ByteArray` or `null`.
 */
interface SqlDatabase : AutoCloseable {
    /** Runs a statement that changes data or schema and returns the number of rows it changed. */
    fun execute(sql: String, args: List<Any?> = emptyList()): Int

    /** Runs a query and maps every row; the row is only valid inside [read]. */
    fun <T> query(sql: String, args: List<Any?> = emptyList(), read: (SqlRow) -> T): List<T>

    /** Runs [block] in a transaction that is rolled back if it throws. */
    fun <T> transaction(block: () -> T): T
}

/** One result row, read by zero-based column. */
interface SqlRow {
    fun isNull(column: Int): Boolean
    fun string(column: Int): String?
    fun long(column: Int): Long?
    fun bytes(column: Int): ByteArray?
}

/** The SQLite `user_version` of the database, used to number schema migrations. */
var SqlDatabase.userVersion: Int
    get() = query("PRAGMA user_version") { it.long(0)?.toInt() ?: 0 }.firstOrNull() ?: 0
    set(value) {
        require(value >= 0) { "A schema version cannot be negative" }
        execute("PRAGMA user_version = $value")
    }
