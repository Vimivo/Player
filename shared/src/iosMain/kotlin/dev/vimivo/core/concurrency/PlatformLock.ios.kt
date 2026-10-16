package dev.vimivo.core.concurrency

import platform.Foundation.NSLock

actual class PlatformLock {
    private val lock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
