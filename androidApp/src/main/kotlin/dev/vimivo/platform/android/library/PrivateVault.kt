package dev.vimivo.platform.android.library

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage for the private vault. A PIN is never stored: only `v1:<iterations>:<salt>:<hash>` produced with
 * PBKDF2-HMAC-SHA256 and a random per-PIN salt, compared in constant time.
 */
class PrivateVault(private val iterations: Int = DEFAULT_ITERATIONS) {
    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String): String {
        require(isValidPin(pin)) { "PIN must be $PIN_LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val digest = derive(pin, salt, iterations)
        val encoder = Base64.getEncoder()
        return "v1:$iterations:${encoder.encodeToString(salt)}:${encoder.encodeToString(digest)}"
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored == null || !isValidPin(pin)) return false
        val parts = stored.split(':')
        if (parts.size != PARTS || parts[0] != "v1") return false
        val rounds = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt, rounds))
    }

    private fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val DEFAULT_ITERATIONS = 120_000
        private const val MAX_ITERATIONS = 2_000_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PARTS = 4
    }
}

/** Blocks guessing: after [maxAttempts] wrong PINs, unlock attempts are refused for [lockMillis]. */
class PinAttemptLimiter(
    private val maxAttempts: Int = 5,
    private val lockMillis: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var failures = 0
    private var lockedUntil = 0L

    fun remainingLockMillis(): Long = (lockedUntil - clock()).coerceAtLeast(0)

    fun recordFailure() {
        failures++
        if (failures >= maxAttempts) {
            lockedUntil = clock() + lockMillis
            failures = 0
        }
    }

    fun recordSuccess() {
        failures = 0
        lockedUntil = 0
    }
}
