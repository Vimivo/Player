package dev.vimivo.core.concurrency

/** Small synchronous lock used by non-suspending, cross-platform in-memory stores. */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
