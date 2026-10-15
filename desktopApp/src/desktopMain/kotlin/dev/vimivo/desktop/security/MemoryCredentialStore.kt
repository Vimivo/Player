package dev.vimivo.desktop.security

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fallback for secure credential storage.
 *
 * Holds secrets in memory for the active process lifecycle without persisting
 * plaintext to disk. Wipes sensitive arrays on removal or closure.
 */
internal class MemoryCredentialStore : SecureCredentialStore {
    private val store = ConcurrentHashMap<String, CharArray>()

    override fun saveSecret(key: String, secret: CharArray): Result<Unit> = runCatching {
        require(key.isNotBlank()) { "Key must not be blank" }
        store[key]?.fill('\u0000')
        store[key] = secret.copyOf()
    }

    override fun getSecret(key: String): Result<CharArray?> = runCatching {
        require(key.isNotBlank()) { "Key must not be blank" }
        store[key]?.copyOf()
    }

    override fun deleteSecret(key: String): Result<Unit> = runCatching {
        require(key.isNotBlank()) { "Key must not be blank" }
        store.remove(key)?.fill('\u0000')
        Unit
    }

    override fun close() {
        store.values.forEach { it.fill('\u0000') }
        store.clear()
    }
}
