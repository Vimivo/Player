package dev.vimivo.storage

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Opens the iOS app's library store; the Swift side calls this once with the database path it chooses. */
object IosLibrary {
    private const val MILLIS_PER_SECOND = 1_000.0

    /** The library database at [path] (a file in the app's Application Support folder), migrated to the current schema. */
    @Throws(Exception::class)
    fun open(path: String): LocalLibraryStore = LocalLibraryStore(IosSqliteDatabase(path), ::nowMillis)

    /** A library that exists only in memory; for tests. */
    @Throws(Exception::class)
    fun openInMemory(): LocalLibraryStore = LocalLibraryStore(IosSqliteDatabase(IosSqliteDatabase.IN_MEMORY), ::nowMillis)

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
}
