package dev.vimivo.platform.android.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateVaultTest {
    private val vault = PrivateVault(iterations = 1_000)

    @Test
    fun hashVerifiesOnlyTheRightPin() {
        val stored = vault.hash("4821")
        assertTrue(vault.verify("4821", stored))
        assertFalse(vault.verify("4822", stored))
        assertFalse(vault.verify("", stored))
        assertFalse(stored.contains("4821"))
    }

    @Test
    fun saltMakesEqualPinsHashDifferently() {
        assertNotEquals(vault.hash("1234"), vault.hash("1234"))
    }

    @Test
    fun rejectsMalformedPinsAndStoredValues() {
        assertFalse(vault.isValidPin("123"))
        assertFalse(vault.isValidPin("12345"))
        assertFalse(vault.isValidPin("12a4"))
        assertFalse(vault.verify("1234", null))
        assertFalse(vault.verify("1234", "garbage"))
        assertFalse(vault.verify("1234", "v1:abc:AAAA:AAAA"))
        assertFalse(vault.verify("1234", "v1:999999999:AAAA:AAAA"))
    }

    @Test
    fun limiterLocksAfterFiveFailuresAndRecoversAfterTheWait() {
        var now = 1_000L
        val limiter = PinAttemptLimiter(maxAttempts = 5, lockMillis = 30_000, clock = { now })
        repeat(4) { limiter.recordFailure() }
        assertEquals(0L, limiter.remainingLockMillis())
        limiter.recordFailure()
        assertEquals(30_000L, limiter.remainingLockMillis())
        now += 12_000
        assertEquals(18_000L, limiter.remainingLockMillis())
        now += 18_000
        assertEquals(0L, limiter.remainingLockMillis())
    }

    @Test
    fun successResetsTheFailureCount() {
        val limiter = PinAttemptLimiter(maxAttempts = 3, lockMillis = 1_000, clock = { 0L })
        repeat(2) { limiter.recordFailure() }
        limiter.recordSuccess()
        repeat(2) { limiter.recordFailure() }
        assertEquals(0L, limiter.remainingLockMillis())
    }
}
