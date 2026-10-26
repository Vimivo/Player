package dev.vimivo.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wraps playback back to A when it reaches B, for engines that have no loop of their own that is accurate.
 *
 * libmpv's own `ab-loop-*` is not used on desktop: with a real audio output it leaves audio queued in the output, so
 * the reported position sits at or below zero while looping. A seek flushes that audio and keeps the position true, at
 * the cost of up to one [POLL_MILLIS] past B. ExoPlayer has no A–B loop at all. The watcher has a light loop of its
 * own because an engine's main state poll can be hundreds of milliseconds between rounds.
 */
class AbRegionWatcher(
    private val scope: CoroutineScope,
    private val positionMillis: () -> Long,
    private val isPaused: () -> Boolean,
    private val seekTo: (Long) -> Unit,
) {
    private var job: Job? = null

    /** Watches [range] (null or incomplete stops watching). */
    fun watch(range: ABRepeatRange?) {
        job?.cancel()
        val (a, b) = range?.takeIf { it.enabled }?.span() ?: return
        job = scope.launch {
            while (isActive) {
                if (!isPaused() && positionMillis() >= b) runCatching { seekTo(a) }
                delay(POLL_MILLIS)
            }
        }
    }

    private fun ABRepeatRange.span(): Pair<Long, Long>? {
        val a = pointA ?: return null
        val b = pointB ?: return null
        return if (b > a) a to b else null
    }

    fun stop() {
        job?.cancel()
    }

    private companion object {
        const val POLL_MILLIS = 20L
    }
}
