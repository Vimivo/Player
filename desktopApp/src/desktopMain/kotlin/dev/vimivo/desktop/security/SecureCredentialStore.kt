package dev.vimivo.desktop.security

/**
 * Secure credential storage interface for desktop platforms.
 *
 * Prevents storing sensitive credentials (e.g. streaming passwords, auth tokens) in plaintext SQLite.
 */
internal interface SecureCredentialStore : AutoCloseable {
    fun saveSecret(key: String, secret: CharArray): Result<Unit>
    fun getSecret(key: String): Result<CharArray?>
    fun deleteSecret(key: String): Result<Unit>
    override fun close() {}
}
