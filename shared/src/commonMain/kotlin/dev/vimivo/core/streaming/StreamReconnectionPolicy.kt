@file:Suppress(
    "ArgumentListWrapping", "MagicNumber", "MaximumLineLength", "MaxLineLength",
)

package dev.vimivo.core.streaming

import kotlin.math.pow

data class StreamReconnectionPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000L,
    val backoffMultiplier: Double = 2.0,
    val maxDelayMs: Long = 6000L,
) {
    fun shouldRetry(
        attempt: Int,
        failureReason: StreamFailureReason,
        isUserCancelled: Boolean = false,
    ): Boolean {
        if (isUserCancelled) return false
        if (!failureReason.isRecoverable) return false
        return attempt < maxRetries
    }

    fun calculateDelayMs(attempt: Int): Long {
        if (attempt <= 0) return 0L
        val multiplier = backoffMultiplier.pow((attempt - 1).toDouble())
        val calculated = (initialDelayMs * multiplier).toLong()
        return calculated.coerceIn(initialDelayMs, maxDelayMs)
    }

    companion object {
        val DEFAULT = StreamReconnectionPolicy()
    }
}
